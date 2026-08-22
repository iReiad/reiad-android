package uk.co.reiad.library.broker

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import uk.co.reiad.library.account.Account
import uk.co.reiad.library.core.SITE_ORIGIN
import uk.co.reiad.library.core.broker.PublicAnswer
import uk.co.reiad.library.core.broker.PublicPortfolio

/* ============================================================
   The live portfolio.

   ---- this app never speaks to the broker ----

   Not once, and it must not start. Everything here calls
   `/api/broker/<route>` on this site, and the Worker behind it is the
   only caller of `live.trading212.com`, which is what makes the
   rate limiting honest: the broker's limits are per ACCOUNT, so
   the one place that can meter requests is the one place they all
   pass through.

   The site says the same in stronger terms: do not write the
   broker's hostname into anything the browser ships, because
   `check-csp.ts` scans for it and will rightly fail the build.
   The same rule holds here for the same reason, minus the check.

   ---- three readers, three answers ----

   | signed out          | `public`: the site's own portfolio in percentages. A weight and a return teach something; a balance only says how much money somebody else has. |
   | signed in, no key   | the same, plus an offer to connect their own account |
   | signed in, with key | `live`: their own account, in full, in their own currency |

   The key itself is never held here. It is sealed by the Worker
   under a wrangler secret and stored as ciphertext in a row only
   its owner can read, so this app can ask whether one EXISTS and
   can ask for the numbers it unlocks, and cannot ever see it.
   ============================================================ */

/** What `/api/broker/me` says about the reader. */
data class Standing(
    val admin: Boolean = false,
    /** Whether the site can seal a key at all. Without the
        secret there is nothing to store one in, and offering to
        save one would be a promise the site cannot keep. */
    val sealing: Boolean = false,
    /** A key already saved, by the label its owner gave it. */
    val savedLabel: String? = null,
    val publicConfigured: Boolean = false,
)

/** Trading 212's own answer, unchanged, which is what the Worker
    passes through. It is parsed by `core/broker/Portfolio.kt`
    rather than deserialised into a class, because every field in
    it belongs to somebody else. */
data class Account212(
    val at: String = "",
    val summary: JsonElement? = null,
    val positions: JsonElement? = null,
)

/** Why an answer did not come back, in the site's own vocabulary.

    Named rather than reduced to null, because these are four
    different things to say to a reader and only one of them is a
    fault: `no-key` means "connect an account", `sign-in-required`
    means "sign in", `too-many` means "wait a minute", and
    `not-configured` means the site has no key of its own yet. */
data class Trouble(val reason: String, val message: String? = null)

sealed interface Answer<out T> {
    data class Got<T>(val value: T) : Answer<T>
    data class Failed(val trouble: Trouble) : Answer<Nothing>
}

class Broker(private val account: Account) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val http = HttpClient(OkHttp) {
        install(HttpTimeout) {
            requestTimeoutMillis = 20_000
            connectTimeoutMillis = 10_000
        }
    }

    /** The site's own portfolio, in percentages. No account
        needed, which is the whole point of it. */
    suspend fun public(): Answer<PublicPortfolio> = ask("public", signedIn = false) { body ->
        json.decodeFromString(PublicAnswer.serializer(), body).portfolio
    }

    suspend fun me(): Answer<Standing> = ask("me") { body ->
        val o = json.parseToJsonElement(body).jsonObject
        val saved = o["saved"] as? JsonObject
        Standing(
            admin = o["admin"]?.jsonPrimitive?.content == "true",
            sealing = o["sealing"]?.jsonPrimitive?.content == "true",
            savedLabel = saved?.get("label")?.jsonPrimitive?.content,
            publicConfigured = o["publicConfigured"]?.jsonPrimitive?.content == "true",
        )
    }

    /** The reader's own account, if they have connected one. */
    suspend fun live(): Answer<Account212> = ask("live") { body ->
        val o = json.parseToJsonElement(body).jsonObject["account"]?.jsonObject
        o?.let {
            Account212(
                at = it["at"]?.jsonPrimitive?.content ?: "",
                summary = it["summary"],
                positions = it["positions"],
            )
        }
    }

    /** Dividends, fills and transfers. One page of each, which is
        the largest the broker allows. */
    suspend fun history(): Answer<JsonObject> = ask("history") { body ->
        json.parseToJsonElement(body).jsonObject["history"]?.jsonObject
    }

    /** One call, with the reader's bearer where there is one.

        A failure is a NAMED reason rather than a null, and the
        reason comes out of the body: the Worker answers 428 with
        `no-key` for a reader who has not connected an account,
        and that is an invitation rather than an error. */
    private suspend fun <T> ask(
        route: String,
        signedIn: Boolean = true,
        read: (String) -> T?,
    ): Answer<T> {
        val token = if (signedIn) account.token() else null
        if (signedIn && token == null) return Answer.Failed(Trouble("sign-in-required"))

        val answer = runCatching {
            http.get("$SITE_ORIGIN/api/broker/$route") {
                if (token != null) header("Authorization", "Bearer $token")
            }
        }.getOrElse { return Answer.Failed(Trouble("offline", it.message)) }

        val body = runCatching { answer.bodyAsText() }.getOrDefault("")
        if (answer.status.value !in 200..299) {
            val o = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
            return Answer.Failed(
                Trouble(
                    reason = o?.get("error")?.jsonPrimitive?.content ?: "failed",
                    message = o?.get("message")?.jsonPrimitive?.content,
                ),
            )
        }
        val value = runCatching { read(body) }.getOrNull()
            ?: return Answer.Failed(Trouble("unreadable"))
        return Answer.Got(value)
    }
}
