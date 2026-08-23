package uk.co.reiad.library.account

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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import uk.co.reiad.library.core.CommentPosted
import uk.co.reiad.library.core.SITE_ORIGIN
import uk.co.reiad.library.core.commentProblem

/* ============================================================
   Leaving a comment.

   The one write in this app that does NOT go to Supabase. It
   goes to this site's own Worker, which verifies the reader's
   Supabase token itself, decides whether they are an admin, and
   files the comment as `live` or `pending` accordingly. That
   decision is the server's and cannot be moved: an app that
   posted straight to a table would be an app that decides its
   own moderation.

   The name is taken from the VERIFIED TOKEN by the endpoint and
   never from the body, so nothing here sends one. Somebody may
   rename themselves afterwards; the comment keeps who they were
   when they wrote it.
   ============================================================ */

/** What happened, in one shape, because both halves are one
    sentence to a reader. */
sealed interface Said {
    /** Up now: an admin's own words skip their own queue. */
    data object Live : Said

    /** Waiting for the person who runs the site. */
    data object Queued : Said

    data class Wrong(val why: String) : Said
}

class Say(private val account: Account) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val http = HttpClient(OkHttp) {
        install(HttpTimeout) {
            requestTimeoutMillis = 20_000
            connectTimeoutMillis = 10_000
        }
    }

    /**
     * @param parentId the comment being replied to, or null.
     *   ONE LEVEL: the endpoint refuses a reply to a reply, and
     *   the screen must not offer one either.
     */
    suspend fun leave(
        slug: String,
        section: String,
        body: String,
        parentId: Int? = null,
    ): Said {
        val token = account.token() ?: return Said.Wrong(commentProblem("sign-in-required"))
        return runCatching {
            val answer = http.post("$SITE_ORIGIN/api/comments") {
                header("Authorization", "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(
                    buildJsonObject {
                        put("slug", slug)
                        put("section", section)
                        put("body", body)
                        parentId?.let { put("parent_id", it) }
                    }.toString(),
                )
            }
            val text = answer.bodyAsText()
            if (!answer.status.isSuccess()) {
                /* The endpoint's OWN name for what went wrong.
                   Being rate limited is not the same as being
                   signed out, and one sentence for both is the
                   shape `sendLink()` was just fixed for. */
                val code = runCatching {
                    (json.parseToJsonElement(text) as? JsonObject)
                        ?.get("error")?.jsonPrimitive?.contentOrNull
                }.getOrNull()
                return Said.Wrong(commentProblem(code))
            }
            val said = json.decodeFromString(CommentPosted.serializer(), text)
            if (said.live) Said.Live else Said.Queued
        }.getOrElse {
            Said.Wrong("Could not reach the site: ${it.message ?: "no connection"}.")
        }
    }
}
