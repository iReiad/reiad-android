package uk.co.reiad.library.ui

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.coroutines.cancellation.CancellationException
import uk.co.reiad.library.core.Motion

/* ============================================================
   Back, as a gesture rather than as an event.

   A sheet that vanishes the instant a finger touches the left
   edge tells a reader nothing about what is underneath. Predictive
   back hands the drag over as it happens, so the sheet can move
   with the finger and come BACK if the gesture is abandoned,
   which is the whole difference: the reader finds out where they
   are going before they commit to it.

   `PredictiveBackHandler` gives a flow of progress, 0 to 1, and
   throws `CancellationException` into the collector if the reader
   changes their mind. That is the branch most implementations
   forget, and it is the one that makes the gesture trustworthy:
   without it a half-swipe leaves the sheet stranded.

   Reduced motion turns the movement off and keeps the handler,
   because back must still work.
   ============================================================ */

/** How far into a back gesture a surface is. */
@Stable
class Retreat internal constructor() {
    internal var progress by mutableFloatStateOf(0f)
    val amount: Float get() = progress
}

/** Installs the gesture, and gives back how far into it we are.

    `onBack` runs only if the reader finishes the gesture. An
    abandoned one eases the surface home and calls nothing. */
@Composable
fun rememberRetreat(enabled: Boolean = true, onBack: () -> Unit): Retreat {
    val retreat = remember { Retreat() }
    val settling = remember { Animatable(0f) }

    PredictiveBackHandler(enabled) { events ->
        try {
            events.collect { event -> retreat.progress = event.progress }
            /* Finished. Left where the finger left it, because the
               screen underneath is about to replace this one and
               animating home first would be a flicker. */
            retreat.progress = 0f
            onBack()
        } catch (cancelled: CancellationException) {
            /* Changed their mind. Ease it home rather than
               snapping: a surface that jumps back reads as a
               rejected gesture rather than an abandoned one. */
            settling.snapTo(retreat.progress)
            /* Home on a spring, the same one everything else in
               this app settles on: a gesture that was abandoned
               should feel like a thing let go of rather than a
               value being eased to nought. */
            settling.animateTo(
                0f,
                androidx.compose.animation.core.spring(
                    dampingRatio = 0.8f,
                    stiffness = 340f,
                ),
            ) { retreat.progress = value }
            retreat.progress = 0f
            throw cancelled
        }
    }
    return retreat
}

/** A surface following the gesture: it shrinks towards the edge
    the finger came from and fades a little.

    Deliberately small. The system is already drawing the screen
    underneath, so this only has to say "this one is leaving";
    anything bigger competes with what the reader is being shown. */
fun Modifier.retreating(retreat: Retreat, reduced: Boolean = false): Modifier =
    graphicsLayer {
        if (reduced) return@graphicsLayer
        val p = retreat.amount.coerceIn(0f, 1f)
        val shrink = 1f - p * 0.12f
        scaleX = shrink
        scaleY = shrink
        alpha = 1f - p * 0.35f
        transformOrigin = TransformOrigin(0.5f, 1f)
    }
