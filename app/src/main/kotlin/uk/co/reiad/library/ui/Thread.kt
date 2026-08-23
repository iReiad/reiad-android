package uk.co.reiad.library.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import uk.co.reiad.library.core.Comment
import uk.co.reiad.library.core.Kind
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight

/* ============================================================
   The thread under a piece or a lesson.

   ---- what this screen must NOT do ----

   Show your own comment back to you after you leave one.

   The endpoint deliberately returns no row, precisely so a page
   cannot render what it just sent, and "shown immediately and
   confirmed after" is exactly what `keep()` does one file away:
   the right pattern for a bookmark and the one thing moderation
   exists to prevent here. What a reader gets instead is a
   sentence saying their words are waiting, which is true, and
   the thread unchanged, which is also true.

   An admin is the exception and it is the SERVER'S exception:
   their own words are already live, so the screen re-reads the
   thread rather than inventing a row.

   ---- pieces only, and that is the site's choice ----

   `next/app/[section]/[slug]/page.tsx` renders `<Comments>` and
   the lesson route does not. A thread under a lesson would be a
   feature this app invented, filed where the moderation queue
   does not expect it, and read by nobody. The endpoint would
   accept it, which is exactly why the restraint has to be here.

   ---- and a body is TEXT ----

   Never `BodyView`, never the article vocabulary, never a
   sanitiser. `core/Comments.kt` says why at length: a sanitiser
   here would imply the body could contain markup.
   ============================================================ */

/** What the thread is doing, as one value.

    `posting` and `said` are separate from `problem` because they
    are about the reader's own attempt rather than about the
    thread: a failed post must not blank a thread that loaded. */
data class ThreadState(
    val slug: String = "",
    val section: String = "",
    val comments: List<Comment> = emptyList(),
    val count: Int = 0,
    val loading: Boolean = true,
    val stale: Boolean = false,
    /** Could not be read at all. Different from an empty thread,
        which is a real answer. */
    val problem: String? = null,
    val posting: Boolean = false,
    /** What the server said about the reader's own last attempt. */
    val said: String? = null,
    val wrong: Boolean = false,
)

@Composable
fun Thread(
    state: ThreadState,
    signedIn: Boolean,
    onLeave: (body: String, parentId: Int?) -> Unit,
    onRetry: () -> Unit,
) {
    val c = LocalReiad.current
    Column(Modifier.fillMaxWidth()) {
        Text(
            "কথা",
            style = MaterialTheme.typography.titleSmall,
            color = c.ink,
        )
        Spacer(Modifier.height(Gap.s3))
        Text(
            /* Counted from what the server sent rather than from
               the list's length: `count` is every row including
               replies and `comments.size` is only the tops. The
               site prints the first. */
            when {
                state.loading -> "…"
                state.count == 0 -> "Nothing here yet."
                state.count == 1 -> "One comment."
                else -> "${state.count} comments."
            },
            style = MaterialTheme.typography.bodySmall,
            color = c.inkSoft,
        )
        if (state.stale) {
            Spacer(Modifier.height(Gap.s4))
            StaleNote()
        }
        Spacer(Modifier.height(Gap.s6))

        when {
            state.loading -> Skeleton(lines = 2, label = "Reading the thread")
            state.problem != null ->
                Problem("The thread would not load", state.problem, onRetry = onRetry)
            else -> {
                Column(verticalArrangement = Arrangement.spacedBy(Gap.s5)) {
                    for (comment in state.comments) {
                        CommentBlock(comment, signedIn, state.posting, onLeave)
                    }
                }
            }
        }

        Spacer(Modifier.height(Gap.s7))
        if (signedIn) {
            Leave(
                placeholder = "Say something",
                busy = state.posting,
                onSend = { text -> onLeave(text, null) },
            )
        } else {
            Text(
                "Sign in to leave a comment.",
                style = MaterialTheme.typography.bodySmall,
                color = c.inkSoft,
            )
        }
        state.said?.let { said ->
            Spacer(Modifier.height(Gap.s4))
            Text(
                said,
                style = MaterialTheme.typography.bodySmall,
                color = if (state.wrong) c.danger else c.accent,
            )
        }
    }
}

