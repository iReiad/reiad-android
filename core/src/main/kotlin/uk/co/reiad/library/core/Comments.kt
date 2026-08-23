package uk.co.reiad.library.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/* ============================================================
   A thread, under a piece or a lesson.

   ---- three rules, and all three are the SERVER'S ----

   1. A BODY IS TEXT. Not HTML, not the article vocabulary, not
      markdown. `functions/api/comments` stores what was typed and
      the page prints it, which is why there is no sanitiser in
      this path and must never be one: a sanitiser here would
      imply the body could contain markup, and the day somebody
      widened it, every reader's comment would be a place to put
      a script tag. `CommentsTest` asserts the app renders it as
      text and never through `BodyParser`.

   2. A REPLY IS ONE LEVEL. The endpoint refuses a reply to a
      reply with `replies-are-one-level`, and it checks the
      parent is on the SAME piece, so a reply cannot be smuggled
      under a thread it was never written for. The app has to
      honour it in the drawing too: offering Reply on a reply is
      a control whose only outcome is a 400.

   3. NOTHING APPEARS UNTIL IT IS APPROVED. The POST deliberately
      returns no row, so a page cannot render what it just sent.
      This app must not either: showing your own pending comment
      in the thread is the one thing moderation exists to
      prevent, and "shown immediately and confirmed after" is
      exactly the pattern `keep()` uses one file away, which is
      what makes it the mistake somebody would make here.
   ============================================================ */

/**
 * One comment, as `/api/comments?slug=` sends it.
 *
 * `replies` is assembled by the SERVER rather than by the page,
 * so nothing here has to know the shape of a thread to draw one.
 */
@Serializable
data class Comment(
    val id: Int = 0,
    val slug: String = "",
    val section: String = "",
    @SerialName("parent_id") val parentId: Int? = null,
    @SerialName("author_name") val authorName: String = "",
    val body: String = "",
    @SerialName("created_at") val createdAt: String = "",
    val replies: List<Comment> = emptyList(),
)

@Serializable
data class CommentsResponse(
    val ok: Boolean = true,
    val comments: List<Comment> = emptyList(),
    /** Every row including replies, which is not `comments.size`:
        that is the top-level count. The site prints this one. */
    val count: Int = 0,
)

/** What the server said about a comment that was just left.

    Two outcomes and they read differently to a reader: an
    admin's own words are up, and everybody else's are waiting.
    Neither is a row: see rule 3. */
@Serializable
data class CommentPosted(
    val ok: Boolean = true,
    val queued: Boolean = true,
    val live: Boolean = false,
)

/** The endpoint's own refusals, by the name it answers with.

    Read rather than guessed at, because each is a different
    sentence to a reader: being rate limited is not the same as
    being signed out, and "that did not send" for both is the
    shape `sendLink()` was just fixed for. */
fun commentProblem(code: String?): String = when (code) {
    "sign-in-required" -> "Sign in to leave a comment."
    "bad-token" -> "Your session has expired. Sign in again."
    "too-many" -> "That is a lot of comments at once. Try again in a minute."
    "empty" -> "There is nothing in it."
    "replies-are-one-level" -> "You can reply to a comment, but not to a reply."
    "no-such-parent" -> "The comment you replied to has gone."
    "slug-required" -> "This page cannot take comments."
    null -> "That did not send."
    else -> "That did not send: $code."
}
