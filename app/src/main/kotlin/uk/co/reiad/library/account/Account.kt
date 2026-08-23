package uk.co.reiad.library.account

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import uk.co.reiad.library.core.Arrival
import uk.co.reiad.library.core.Reader
import uk.co.reiad.library.core.Session
import uk.co.reiad.library.core.Supabase
import uk.co.reiad.library.core.encodeComponent
import uk.co.reiad.library.core.arrivalOf
import uk.co.reiad.library.core.authorizeUrl
import uk.co.reiad.library.core.needsRefresh
import uk.co.reiad.library.core.readToken

/* ============================================================
   Being signed in.

   `core/Auth.kt` builds the addresses and reads the tokens, and
   is tested. This is the parts that need a phone: opening a
   browser, keeping a session, and renewing it.

   ---- a Custom Tab, never a WebView ----

   A WebView is a second browser with its own cookie jar. A reader
   already signed in to the site on their phone would be asked to
   sign in again inside the app, and a password manager would not
   fill it because it is not the browser the reader uses. A Custom
   Tab IS their browser: the session is there, the manager works,
   and the address bar shows them whose page they are typing a
   password into, which for a sign-in is the whole point.

   ---- and the session is the device's, not the account's ----

   Kept in the same DataStore as everything else and cleared on
   sign out. The site's own rule is why that matters: signing out
   takes the mirror off, so the next person at the same handset
   does not inherit somebody's ticks.
   ============================================================ */

private val Context.session by preferencesDataStore(name = "reiad-session")

private val ACCESS = stringPreferencesKey("access_token")
private val REFRESH = stringPreferencesKey("refresh_token")
private val EXPIRES = stringPreferencesKey("expires_at")

