package uk.co.reiad.library.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/* ============================================================
   Who a reader is, and how they say so.

   The site's own account module is `aab/src/account.ts` and this
   is the same conversation with the same Supabase project. The
   two public strings below are public by design: the publishable
   key identifies the project and grants nothing on its own,
   because every table it can reach is behind row-level security.
   The key that DOES grant things is the service role key, which
   is not here, is not in either repository, and has no reason
   ever to be.

   ---- what an app does differently from a page ----

   A page sends the reader to Supabase and gets them back at the
   same address with the tokens in the URL FRAGMENT, which is
   deliberate: a fragment is never sent to a server, so a token
   cannot end up in a log.

   An app has no address to come back to, so it declares a scheme
   and comes back to that: `uk.co.reiad.library://auth`. The
   fragment arrives the same way and is read the same way. What is
   NOT used is an App Link over `https://reiad.co.uk`, and that is
   a decision rather than an oversight: a verified App Link needs
   a signing fingerprint in `/.well-known/assetlinks.json`, which
   means the account cannot work until somebody has published a
   fingerprint. Signing in is not a thing to make wait on a
   deployment.
   ============================================================ */

object Supabase {
    const val URL = "https://wvjarqnnmkkuxyrndtya.supabase.co"

    /** The PUBLISHABLE key, and the word is doing work. It
        identifies the project and grants nothing on its own,
        because every table it can reach is behind row-level
        security. The key that does grant things is the service
        role key, which is in neither repository. */
    const val KEY = "sb_publishable_lvckv69CrjRyF1_urwDrCQ_PWoTH3UW"

    const val AUTH = "$URL/auth/v1"
    const val REST = "$URL/rest/v1"

    /** Where a sign-in comes back to. Declared in the manifest as
        an intent filter, and registered in the Supabase dashboard
        as an allowed redirect. Both halves are needed. */
    const val REDIRECT = "uk.co.reiad.library://auth"

    /** Refresh a minute before the token actually expires. A
        token that expires mid-request is a request that fails for
        no reason the reader could understand. */
    const val EARLY_MS = 60_000L
}

/** Where to send somebody to sign in with a provider. */
fun authorizeUrl(provider: String): String =
    "${Supabase.AUTH}/authorize?provider=$provider" +
        "&redirect_to=${encodeComponent(Supabase.REDIRECT)}"

/** Percent-encoding for a URL component, written out because the
    only thing in the standard library that does it is Java's
    `URLEncoder`, which is `application/x-www-form-urlencoded` and
    turns a space into `+`. That is right for a form field and
    wrong for a query value. */
fun encodeComponent(text: String): String = buildString {
    for (byte in text.encodeToByteArray()) {
        /* Masked to a byte FIRST, and the safe set tested on the
           number rather than on a Char.

           Both halves were wrong once and the test caught it. A
           Kotlin Byte is signed, so the first byte of `ট` is
           -32 and `shr 4` on it produces the wrong nibble
           entirely. And `byte.toInt().toChar()` turns that byte
           into U+00E0, which `isLetterOrDigit` calls a letter, so
           every non-ASCII byte was being passed through raw
           instead of encoded. */
        val value = byte.toInt() and 0xFF
        val safe = value in 0x30..0x39 ||   // 0-9
            value in 0x41..0x5A ||          // A-Z
            value in 0x61..0x7A ||          // a-z
            value == 0x2D || value == 0x5F || value == 0x2E || value == 0x7E  // - _ . ~
        if (safe) {
            append(value.toChar())
        } else {
            append('%')
            append(HEX[value shr 4])
            append(HEX[value and 0xF])
        }
    }
}

private const val HEX = "0123456789ABCDEF"

/** A session, as this device keeps it. */
data class Session(
    val accessToken: String,
    val refreshToken: String,
    /** When the access token stops working, in epoch millis. */
    val expiresAt: Long,
    val reader: Reader?,
)

/** Who a reader is, as far as this device knows. */
data class Reader(
    val id: String,
    val email: String = "",
    val name: String = "",
    /** Their picture, where the provider they used gave one. */
    val picture: String = "",
)

/** What came back on the redirect.

    Three outcomes, not two. A sign-in that failed says WHY, and
    that is not politeness: silently doing nothing is the one
    response to a failed sign-in that leaves somebody pressing the
    same button again. */
sealed interface Arrival {
    data class SignedIn(val session: Session) : Arrival
    data class Failed(val reason: String) : Arrival
    data object NotAnArrival : Arrival
}

/** Reads the tokens out of a redirect URI.

    Supabase puts them in the FRAGMENT, and some providers put an
    error there instead. A few paths in the wild return the pair
    in the query string, so both are read: taking only the
    fragment would be a sign-in that works for one provider and
    silently does nothing for another. */
