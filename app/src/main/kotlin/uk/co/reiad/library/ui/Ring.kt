package uk.co.reiad.library.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import uk.co.reiad.library.core.Motion

/* ============================================================
   How much of a school is read, as a ring.

   A groove bent into a circle: the same channel cut in, the same
   accent filling it, drawn round rather than along. It is the
   head of a school's page where a bar is the body of a card, and
   the distinction is about how much room there is rather than
   about what it means.

   ---- what it counts, and what it must not ----

   Lessons. Never checkpoints, and never practice-book days. A
   checkpoint is not a lesson and the site says so in as many
   words; letting one into this arithmetic would be the one way
   to get it wrong, because the number would still look
   plausible.
   ============================================================ */

@Composable
fun Ring(
    done: Int,
    total: Int,
    modifier: Modifier = Modifier,
    size: Dp = 84.dp,
    thickness: Dp = 7.dp,
) {
    val c = LocalReiad.current
    val target = if (total <= 0) 0f else (done.toFloat() / total).coerceIn(0f, 1f)
    /* It fills rather than appearing. A ring that is simply drawn
       at its value says a number; a ring that runs round to it
       says the reader put it there. */
    val filled by animateFloatAsState(target, tween(Motion.SLOW_MS), label = "ring")

    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val stroke = thickness.toPx()
            val inset = stroke / 2f
            val box = androidx.compose.ui.geometry.Size(
                this.size.width - stroke,
                this.size.height - stroke,
            )
            val at = androidx.compose.ui.geometry.Offset(inset, inset)

            /* The channel first, all the way round. */
            drawArc(
                color = c.hairline,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = at,
                size = box,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            if (filled > 0f) {
                /* From the top, clockwise, because that is where
                   a reader's eye starts on a dial. */
                drawArc(
                    color = c.accent,
                    startAngle = -90f,
                    sweepAngle = 360f * filled,
                    useCenter = false,
                    topLeft = at,
                    size = box,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
        Text(
            /* The COUNT, not the percentage. "12 of 60" is a
               reader's own answer to "how far have I got"; "20%"
               is a statistic about them. */
            "$done",
            style = MaterialTheme.typography.titleMedium,
            color = c.ink,
        )
    }
}