class Account(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val http = HttpClient(OkHttp) {
        install(HttpTimeout) {
            requestTimeoutMillis = 15_000
            connectTimeoutMillis = 10_000
        }
    }

    /** Who is signed in, or nobody.

        Read from the stored token's own claims rather than kept
        as a second record, so there is one place a reader's name
        can come from and it cannot go stale against the token. */
    val reader: Flow<Reader?> = context.session.data.map { stored ->
        stored[ACCESS]?.let { readToken(it)?.reader }
    }

    val signedIn: Flow<Boolean> = context.session.data.map { it[ACCESS] != null }

    /** Sends the reader to their own browser to sign in.

        Returns null when the browser opened, and a sentence when
        it did not.

        ---- it used to return nothing, and swallow everything ----

        `runCatching { launchUrl(...) }` with no `getOrElse` is a
        button that does nothing at all when there is no browser
        that can answer a `VIEW` intent, and nothing on screen
        says so. That is indistinguishable from a broken account,
        it is what "signin still not working" looks like from the
        outside, and it needs a device to reproduce, which is
        exactly the kind of failure that has to be REPORTED rather
        than caught. */
    fun signIn(provider: String): String? {
        val url = Uri.parse(authorizeUrl(provider))
        /* A Custom Tab first, because it IS the reader's own
           browser: their session is there, their password manager
           works, and the address bar shows whose page they are
           typing into. */
        runCatching {
            CustomTabsIntent.Builder()
                .setShowTitle(true)
                /* The address bar stays visible. This is the one
                   screen in the app where a reader is typing a
                   password, and hiding whose page they are on
                   would be the wrong thing to save two lines of
                   height. */
                .setUrlBarHidingEnabled(false)
                .build()
                .launchUrl(context, url)
        }.onSuccess { return null }

        /* And a plain browser when there is no Custom Tabs
           provider. Not the same thing and not as good, but a
           reader who can sign in beats a reader who cannot. */
        return runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            null
        }.getOrElse {
            "This phone has no browser that can open a sign-in page."
        }
    }

    /** A magic link, for a reader with no Google account.

        Both ways in, because the site offers both and an app that
        offered fewer would be an account somebody could make on
        one and not reach on the other. */
    suspend fun sendLink(email: String): String? = runCatching {
        /* ---- `redirect_to` is a QUERY PARAMETER ----

           It was `options.email_redirect_to` in the body, which
           is the JS client library's shape and not this API's.
           GoTrue ignores a field it does not know, so the request
           returned 200, the screen said the link had been sent,
           the email arrived, and the link went to the SITE_URL
           default instead of back to the app. Every part of that
           looks like success.

           `aab/src/account.ts` does it the right way and has
           since it was written, which is what settled it: the
           site's magic link works and the app's did not, over one
           difference. */
        val answer = http.post(
            "${Supabase.AUTH}/otp?redirect_to=${encodeComponent(Supabase.REDIRECT)}",
        ) {
            header("apikey", Supabase.KEY)
            contentType(ContentType.Application.Json)
            /* The body is BUILT rather than interpolated, so a
               quote or a backslash in what somebody typed cannot
               end the string early and change the shape of the
               request. */
            setBody(
                buildJsonObject {
                    put("email", email)
                    put("create_user", true)
                }.toString(),
            )
        }
        /* And the ANSWER is read. `runCatching` around a call that
           cannot throw on a 400 turned every refusal into a
           success: a rate limit, a malformed address and a
           project with email sign-in switched off all reported
           "we have sent you a link". */
        if (answer.status.isSuccess()) {
            null
        } else {
            /* The server's OWN words, not a sentence made up
               here. "For security purposes, you can only request
               this after 47 seconds" and "Signups not allowed for
               otp" are both things GoTrue says plainly, and both
               were reported as "That email would not send",
               which tells a reader nothing and tells whoever is
               fixing it less. */
            val said = runCatching {
                json.parseToJsonElement(answer.bodyAsText())
                    .let { it as? JsonObject }
                    ?.let { o ->
                        (o["msg"] ?: o["message"] ?: o["error_description"] ?: o["error"])
                            ?.jsonPrimitive?.contentOrNull
                    }
            }.getOrNull()
            said?.ifBlank { null } ?: "The sign-in service answered ${answer.status.value}."
        }
    }.getOrElse { "Could not reach the sign-in service: ${it.message ?: "no connection"}." }

    /** What came back on the redirect. */
    suspend fun arrived(uri: String): Arrival {
        val arrival = arrivalOf(uri, System.currentTimeMillis())
        if (arrival is Arrival.SignedIn) keep(arrival.session)
        return arrival
    }

    private suspend fun keep(session: Session) {
        context.session.edit {
            it[ACCESS] = session.accessToken
            it[REFRESH] = session.refreshToken
            it[EXPIRES] = session.expiresAt.toString()
        }
    }

    /** A usable access token, refreshed if it is about to expire.

        Null means signed out, and a FAILED refresh signs out
        rather than returning the stale token: a token the server
        has stopped accepting is not a session, and pretending it
        is turns every later request into a silent failure. */
    suspend fun token(): String? {
        val stored = context.session.data.first()
        val access = stored[ACCESS] ?: return null
        val refresh = stored[REFRESH] ?: return null
        val expires = stored[EXPIRES]?.toLongOrNull() ?: 0L
        val session = Session(access, refresh, expires, null)
        if (!needsRefresh(session, System.currentTimeMillis())) return access

        val renewed = renew(refresh)
        if (renewed == null) {
            signOut()
            return null
        }
        keep(renewed)
        return renewed.accessToken
    }

    private suspend fun renew(refresh: String): Session? = runCatching {
        val text = http.post("${Supabase.AUTH}/token?grant_type=refresh_token") {
            header("apikey", Supabase.KEY)
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject { put("refresh_token", refresh) }.toString())
        }.bodyAsText()

        val fields = json.parseToJsonElement(text) as? JsonObject ?: return@runCatching null
        val access = fields["access_token"]?.jsonPrimitive?.contentOrNull
            ?: return@runCatching null
        /* A refresh may or may not hand back a new refresh token.
           Keeping the old one where it does not is what stops a
           renewal from being the last one. */
        val next = fields["refresh_token"]?.jsonPrimitive?.contentOrNull ?: refresh
        val claims = readToken(access)
        Session(
            accessToken = access,
            refreshToken = next,
            expiresAt = claims?.expiresAt ?: (System.currentTimeMillis() + 3_600_000L),
            reader = claims?.reader,
        )
    }.getOrNull()

    /** Takes the mirror off.

        The session goes, and everything the account put on this
        device goes with it, which is `Sync.forget`. The next
        person at the same handset inherits nothing. */
    suspend fun signOut() {
        context.session.edit { it.clear() }
    }
}