fun arrivalOf(uri: String, now: Long): Arrival {
    val fragment = uri.substringAfter('#', "")
    val query = uri.substringAfter('?', "").substringBefore('#')
    val params = parseParams(fragment) + parseParams(query)
    if (params.isEmpty()) return Arrival.NotAnArrival

    val failed = params["error_description"] ?: params["error"] ?: params["error_code"]
    if (failed != null) return Arrival.Failed(failed.replace('+', ' '))

    val access = params["access_token"] ?: return Arrival.NotAnArrival
    val refresh = params["refresh_token"] ?: return Arrival.NotAnArrival
    val claims = readToken(access)

    return Arrival.SignedIn(
        Session(
            accessToken = access,
            refreshToken = refresh,
            /* The token's own `exp` first, and the response's
               `expires_in` only as a fallback. The token is the
               thing that actually stops working. */
            expiresAt = claims?.expiresAt
                ?: now + (params["expires_in"]?.toLongOrNull() ?: 3600L) * 1000L,
            reader = claims?.reader,
        ),
    )
}

private fun parseParams(text: String): Map<String, String> {
    if (text.isBlank()) return emptyMap()
    return text.split('&').mapNotNull { pair ->
        val at = pair.indexOf('=')
        if (at <= 0) null
        else decodeComponent(pair.substring(0, at)) to decodeComponent(pair.substring(at + 1))
    }.toMap()
}

private fun decodeComponent(text: String): String {
    if ('%' !in text) return text
    val out = StringBuilder()
    var i = 0
    val bytes = ArrayList<Byte>()
    fun flush() {
        if (bytes.isNotEmpty()) {
            out.append(bytes.toByteArray().decodeToString())
            bytes.clear()
        }
    }
    while (i < text.length) {
        val c = text[i]
        if (c == '%' && i + 2 < text.length) {
            val hex = text.substring(i + 1, i + 3).toIntOrNull(16)
            if (hex != null) {
                bytes += hex.toByte()
                i += 3
                continue
            }
        }
        flush()
        out.append(c)
        i += 1
    }
    flush()
    return out.toString()
}

/** What a token says about itself.

    Read rather than asked about, so the header can be right on
    the first frame rather than after a round trip. The signature
    is NOT verified here and does not need to be: this is the
    device reading a token it was just handed, and everything the
    token is used for goes back to Supabase, which does verify it.
    Nothing is granted on the strength of this. */
data class Claims(val expiresAt: Long, val reader: Reader?)

@OptIn(ExperimentalEncodingApi::class)
fun readToken(jwt: String): Claims? = runCatching {
    val payload = jwt.split('.').getOrNull(1) ?: return null
    val json = Json { ignoreUnknownKeys = true }
        .parseToJsonElement(
            Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)
                .decode(payload).decodeToString(),
        ) as? JsonObject ?: return null

    val exp = json["exp"]?.jsonPrimitive?.content?.toLongOrNull() ?: return null
    val id = json["sub"]?.jsonPrimitive?.content
    val metadata = json["user_metadata"] as? JsonObject

    fun meta(vararg names: String): String =
        names.firstNotNullOfOrNull { metadata?.get(it)?.jsonPrimitive?.content }.orEmpty()

    Claims(
        expiresAt = exp * 1000L,
        reader = id?.let {
            Reader(
                id = it,
                email = json["email"]?.jsonPrimitive?.content.orEmpty(),
                /* Providers disagree about which of these they
                   send, and a reader with a name should not be
                   greeted by their email because one of them
                   called it something else. */
                name = meta("name", "full_name", "user_name"),
                picture = meta("picture", "avatar_url"),
            )
        },
    )
}.getOrNull()

/** Whether a session needs refreshing before it is used. */
fun needsRefresh(session: Session, now: Long): Boolean =
    now >= session.expiresAt - Supabase.EARLY_MS

/* ============================================================
   The three things an account holds that are not a tick.

   Progress has a local copy because four schools have read
   localStorage since before there were accounts, and a reader
   with no account still gets all of it. None of these has that
   history and none of them works signed out, so none of them has
   a local copy: a second record to keep in step for nobody's
   benefit.
   ============================================================ */

/** A page a reader saved, or wrote a note on.

    ONE ROW PER PERSON PER PAGE, with `saved` and `note` as two
    columns of it rather than two tables. They are two facts about
    one thing, and a trigger on the site removes the row once both
    have gone, so the list can be COUNTED rather than filtered. */
