package uk.co.reiad.library.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/* ============================================================
   The ground the glass refracts.

   ---- why a field at all ----

   The bars, the menu and the pill all frost what is behind them,
   and the report on the result was one word: garbage. The blur
   was honest and there was nothing to blur: the page is prose on
   flat paper, so frosted paper looked like slightly greyer
   paper, and a translucent card over a solid colour is just a
   worse solid colour. Every glass interface that reads as glass
   is standing in front of SOMETHING: a wallpaper, a desktop, a
   photograph. This is the app's something.

   ---- what it is ----

   Three soft pools of the section's own accent, big, dim and
   badly out of focus on purpose, drawn once behind the whole
   page. Nobody is meant to look at it; it is meant to be what
   the frost differs from, what a card's bed lets one breath of
   through, and what makes two sheets of glass at different
   depths read as different depths.

   ---- and it is alive, which the site cannot be ----

   The pools drift on a ninety-second orbit, far below the speed
   anybody watches, and they lie at three different depths of
   parallax on the handset's own sway, so tilting the phone
   shifts the light behind the page the way it shifts the glint
   on the cards. A browser has no gyroscope. Reduced motion
   stills the orbit, and `rememberSway` already stills the
   parallax.

   Colours are the palette's own (accent, accentSoft), never a
   value typed here, so a school page's field is that school's
   and the money pages stay green while Deutsch is blue.
   ============================================================ */
private const val BEAT_MS = 500L
private const val ORBIT_MS = 90_000f

@Composable
fun AmbientGround(sway: Sway, modifier: Modifier = Modifier, moving: Boolean = true) {
    val c = LocalReiad.current

    /* One slow phase drives all four pools; each reads it at its
       own offset so they never breathe in step. At the first
       composition the phase is nought, which is what keeps every
       snapshot of every screen deterministic.

       A HEARTBEAT, not a frame animation, and the first draft of
       this was the difference. An infinite transition invalidates
       every frame, which for a ninety-second orbit is sixty
       redraws a second nobody can see: on a phone that is a
       warm battery, and under Robolectric it is a frame callback
       that reposts itself for ever, which `LaunchTest`'s looper
       drain turned into an OutOfMemoryError. Two beats a second
       moves each pool well under a pixel per step and costs two
       invalidations a second, all of it skipped when the reader
       asked for reduced motion. */
    var turn by remember { mutableFloatStateOf(0f) }
    if (moving) {
        LaunchedEffect(Unit) {
            while (true) {
                delay(BEAT_MS)
                turn = (turn + BEAT_MS.toFloat() / ORBIT_MS) % 1f
            }
        }
    }

    val strong = if (c.isDark) 0.15f else 0.10f

    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val r = max(w, h)

        fun pool(
            colour: Color,
            alpha: Float,
            atX: Float,
            atY: Float,
            radius: Float,
            phase: Float,
            depth: Float,
            orbit: Float,
        ) {
            val a = (turn + phase) * 2f * Math.PI.toFloat()
            val centre = Offset(
                x = atX * w + sin(a) * orbit + sway.x * depth,
                y = atY * h + cos(a) * orbit * 0.7f + sway.y * depth,
            )
            drawRect(
                Brush.radialGradient(
                    0f to colour.copy(alpha = alpha),
                    1f to Color.Transparent,
                    center = centre,
                    radius = max(1f, radius),
                ),
            )
        }

        /* Highest sits deepest: the top pool moves least with the
           sway and the low one most, which is what makes the
           three read as layers rather than as one stain. */
        pool(c.accent, strong, 0.12f, 0.08f, r * 0.62f, 0.00f, 10.dp.toPx(), 26.dp.toPx())
        pool(c.accentSoft, strong * 0.9f, 0.94f, 0.40f, r * 0.5f, 0.37f, 18.dp.toPx(), 34.dp.toPx())
        pool(c.accent, strong * 0.66f, 0.30f, 0.98f, r * 0.66f, 0.71f, 26.dp.toPx(), 42.dp.toPx())

        /* A breath of the paper's opposite in the middle ground,
           so the field is not one hue: the warm face colour the
           panes already use for their top light. */
        pool(c.paneTop, min(0.5f, strong), 0.68f, 0.72f, r * 0.44f, 0.52f, 14.dp.toPx(), 22.dp.toPx())
    }
}
