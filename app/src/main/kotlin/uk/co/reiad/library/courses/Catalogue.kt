package uk.co.reiad.library.courses

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json
import uk.co.reiad.library.account.Account
import uk.co.reiad.library.core.CatalogueResponse
import uk.co.reiad.library.core.CourseResponse
import uk.co.reiad.library.core.QuizResponse
import uk.co.reiad.library.core.ReadingResponse
import uk.co.reiad.library.core.SITE_ORIGIN
import uk.co.reiad.library.core.TicketResponse
import uk.co.reiad.library.core.encodeComponent

/* ============================================================
   `/api/courses`, asked by the app with the app's own token.

   ---- why this exists at all, which is worth writing down ----

   The section used to be a Custom Tab. The card said "খুলুন", a
   browser opened `/skills/courses`, and for the one reader it
   belongs to, nothing was there.

   That is not a wrong path and it is not a bug in the page. It is
   structural. The site's reader session is a bearer token in the
   BROWSER's own storage, and this app's session is its own: two
   sessions, no way to pass one to the other. So the tab loads the
   shell, the shell asks the endpoint with no credential, and the
   reader is told they are signed out. On a phone where they are
   signed in, opening a section the app already KNOWS is theirs,
   because the answer to this very endpoint is what decides
   whether the card is drawn.

   No amount of fixing the URL fixes that. The fix is to ask the
   endpoint here, with the token in hand, which is what this file
   does.

   ---- and the boundary does not move ----

   Nothing is granted here. The Worker checks `isAdmin()` on every
   one of these routes and answers 403 to anybody else; the ticket
   routes are signed by a key only the Worker holds, name one
   file, and last half an hour. This app holds no list of course
   ids, ships none in its binary, and caches no catalogue to disk:
   what it has is a token and the same right to ask that a browser
   would have.

   ---- 401 and 403 are different sentences ----

   "You are not signed in" and "you are signed in and this is not
   yours" want different screens, and a page that conflates them
   offers a sign-in button to somebody already signed in. So the
   answer type carries the status, and the caller reads it.
   ============================================================ */

/**
 * What one call to the Worker came back with.
 *
 * A sealed answer rather than a nullable value: every failure on
 * this endpoint has a different thing to say to the reader, and
 * a null would flatten "your session expired", "this is not
 * yours", "Drive is not connected" and "the train went into a
 * tunnel" into one blank screen.
 */
sealed interface Answer<out T> {
    data class Got<T>(val value: T) : Answer<T>

    /** Signed out, or a token that would not renew. */
    data object SignedOut : Answer<Nothing>

    /** Signed in, and the server says this is not yours. */
    data class NotYours(val message: String) : Answer<Nothing>

    /** Anything else the server said, in the server's own words.

        The server's sentence and not a general one: this endpoint
        answers 503 with the name of the secret that is missing,
        and "that did not load, try again" instead of it is how
        the website's own player spent a week recommending a
        reload for something a reload could never fix. */
    data class Refused(val status: Int, val message: String) : Answer<Nothing>

    /** No answer at all. A network failure is not a permission
        failure, and the difference matters to somebody deciding
        whether to sign in again. */
    data class Unreachable(val message: String) : Answer<Nothing>
}

/** The value, where there is one, and every failure carried
    through untouched.

    Typed rather than cast: each of the four failures is an
    `Answer<Nothing>`, which is an `Answer<R>` for any R, so this
    needs no unchecked cast and cannot lose a reason. */
fun <T, R> Answer<T>.map(change: (T) -> R): Answer<R> = when (this) {
    is Answer.Got -> Answer.Got(change(value))
    Answer.SignedOut -> Answer.SignedOut
    is Answer.NotYours -> this
    is Answer.Refused -> this
    is Answer.Unreachable -> this
}

/** The sentence to put on the screen, for the four that are not
    an answer. Null when there is one. */
val Answer<*>.problem: String?
    get() = when (this) {
        is Answer.Got<*> -> null
        Answer.SignedOut -> "Your session has expired. Sign in again to carry on."
        is Answer.NotYours -> message
        is Answer.Refused -> message
        is Answer.Unreachable -> message
    }

/** The Worker's own `fail()` shape, for reading a reason out of
    a response that is not a 200. */
@kotlinx.serialization.Serializable
private data class Failure(
    val ok: Boolean = false,
    val reason: String = "",
    val message: String = "",
)

/**
 * What a LESSON screen needs, which is less than the whole client.
 *
 * Three calls, named as an interface rather than taken as the
 * concrete `Catalogue`, and the reason is not ceremony: the
 * concrete one holds a credential and talks to the live endpoint
 * by design. A screen that names it cannot be composed anywhere
 * without a session and a socket, so the test that audits that
 * screen did real disk I/O inside a composition to reach a
 * refusal card. That is a slow test and an honest way to make a
 * whole suite flaky.
 *
 * The catalogue's two LIST calls are deliberately not here. They
 * belong to `Desk`, which fetches once for five views; a lesson
 * asks only about its own files.
 */
