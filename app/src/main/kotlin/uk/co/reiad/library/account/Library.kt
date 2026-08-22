package uk.co.reiad.library.account

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import uk.co.reiad.library.core.Kept
import uk.co.reiad.library.core.Supabase
import uk.co.reiad.library.core.Target
import uk.co.reiad.library.core.encodeComponent

/* ============================================================
   What an account holds that is not a tick.

   A reading list with notes on it, and targets. Neither has a
   local copy and that is deliberate rather than an omission:
   progress has one because four schools have read localStorage
   since before there were accounts, and a reader with no account
   still gets all of it. Nothing here has that history and nothing
   here works signed out, so a second copy would be a second
   record to keep in step for nobody's benefit.

   ---- the filter on a read is not a second lock, except once ----

   Every table here is `auth.uid() = user_id`, so a read with no
   filter returns your own rows and nothing else. That is true of
   `library` and `targets` and it is why neither call below names
   a user.

   It is NOT true of `profiles`, which is the one table on this
   site whose select policy is `using (true)`, because a comment
   has to show its author's name to somebody signed out. The site
   learned that the hard way: `getProfile()` asked for
   `profiles?select=...&limit=1` and PostgREST answered with
   whichever row the planner reached first, so with two accounts
   SAVING your profile is what made the next read return somebody
   else's. Anything added here that touches `profiles` carries
   `id=eq.<me>`.
   ============================================================ */

class Library(private val account: Account) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        encodeDefaults = true
    }

    private val http = HttpClient(OkHttp) {
        install(HttpTimeout) {
            requestTimeoutMillis = 20_000
            connectTimeoutMillis = 10_000
        }
    }

    private suspend fun <T> withToken(block: suspend (String) -> T): T? {
        val token = account.token() ?: return null
        return runCatching { block(token) }.getOrNull()
    }

    /* ---------- the reading list ---------- */

    /** Everything saved or noted, newest first.

        Both in one call, because they are one row: `saved` and
        `note` are two columns of one fact about one page, and a
        trigger removes the row once both have gone. */
    suspend fun kept(): List<Kept> = withToken { token ->
        val text = http.get(
            "${Supabase.REST}/library?select=id,url,title,kind,saved,note&order=updated_at.desc",
        ) {
            header("apikey", Supabase.KEY)
            header("Authorization", "Bearer $token")
        }.bodyAsText()
        json.decodeFromString(ListSerializer(Kept.serializer()), text)
    }.orEmpty()

    /** Saves, unsaves, or writes a note.

        An UPSERT rather than a read-then-decide, which is one
        round trip instead of two and cannot race with the same
        reader's laptop. `user_id` is the column's default from
        the token, so this device never names whose row it is
        writing: it cannot get that wrong and cannot be talked
        into getting it wrong.

        Each control writes only ITS OWN column. The site's own
        note is why: a Save and a note are two controls over one
        row, and one that sent the whole row would overwrite what
        the other had just put there. */
    suspend fun keep(
        url: String,
        title: String,
        kind: String,
        saved: Boolean? = null,
        note: String? = null,
    ): Boolean = withToken { token ->
        val row = buildJsonObject {
            put("url", url)
            put("title", title)
            put("kind", kind)
            saved?.let { put("saved", it) }
            note?.let { put("note", it) }
        }
        http.post("${Supabase.REST}/library?on_conflict=user_id,url") {
            header("apikey", Supabase.KEY)
            header("Authorization", "Bearer $token")
            header("Prefer", "resolution=merge-duplicates,return=minimal")
            contentType(ContentType.Application.Json)
            setBody(buildJsonArray { add(row) }.toString())
        }
        true
    } ?: false

    /* ---------- targets ---------- */

    suspend fun targets(): List<Target> = withToken { token ->
        val text = http.get(
            "${Supabase.REST}/targets?select=id,kind,subject,label,target,reached,unit,done_at" +
                "&order=created_at.desc",
        ) {
            header("apikey", Supabase.KEY)
            header("Authorization", "Bearer $token")
        }.bodyAsText()
        json.decodeFromString(ListSerializer(Target.serializer()), text)
    }.orEmpty()

    suspend fun addTarget(target: Target): Boolean = withToken { token ->
        http.post("${Supabase.REST}/targets") {
            header("apikey", Supabase.KEY)
            header("Authorization", "Bearer $token")
            header("Prefer", "return=minimal")
            contentType(ContentType.Application.Json)
            setBody(
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("kind", target.kind)
                            put("subject", target.subject)
                            put("label", target.label)
                            put("target", target.target)
                            /* Only a metric carries its own
                               number. Sending one for a course
                               would be storing a derived value,
                               which is a value that goes stale. */
                            if (target.kind == "metric") put("reached", target.reached)
                            put("unit", target.unit)
                        },
                    )
                }.toString(),
            )
        }
        true
    } ?: false

    suspend fun removeTarget(id: String): Boolean = withToken { token ->
        http.delete("${Supabase.REST}/targets?id=eq.${encodeComponent(id)}") {
            header("apikey", Supabase.KEY)
            header("Authorization", "Bearer $token")
            header("Prefer", "return=minimal")
        }
        true
    } ?: false
}

/* ============================================================
   Taking a copy, and erasing everything.

   Leaving should be as easy as arriving. The site says so and
   means it: one JSON file with the progress, the library, the
   targets and the profile in it, and an erase that takes the
   account AND the mirror.
   ============================================================ */

/** Everything this account holds, as one file.

    Built from what the account answers rather than from what this
    device happens to hold, because a copy of a mirror is not a
    copy of the record. A device that had never exchanged would
    otherwise export an empty file and call it everything. */
suspend fun Library.exportAll(
    reader: uk.co.reiad.library.core.Reader?,
    progress: Map<String, String>,
): String {
    val kept = kept()
    val targets = targets()
    return buildJsonObject {
        put("exported_from", "reiad.co.uk (Android)")
        putJsonObject("reader") {
            put("id", reader?.id.orEmpty())
            put("email", reader?.email.orEmpty())
            put("name", reader?.name.orEmpty())
        }
        putJsonObject("progress") {
            for ((key, value) in progress) {
                /* Written as the JSON it is rather than as a
                   string containing JSON, so the file is readable
                   by anything rather than needing a second parse
                   per key. */
                put(key, runCatching { Json.parseToJsonElement(value) }
                    .getOrElse { JsonPrimitive(value) })
            }
        }
        put(
            "library",
            Json.encodeToJsonElement(ListSerializer(Kept.serializer()), kept),
        )
        put(
            "targets",
            Json.encodeToJsonElement(ListSerializer(Target.serializer()), targets),
        )
    }.toString()
}
