package uk.co.reiad.library.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import uk.co.reiad.library.core.Lesson
import uk.co.reiad.library.core.Stage

/* ============================================================
   The head of a school, and the one card that says where to go
   back to.

   ---- nothing here is ever locked ----

   The site has never had a padlock on a stage, and this does not
   introduce one. A stage a reader has not laid the ground for
   says what it reads best after; a stage they have finished says
   so; a stage in the middle shows how far. Three labels, no
   gates, and the difference between a ladder and a corridor.

   ---- and the resume card is where they WERE ----

   Not where they got to. The bookmark moves on a visit and ticks
   nothing, because opening is not finishing, so this card can
   point at a lesson that is still unticked and that is correct:
   it is the way back into the middle of something, not a claim
   about what has been read.
   ============================================================ */

@Composable
fun SchoolHead(
    bn: String,
    en: String,
    done: Int,
    total: Int,
    modifier: Modifier = Modifier,
) {
    val c = LocalReiad.current
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                bn,
                style = if (isBangla(bn)) BanglaHeading.copy(
                    fontSize = MaterialTheme.typography.displaySmall.fontSize,
                ) else MaterialTheme.typography.displaySmall,
                color = c.ink,
            )
            Text(en, style = MaterialTheme.typography.bodyMedium, color = c.inkSoft)
            Spacer(Modifier.height(Gap.s4))
            Text(
                /* Counted, and written out in full rather than as
                   a percentage: "12 of 60" is a reader's own
                   answer to how far they have got. */
                "$done of $total read",
                style = MaterialTheme.typography.labelMedium,
                color = c.accent,
            )
        }
        Spacer(Modifier.width(Gap.s7))
        Ring(done, total)
    }
}

/** Back into the middle of something.

    Absent where there is nothing to go back to, rather than shown
    empty: a card offering to resume a school nobody has opened is
    a card that has to be read before it can be dismissed. */
@Composable
fun ResumeCard(
    stage: Stage?,
    lesson: Lesson?,
    onOpen: (Stage, Lesson) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (stage == null || lesson == null) return
    GoCard(
        title = lesson.bn,
        dek = (lesson.en ?: lesson.de ?: lesson.ar)?.takeIf { it.isNotBlank() },
        chip = stage.bn,
        go = "Pick up where you were",
        onOpen = { onOpen(stage, lesson) },
        modifier = modifier,
    )
}

/** What a stage's state is, in one line, and never a gate. */
@Composable
fun StageState(
    done: Int,
    total: Int,
    readsAfter: List<String>,
    modifier: Modifier = Modifier,
) {
    val c = LocalReiad.current
    val text = when {
        total == 0 -> "Being written"
        done == 0 && readsAfter.isNotEmpty() ->
            "Reads best after " + readsAfter.joinToString(" and ") + "."
        done == 0 -> "Not started"
        done >= total -> "Finished"
        else -> "$done of $total"
    }
    Text(
        text,
        style = if (isBangla(text)) BanglaBody.copy(
            fontSize = MaterialTheme.typography.bodySmall.fontSize,
            lineHeight = MaterialTheme.typography.bodySmall.fontSize * 1.6f,
        ) else MaterialTheme.typography.bodySmall,
        color = if (done >= total && total > 0) c.accent else c.inkSoft,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}
