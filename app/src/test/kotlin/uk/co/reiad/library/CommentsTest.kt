package uk.co.reiad.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import uk.co.reiad.library.core.Comment
import uk.co.reiad.library.ui.ReiadTheme
import uk.co.reiad.library.ui.Thread
import uk.co.reiad.library.ui.ThreadState
import java.io.File
import kotlin.test.Test as KTest
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/* ============================================================
   A thread, and the three rules that are the SERVER'S.

   All three are enforced by `functions/api/comments`, so the app
   cannot break them in the sense of getting bad data into the
   database. It can break all three in the sense that matters,
   which is what a reader sees:

   1. A BODY IS TEXT. Rendering it through the article vocabulary
      would make every comment a place to put markup, on a path
      that has no sanitiser precisely because it does not need
      one.
   2. A REPLY IS ONE LEVEL. Offering Reply on a reply is a
      control whose only outcome is a 400.
   3. NOTHING APPEARS UNTIL IT IS APPROVED, INCLUDING YOUR OWN.
      The endpoint answers with no row so a page cannot render
      what it just sent. Adding it optimistically is the natural
      thing to write, it is what `keep()` does one file away, and
      it defeats the whole of moderation.
   ============================================================ */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xhdpi")
class CommentsTest {

    @get:Rule val compose = createComposeRule()

    private val thread = ThreadState(
        slug = "a-piece",
        section = "insights",
        count = 3,
        loading = false,
        comments = listOf(
            Comment(
                id = 1, authorName = "Rony", body = "A top-level comment.",
                createdAt = "2026-08-20T10:00:00Z",
                replies = listOf(
                    Comment(
                        id = 2, parentId = 1, authorName = "Someone",
                        body = "A reply to it.", createdAt = "2026-08-20T11:00:00Z",
                    ),
                ),
            ),
            Comment(
                id = 3, authorName = "Another", body = "<b>not markup</b>",
                createdAt = "2026-08-21T09:00:00Z",
            ),
        ),
    )

    private fun walk(n: SemanticsNode, into: MutableList<SemanticsNode>) {
        into.add(n); n.children.forEach { walk(it, into) }
    }

    private fun nodes(): List<SemanticsNode> =
        mutableListOf<SemanticsNode>().also { walk(compose.onRoot().fetchSemanticsNode(), it) }

    private fun mount(signedIn: Boolean, onLeave: (String, Int?) -> Unit = { _, _ -> }) {
        compose.setContent {
            ReiadTheme {
                Box(Modifier.fillMaxSize()) {
                    Thread(thread, signedIn = signedIn, onLeave = onLeave, onRetry = {})
                }
            }
        }
        compose.waitForIdle()
    }

    private fun labels(): List<String> = nodes().mapNotNull { n ->
        n.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }
    }

    /** RULE 2, drawn.

        One Reply control per TOP-LEVEL comment and none on a
        reply. Two comments, one of which has a reply: two Reply
        controls, not three. */
    @KTest fun aReplyIsNeverOfferedAReply() {
        mount(signedIn = true)
        val replies = nodes().count { n ->
            n.config.getOrNull(SemanticsActions.OnClick) != null &&
                n.config.getOrNull(SemanticsProperties.Text)
                    ?.any { it.text.trim() == "Reply" } == true
        }
        assertEquals(
            2,
            replies,
            "there are two top-level comments and one reply; a Reply control on the " +
                "reply is a button whose only outcome is a 400",
        )
    }

    /** RULE 1, drawn.

        A body that looks like markup is shown as the characters
        somebody typed. If this ever renders as bold, a body has
        started being parsed. */
    @KTest fun aBodyIsShownAsTheCharactersSomebodyTyped() {
        mount(signedIn = false)
        assertTrue(
            labels().any { it.contains("<b>not markup</b>") },
            "the body was not shown verbatim, so something is parsing it. " +
                "What is on screen: " + labels().joinToString(" | "),
        )
    }

    /** Signed out reaches no box at all, because the endpoint
        refuses a comment from nobody: a box that always answers
        "sign in" is a box that should not have been drawn. */
    @KTest fun signedOutIsToldRatherThanGivenABox() {
        mount(signedIn = false)
        assertTrue(
            labels().any { it.contains("Sign in to leave a comment") },
            "signed out should be told why there is no box",
        )
        assertTrue(
            nodes().none { n ->
                n.config.getOrNull(SemanticsActions.OnClick) != null &&
                    n.config.getOrNull(SemanticsProperties.Text)
                        ?.any { it.text.contains("Leave it") } == true
            },
            "signed out reached a Leave it button",
        )
    }

    /** RULE 3, in the SOURCE, because it is about what the model
        does rather than what the screen draws.

        `leaveComment` must never add a comment to the thread. The
        only permitted response to a successful post is a
        sentence, plus a re-read when the SERVER said the comment
        is already live. */
    @KTest fun aPendingCommentIsNeverPutIntoTheThread() {
        val main = File("src/main/kotlin/uk/co/reiad/library/MainActivity.kt").readText()
        val at = main.indexOf("fun leaveComment(")
        assertTrue(at > 0, "leaveComment is gone from MainActivity")
        val body = main.substring(at, main.indexOf("\n    }\n", at))
        /* Comments name the thing they are about, so the grep has
           to be on the code. */
        val code = body
            .replace(Regex("""/\*[\s\S]*?\*/"""), "")
            .replace(Regex("""(?m)//.*$"""), "")
        for (bad in listOf("comments =", "comments +", "_thread.value.comments")) {
            assertTrue(
                bad !in code,
                "leaveComment touches the comment list ('$bad'). The endpoint answers " +
                    "with no row precisely so a page cannot render what it just sent: " +
                    "showing your own pending words back to you is the one thing " +
                    "moderation exists to prevent.",
            )
        }
        assertTrue(
            "openThread(" in code,
            "an admin's own comment is filed live, so the thread should be RE-READ " +
                "rather than left stale",
        )
    }

    /** And the thread is re-read per SLUG, so walking from one
        piece to the next does not leave the last one's comments
        under the new piece. */
    @KTest fun theThreadIsKeyedOnTheSlug() {
        val main = File("src/main/kotlin/uk/co/reiad/library/MainActivity.kt").readText()
        assertTrue(
            Regex("""LaunchedEffect\(\s*shown\.slug\s*\)""").containsMatchIn(main),
            "the thread is not keyed on the piece's slug, so the previous piece's " +
                "comments would stay on screen under the next one",
        )
    }
}
