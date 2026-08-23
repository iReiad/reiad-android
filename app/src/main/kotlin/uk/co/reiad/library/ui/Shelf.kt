package uk.co.reiad.library.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import uk.co.reiad.library.core.Kind
import uk.co.reiad.library.data.Held
import uk.co.reiad.library.data.sizeWords

/* ============================================================
   The shelf, said to a reader.

   One question and one honest answer: will this school work on a
   plane, and does it work NOW. Both of those are facts the app
   can check rather than promise, so this component reports rather
   than reassures: it counts what is actually in the cache against
   what the ladder says exists.

   "Kept" is not a progress bar and not a percentage. It is a
   fraction of lessons, because a reader who is about to fly wants
   to know whether the number on the left is the number on the
   right, and nothing else about it.
   ============================================================ */

/**
 * Keep this school on the phone.
 *
 * A `control`, because a lone button on a page has to look
 * pressable: the site's own rule. It latches, which is why the
 * pressed state is a filled accent rather than a tick beside it.
 */
@Composable
fun KeepSchool(
    following: Boolean,
    held: Held,
    lessons: Int,
    waiting: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalReiad.current
    val complete = lessons > 0 && held.lessons >= lessons
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PillButton(
                label = if (following) "Kept on this phone" else "Keep for offline",
                icon = if (following) "check" else "seed",
                filled = following,
                description = if (following) {
                    "Stop keeping this school on the phone"
                } else {
                    "Keep this school on the phone"
                },
                onClick = onToggle,
            )
            if (following && lessons > 0) {
                Spacer(Modifier.width(Gap.s6))
                Text(
                    "${held.lessons} of $lessons",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (complete) c.accent else c.inkSoft,
                )
            }
        }

        AnimatedVisibility(following) {
            Column {
                Spacer(Modifier.height(Gap.s5))
                Text(
                    when {
                        complete -> "Every lesson is on this phone. " +
                            "It reads with no connection at all."
                        waiting -> "Waiting for wifi. It will not spend your mobile data " +
                            "on a megabyte of prose without being asked."
                        else -> "Fetching the rest in the background. " +
                            "You can leave this page."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = c.inkSoft,
                )
            }
        }
    }
}

/**
 * What the app is holding, and the way to be rid of it.
 *
 * No bar chart and no eviction policy, deliberately: the whole
 * corpus is four schools of prose and there is nothing to manage.
 * What a reader actually wants here is a number they can check
 * against their own sense of "is this app hoarding", and one
 * button.
 */
@Composable
fun HeldPanel(held: Held, onForget: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalReiad.current
    Pane(modifier.fillMaxWidth()) {
        Text(
            "What is on this phone",
            style = MaterialTheme.typography.titleSmall,
            color = c.ink,
        )
        Spacer(Modifier.height(Gap.s4))
        Text(
            if (held.lessons == 0) {
                "Nothing kept yet. Follow a school and it reads offline."
            } else {
                "${held.lessons} lesson${if (held.lessons == 1) "" else "s"}, " +
                    sizeWords(held.bytes) + "."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = c.inkSoft,
        )
        if (held.bytes > 0) {
            Spacer(Modifier.height(Gap.s6))
            PillButton("Forget it all", onForget)
            Spacer(Modifier.height(Gap.s4))
            Text(
                /* Said because it is the question a reader asks
                   before pressing it, and the answer is the whole
                   reason the prefix guard exists in `Shelf.kt`. */
                "Your ticks, your notes and your account are not touched.",
                style = MaterialTheme.typography.bodySmall,
                color = c.inkSoft,
            )
        }
    }
}
