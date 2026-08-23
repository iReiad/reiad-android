package uk.co.reiad.library.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import uk.co.reiad.library.core.Kind
import uk.co.reiad.library.core.Motion

/* ============================================================
   Nothing on this site is ever a blank screen.

   ---- what this file is for, in one story ----

   The stock check and the five calculators drew NOTHING at all
   for a reader who installed the app: a black page, on both, on
   every open. Every check passed, the whole model was correct to
   the last digit, and 119 tests agreed with the site.

   The screen waits for its words, which come down from
   `/api/tools` because they are DATA and must reach a phone with
   no app release. That endpoint did not exist yet in production.
   So `words` was null, and the branch for null drew a `Box` of
   fixed height with nothing in it, on the argument that a spinner
   flashing for one frame is worse than nothing.

   The argument was right and the code was wrong, and the
   difference is the one thing it did not consider: a fetch that
   never succeeds. `Cached` already carried the failure; the
   screen threw it away and kept the null.

   ---- the three states, and every fetch has all three ----

   | | |
   | --- | --- |
   | `Skeleton` | it is coming. Shapes the size of the thing, breathing, after a beat |
   | `Problem`  | it did not come. What failed, and a button |
   | the thing  | it came |

   There is no fourth, and in particular there is no "nothing".
   `Waiting()` was the fourth and it is gone.

   ---- why the skeleton waits before it appears ----

   Almost every one of these answers out of the device's own cache
   in one frame. A spinner that flashes for one frame says
   something went slowly when nothing did. So the shapes fade in
   over Motion.SLOW after a beat: fast answers show nothing, slow
   ones show the page arriving.
   ============================================================ */

/** One bar of a skeleton, breathing between two opacities. */
@Composable
private fun Bar(width: Float, height: Dp = 14.dp, corner: Dp = Corner.xs) {
    val c = LocalReiad.current
    val pulse = rememberInfiniteTransition(label = "skeleton")
    val a by pulse.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            tween(Motion.SLOW_MS, easing = androidx.compose.animation.core.LinearEasing),
            RepeatMode.Reverse,
        ),
        label = "breath",
    )
    Box(
        Modifier
            .fillMaxWidth(width)
            .height(height)
            .clip(RoundedCornerShape(corner))
            .background(c.paperSunk.copy(alpha = a)),
    )
}

/**
 * The shape of what is coming, while it comes.
 *
 * Deliberately the shape of a CARD rather than a circle going
 * round: a reader who can see the page arriving knows what page
 * it is, and a spinner tells them only that something is
 * happening somewhere.
 */
@Composable
fun Skeleton(
    modifier: Modifier = Modifier,
    lines: Int = 3,
    label: String = "Reading the site",
) {
    Column(
        modifier
            .fillMaxWidth()
            .semantics { contentDescription = label },
        verticalArrangement = Arrangement.spacedBy(Gap.s6),
    ) {
        Bar(0.34f, 12.dp, Corner.pill)
        Spacer(Modifier.height(Gap.s3))
        Bar(0.85f, 26.dp)
        repeat(lines) { Bar(if (it == lines - 1) 0.55f else 0.95f) }
    }
}

/**
 * It did not come, and here is what happened.
 *
 * Three things, and every one of them is the difference between
 * this and the blank page it replaces: a sentence naming what
 * failed, the reason underneath it, and a button that tries
 * again. An app that cannot say which of its own requests failed
 * leaves a reader pressing the same thing twice.
 *
 * It is an `InfoCard` with a control in it rather than a
 * `GoCard`: what failed is the end of the road, and the retry is
 * the one thing on it that acts.
 */
@Composable
fun Problem(
    title: String,
    detail: String? = null,
    modifier: Modifier = Modifier,
    retryLabel: String = "Try again",
    onRetry: (() -> Unit)? = null,
) {
    val c = LocalReiad.current
    InfoCard(title = title, dek = detail, modifier = modifier) {
        if (onRetry != null) {
            Spacer(Modifier.height(Gap.s6))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(Corner.pill))
                        .material(Kind.CONTROL, c, Corner.pill)
                        .clickable(role = Role.Button, onClick = onRetry)
                        .padding(horizontal = Gap.s7, vertical = Gap.s5),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            retryLabel.uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            color = c.accent,
                        )
                        Spacer(Modifier.width(Gap.s4))
                        Icon("chevron", size = 13.dp, tint = c.accent)
                    }
                }
            }
        }
    }
}

/** A line of prose saying the thing on screen is a saved copy.

    Quiet on purpose: a stale answer is still an answer, and a
    reader on a train should be told once rather than warned. */
@Composable
fun StaleNote(modifier: Modifier = Modifier) {
    val c = LocalReiad.current
    Text(
        "Saved copy. This is what the site last said.",
        modifier.alpha(0.85f),
        style = MaterialTheme.typography.bodySmall,
        color = c.inkSoft,
    )
}