@kotlinx.serialization.Serializable
data class Kept(
    val id: String = "",
    val url: String = "",
    val title: String = "",
    /** `piece` or `lesson`. */
    val kind: String = "piece",
    val saved: Boolean = false,
    val note: String = "",
)

/** A goal with a number on it.

    Three kinds, and they are three sources of progress that
    already existed rather than three shapes somebody invented: a
    `course` reads the reader's ticks, a `habit` reads
    `days-active`, and a `metric` is a number this site cannot
    see, so the reader types it in.

    **A fourth kind has to pass that test.** If the site cannot
    measure it out of something it already holds, the bar would be
    a decoration. */
@kotlinx.serialization.Serializable
data class Target(
    val id: String = "",
    val kind: String = "course",
    /** A course id for `course`, a unit of time for `habit`, free
        text naming the number for `metric`. */
    val subject: String = "",
    val label: String = "",
    val target: Double = 0.0,
    /** Only ever written for `metric`. The other two are computed
        from what the reader has actually done, and a stored copy
        of a derived number is a copy that goes stale. */
    val reached: Double = 0.0,
    val unit: String = "",
    @SerialName("done_at") val doneAt: String? = null,
)

/* ---------- a filled-in calculator, under a name ---------- */

/**
 * `public.scenarios`. One saved check.
 *
 * `inputs` is whatever shape the calculator already had for its
 * own state, and the stock check's shape is its QUERY STRING:
 * the format it has shared analyses in since it was written. A
 * second serialisation of the same fifty-six fields would be a
 * second thing to keep in step with the model, and this one is
 * already proved correct by every link anybody has ever copied
 * off that page.
 *
 * `summary` is one line of the ANSWER, stored so a list can be
 * drawn without loading the model that produced it.
 */
@Serializable
data class Scenario(
    val id: String = "",
    /** Which calculator. `stock` today. */
    val tool: String = "stock",
    val name: String = "",
    val inputs: ScenarioInputs = ScenarioInputs(),
    val summary: String = "",
    @SerialName("updated_at") val updatedAt: String? = null,
)

/** The stock check's own shape for `inputs`: its query string,
    with no leading `?`. Named rather than a free map, because the
    site writes exactly this key and a typo here is a saved check
    that opens empty. */
@Serializable
data class ScenarioInputs(val query: String = "")

/* ---------- what an account says about its reader ---------- */

/**
 * `public.profiles`, the one table on this site whose select
 * policy is `using (true)`.
 *
 * Every field is nullable because it arrives over a wire and
 * because two of them mean something when absent: `setup_at` null
 * IS "has never been asked", and that is what decides whether the
 * form says "Set up your account" or "Your settings".
 */
@Serializable
data class Profile(
    @SerialName("display_name") val displayName: String? = null,
    /** The school ids this reader said they were learning. A
        CHECK constraint in Postgres allows only the four in
        `LADDER_SCHOOLS`, so a value not in that list is a 400 on
        the whole write rather than one ignored field. */
    val following: List<String>? = null,
    val pace: String? = null,
    /** When they answered, or null if they never have. Set on the
        first save whether or not anything was ticked: somebody
        who saves a name and nothing else has been through setup,
        and the page has to stop asking. */
    @SerialName("setup_at") val setupAt: String? = null,
)

/** One answer to one of the account's questions.

    The site's `shared/profile.ts` shape, arriving through
    `/api/site`: the ids are CHECK constraint values and are NOT
    spelled in this app, so a fourth pace reaches a phone with no
    release. */
@Serializable
data class Choice(
    val id: String = "",
    val label: String = "",
    val note: String = "",
)

/** The two vocabularies together, as the manifest carries them. */
@Serializable
data class ProfileWords(
    val paces: List<Choice> = emptyList(),
    @SerialName("targetKinds") val targetKinds: List<Choice> = emptyList(),
)

/** How far along a target is, worked out rather than read.

    A `course` counts ticks and a `habit` counts days, both from
    what this device already holds, so the bar is right the moment
    a lesson is ticked rather than after an exchange. Only a
    `metric` uses the stored number, because only a metric has
    one the site could not compute. */
fun reachedFor(
    target: Target,
    ticks: (String) -> Int,
    daysActive: Int,
): Double = when (target.kind) {
    "course" -> ticks(target.subject).toDouble()
    "habit" -> daysActive.toDouble()
    else -> target.reached
}

/** And whether it is finished, which is NOT the same as the bar
    reaching the end.

    Somebody may decide a goal is done at eighty per cent, and
    somebody else may pass a number and want to keep going. The
    reader says, and `done_at` is where they said it. */
fun isDone(target: Target): Boolean = !target.doneAt.isNullOrBlank()
