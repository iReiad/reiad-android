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
   the frost differs from, what the gaps between the cards are
   instead of black, and what makes two sheets of glass at
   different depths read as different depths. It is NOT
   something a card lets through its face: that was tried, and
   a pool behind a card read as a lamp inside it.

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

    /* A THIRD of what it was, and the reason is the report:
       "the light in the middle of all cards etc is weirdly
       there". At 0.15 on a dark handset these pools are a lamp
       behind the page rather than the light in a room, and the
       cards were letting a tenth of that through their faces on
       top. The faces are solid now, so this only has to be the
       thing the frost differs from and the thing the gaps
       between cards are not black. */
    val strong = if (c.isDark) 0.055f else 0.05f

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

        /* All of them anchored OFF the page: three corners and
           one edge, big and soft, so the calmest part of the
           field is the middle, which is where the reading is.
           They used to sit at 0.30 and 0.68 of the height, which
           is exactly where a column of cards is, and a pool
           centred behind a card is the thing that was showing
           through it.

           Highest sits deepest: the top pool moves least with the
           sway and the low one most, which is what makes the
           three read as layers rather than as one stain. */
        pool(c.accent, strong, -0.05f, -0.08f, r * 0.70f, 0.00f, 10.dp.toPx(), 26.dp.toPx())
        pool(c.accentSoft, strong * 0.9f, 1.06f, 0.30f, r * 0.58f, 0.37f, 18.dp.toPx(), 34.dp.toPx())
        pool(c.accent, strong * 0.66f, 0.20f, 1.08f, r * 0.72f, 0.71f, 26.dp.toPx(), 42.dp.toPx())
    }
}