/** One comment and, where it has them, its replies.

    A reply gets NO reply control, which is the one-level rule
    drawn rather than only enforced: offering one would be a
    button whose only outcome is a 400. */
@Composable
private fun CommentBlock(
    comment: Comment,
    signedIn: Boolean,
    busy: Boolean,
    onLeave: (String, Int?) -> Unit,
) {
    var replying by remember(comment.id) { mutableStateOf(false) }
    val c = LocalReiad.current
    /* Full width, always. A `Pane` sizes to its content, so a
       column of them came out with a ragged right edge: three
       cards of three different widths reads as a layout that has
       gone wrong rather than as three comments of different
       lengths. */
    Pane(Modifier.fillMaxWidth()) {
        Said(comment)
        if (signedIn) {
            Spacer(Modifier.height(Gap.s4))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (replying) "Not now" else "Reply",
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = Faces.mono,
                    color = c.accent,
                    modifier = Modifier
                        .heightIn(min = Gap.tap)
                        .clip(RoundedCornerShape(Corner.pill))
                        .clickable(role = Role.Button) { replying = !replying }
                        .padding(horizontal = Gap.s5, vertical = Gap.s5),
                )
            }
        }
        if (replying) {
            Spacer(Modifier.height(Gap.s4))
            Leave(
                placeholder = "Reply to ${comment.authorName}",
                busy = busy,
                onSend = { text -> onLeave(text, comment.id); replying = false },
            )
        }
        for (reply in comment.replies) {
            Spacer(Modifier.height(Gap.s5))
            Row(Modifier.height(IntrinsicSize.Min)) {
                /* Indented by a RAIL rather than by padding, so
                   the relationship survives a long name and a
                   narrow screen. Full height, so it runs the
                   length of however many lines the reply is. */
                Box(
                    Modifier
                        .width(2.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(Corner.xs))
                        .background(c.hairline),
                )
                Spacer(Modifier.width(Gap.s6))
                Column(Modifier.weight(1f)) { Said(reply) }
            }
        }
    }
}

/** A name, a time, and the words.

    `Text`, always. See the note at the top of the file. */
@Composable
private fun Said(comment: Comment) {
    val c = LocalReiad.current
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                comment.authorName.ifBlank { "Reader" },
                style = MaterialTheme.typography.labelMedium,
                fontFamily = Faces.mono,
                color = c.ink,
            )
            Spacer(Modifier.width(Gap.s5))
            Text(
                /* The date only. A comment is not a chat message
                   and a minute-accurate timestamp on one invites
                   reading it as one. */
                comment.createdAt.take(10),
                style = MaterialTheme.typography.labelMedium,
                fontFamily = Faces.mono,
                color = c.inkSoft,
            )
        }
        Spacer(Modifier.height(Gap.s4))
        Text(
            comment.body,
            style = MaterialTheme.typography.bodyMedium,
            color = c.ink,
        )
    }
}

/** The box somebody types into, and the one control beside it. */
@Composable
private fun Leave(placeholder: String, busy: Boolean, onSend: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    Column(Modifier.fillMaxWidth()) {
        Field(
            value = text,
            onValue = { text = it },
            description = placeholder,
            hint = placeholder,
            /* A comment is a paragraph, so it gets the room for
               one. It was a single line 44dp tall, which is a box
               that says "one sentence" to somebody about to write
               five. */
            size = FieldSize.AREA,
            keyboard = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Default,
            ),
        )
        Spacer(Modifier.height(Gap.s5))
        PillButton(
            /* The site's own word. "Leave it" was meant as "leave
               a comment" and reads as "abandon it", which on the
               one button that PUBLISHES is the worst possible
               ambiguity. */
            label = if (busy) "Sending…" else "Post",
            kind = ButtonKind.SOLID,
            onClick = {
                val said = text.trim()
                if (!busy && said.isNotEmpty()) {
                    onSend(said)
                    text = ""
                }
            },
        )
    }
}
