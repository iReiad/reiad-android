package uk.co.reiad.library.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import uk.co.reiad.library.core.Kind
import uk.co.reiad.library.core.Motion

/* ============================================================
   One bottom sheet, said once.

   Three overlays are this shape — the drawer, the settings, and
   whatever comes next — and each had its own scrim, its own
   retreat and its own Column, which is how the same two failures
   shipped three times:

   1. NO TOP CLEARANCE. A sheet is aligned to the bottom and
      grows upward, so a short menu looked right and a tall one
      grew until "Reading" sat behind the clock in the status
      bar. The sheet now stops below the status bar with a gap,
      and the rounded corners stay visible to say so.
   2. NO ARRIVAL. `if (open) Sheet()` popped fully formed, which
      reads as a glitch beside the site's own sheets. This one
      rises over the material's enter step and leaves faster
      than it came, which is the asymmetry every real lid has.

   Predictive back keeps its whole gesture: the sheet follows the
   finger, comes back if the gesture is abandoned, and the system
   takes over if it completes.
   ============================================================ */

@Composable
fun Sheet(
    onClose: () -> Unit,
    content: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit,
) {
    val c = LocalReiad.current
    /* Seated at once for reduced motion, and for a render with no
       clock: a snapshot is one frame, and one frame into the rise
       is a blank page with the sheet below it. */
    val still = rememberReducedMotion() || rememberStill()
    val scope = rememberCoroutineScope()

    /* 0 is off screen, 1 is seated. One value drives the panel,
       the scrim and nothing else. */
    val shown = remember(still) { Animatable(if (still) 1f else 0f) }
    LaunchedEffect(shown) {
        shown.animateTo(1f, tween(Motion.ENTER_MS))
    }
    val dismiss: () -> Unit = {
        scope.launch {
            if (!still) shown.animateTo(0f, tween(Motion.QUICK_MS))
            onClose()
        }
    }

    val retreat = rememberRetreat(onBack = onClose)
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = shown.value }
            /* The scrim closes it. Not a decoration: on a phone
               the outside of a sheet is the biggest and most
               obvious target there is. */
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = dismiss,
            ),
    ) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                /* The whole of the top-draw fix: a sheet may
                   grow, and where it stops is below the status
                   bar with a breath of page still showing, not
                   behind the clock. */
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = Gap.s8)
                .graphicsLayer {
                    translationY = size.height * (1f - shown.value)
                }
                .retreating(retreat, still)
                .clip(RoundedCornerShape(topStart = Corner.lg, topEnd = Corner.lg))
                .material(Kind.PANE, c, Corner.lg, ground = c.paper)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) { }
                .windowInsetsPadding(WindowInsets.navigationBars),
        ) {
            /* The handle: the one mark that says "this is a lid
               over the page, and it moves". */
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = Gap.s4)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(Corner.pill))
                    .background(c.inkSoft.copy(alpha = 0.35f)),
            )
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Gap.s8)
                    .padding(top = Gap.s6, bottom = Gap.s8)
                    .verticalScroll(rememberScrollState()),
            ) {
                content(dismiss)
            }
        }
    }
}
