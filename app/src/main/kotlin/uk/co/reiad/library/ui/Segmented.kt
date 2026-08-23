package uk.co.reiad.library.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import uk.co.reiad.library.core.Kind
import uk.co.reiad.library.core.Motion

/* ============================================================
   The segmented control: a groove with a thumb RIDING in it.

   Every three-way choice in the app is this shape — the settings,
   the audience switch, a calculator's language — and each drew it
   as three grounds where one lit up. Correct in a screenshot and
   wrong in the hand: a choice is one thumb MOVING between
   positions, and a fill that teleports reads as three buttons
   that happen to be exclusive.

   So the thumb is one surface that slides, in the material's own
   fast step, and the finger gets the segment tick. Reduced motion
   snaps it, exactly as the glow snaps.
   ============================================================ */

@Composable
fun <T> Segmented(
    options: List<T>,
    chosen: T?,
    onChoose: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = Gap.tap,
) {
    if (options.isEmpty()) return
    val c = LocalReiad.current
    val reduced = rememberReducedMotion()
    val touch = rememberTouch()
    val dim = if (enabled) 1f else 0.45f

    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(Corner.pill))
            .material(Kind.GROOVE, c, Corner.pill, ground = c.paperSunk)
            .padding(Gap.s2),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            /* Every slot the same width, with a breath between
               them: options that touch read as one striped bar,
               and the thumb needs a lane to travel down. */
            val gap = Gap.s2
            val slot = (maxWidth - gap * (options.size - 1)) / options.size
            val at = options.indexOf(chosen)

            if (at >= 0) {
                val ride by animateDpAsState(
                    targetValue = (slot + gap) * at,
                    animationSpec = if (reduced) snap() else tween(Motion.FAST_MS),
                    label = "thumb",
                )
                Box(
                    Modifier
                        .offset(x = ride)
                        .width(slot)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(Corner.pill))
                        .material(
                            kind = Kind.CONTROL,
                            colours = c,
                            corner = Corner.pill,
                            ground = c.accent.copy(alpha = dim),
                        ),
                )
            }

            Row(
                Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(gap),
            ) {
                for (option in options) {
                    val on = option == chosen
                    val ink by animateColorAsState(
                        targetValue = (if (on) c.paper else c.inkSoft).copy(alpha = dim),
                        animationSpec = if (reduced) snap() else tween(Motion.FAST_MS),
                        label = "segment-ink",
                    )
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(Corner.pill))
                            .clickable(
                                /* The thumb is the indication: a
                                   ripple under a sliding surface
                                   is two answers to one press. */
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() },
                                role = Role.RadioButton,
                                enabled = enabled,
                            ) {
                                if (!on) {
                                    touch.tick()
                                    onChoose(option)
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            label(option),
                            style = MaterialTheme.typography.labelLarge,
                            color = ink,
                            /* Truncated inside the thumb rather
                               than wrapped out of it: a two-line
                               label in a pill this size spills
                               over the edge, and "Follow my
                               system" did exactly that. */
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