interface LessonSource {
    suspend fun ticket(drive: String): Answer<TicketResponse>
    suspend fun reading(drive: String): Answer<ReadingResponse>
    suspend fun quiz(drive: String): Answer<QuizResponse>
}

class Catalogue(private val account: Account) : LessonSource {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        coerceInputValues = true
    }

    private val http = HttpClient(OkHttp) {
        install(HttpTimeout) {
            /* A reading is a scraped web page and the largest in
               this catalogue is about half a megabyte, so this is
               longer than the manifest's fifteen seconds. The
               video does not come through here at all: ExoPlayer
               fetches the bytes itself, over the ticket. */
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 10_000
        }
    }

    /** The shelf: every programme, its totals, and the courses in
        it. No Drive id anywhere, because the shelf draws none. */
    suspend fun shelf(): Answer<CatalogueResponse> =
        ask("", CatalogueResponse.serializer())

    /**
     * One course, named the way its address names it.
     *
     * A course slug is unique across the catalogue, so this could
     * have taken one segment. It takes both because the screen
     * asks with the address it is on, and a route that quietly
     * accepts half of one stops matching the day two programmes
     * hold the same slug.
     */
    suspend fun course(programme: String, course: String): Answer<CourseResponse> =
        ask("/${encodeComponent(programme)}/${encodeComponent(course)}", CourseResponse.serializer())

    /** A pass for one file, good for half an hour. The whole
        address comes back with the pass already on it. */
    override suspend fun ticket(drive: String): Answer<TicketResponse> =
        ask("/ticket/${encodeComponent(drive)}", TicketResponse.serializer())

    /** A saved page, sanitised by the Worker with the same
        sanitiser the site's editor runs over an article, so what
        arrives is words and structure and goes through this
        phone's own body parser like any other prose. */
    override suspend fun reading(drive: String): Answer<ReadingResponse> =
        ask("/reading/${encodeComponent(drive)}", ReadingResponse.serializer())

    /**
     * A quiz, as questions rather than as somebody else's markup.
     *
     * A separate route from a reading and not an accident: every
     * option in a Coursera quiz lives inside a `<form>`, and the
     * sanitiser drops `form` whole, contents and all. Asking for
     * a quiz as a reading renders the prompts and silently
     * deletes every answer.
     */
    override suspend fun quiz(drive: String): Answer<QuizResponse> =
        ask("/quiz/${encodeComponent(drive)}", QuizResponse.serializer())

    /* ---------- the one call underneath all of them ---------- */

    private suspend fun <T> ask(path: String, of: DeserializationStrategy<T>): Answer<T> {
        val token = account.token() ?: return Answer.SignedOut

        val response = runCatching {
            http.get("$SITE_ORIGIN/api/courses$path") {
                header("Authorization", "Bearer $token")
            }
        }.getOrElse { why ->
            return Answer.Unreachable(
                when (why) {
                    is java.net.UnknownHostException ->
                        "No connection. This section is not saved on the phone: it is " +
                            "somebody else's material and nothing here keeps a copy."
                    is java.net.SocketTimeoutException,
                    is io.ktor.client.plugins.HttpRequestTimeoutException ->
                        "The site took too long to answer."
                    else -> why.message ?: "Could not reach the site."
                },
            )
        }

        val status = response.status.value
        val text = runCatching { response.bodyAsText() }.getOrDefault("")

        if (status !in 200..299) {
            val said = runCatching { json.decodeFromString(Failure.serializer(), text) }
                .getOrNull()
            return when (status) {
                401 -> Answer.SignedOut
                403 -> Answer.NotYours(
                    said?.message?.ifBlank { null }
                        ?: "This section is one person's own copy of a third-party " +
                        "course. It is not published.",
                )
                else -> Answer.Refused(
                    status,
                    said?.message?.ifBlank { null }
                        ?: said?.reason?.ifBlank { null }
                        ?: "That did not load ($status).",
                )
            }
        }

        return runCatching { json.decodeFromString(of, text) }
            .fold(
                onSuccess = { Answer.Got(it) },
                /* A 200 this app cannot read is the site having
                   changed shape, and saying so beats a blank
                   screen: `CoursesSurfaceTest` is the other half
                   of this, and it fails in CI rather than on a
                   handset. */
                onFailure = {
                    Answer.Refused(
                        status,
                        "The site answered in a shape this build does not know. " +
                            "It has probably changed since this app was built.",
                    )
                },
            )
    }
}
