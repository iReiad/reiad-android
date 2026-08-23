package uk.co.reiad.library.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import uk.co.reiad.library.core.Kind
import uk.co.reiad.library.core.Motion
import kotlin.math.floor
import kotlin.math.roundToInt

/* ============================================================
   One control, one gesture: hold and slide through the options.

   ---- what this replaces, and why it had to go ----

   Every switch on this site was a row of boxes, one `clickable`
   each, riding in a groove. It read correctly, it drew
   correctly, every snapshot of it was right, and on a real phone
   pressing an option did nothing: the stored values came back as
   the FIRST option of every row, on six of the seven reading
   preferences at once. The one that worked was the theme, which
   is the one setting stored under its own key rather than in the
   shared record, so it was the only one whose result was visible
   at all.

   `SettingsWorkTest` established the half that was NOT at fault:
   every row is wired to the field it names and leaves its
   neighbours alone. So the fault was between the finger and the
   handler, which is the one place a per-segment `clickable` can
   put it.

   **So there is no per-segment hit target any more.** The track
   is one gesture surface and the index is arithmetic on the
   pointer's x. A press cannot land on the wrong segment, because
   nothing is deciding which child was hit.

   ---- and it is what was asked for ----

   Press and slide, with the thumb under the finger the whole
   way, the way a physical switch moves. A tap still works and
   still commits the segment it landed on; the slide is the
   addition, not a replacement.

   ---- the one thing it must not break ----

   A vertical drag that STARTS on a control still scrolls the
   page. So this claims the gesture only after the finger has
   moved horizontally past touch slop: until then the pointer is
   unconsumed and an ancestor's scroll can take it. Consuming on
   the down event would make every switch a hole in the page that
   cannot be scrolled past, which on the settings sheet is six
   holes in a column.
   ============================================================ */

/** Where the thumb is, as a fraction of the track, while a finger
    is on it. Null when nothing is being held. */
private const val NO_HOLD = -1f

/**
 * A row of options with one thumb riding in a groove.
 *
 * `content` draws one option and is told whether it is the
 * chosen one, so the same control serves a settings row of words
 * and a navigation bar of icons.
 */
@Composable
fun <T> Segmented(
    options: List<T>,
    chosen: T?,
    onChoose: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = Gap.tap + Gap.s4,
    label: (T) -> String = { it.toString() },
    /** The thumb's ground. The accent for a setting; a quieter
        one for the navigation bar, where the accent belongs to
        the icon rather than to the whole tile. */
    thumbGround: androidx.compose.ui.graphics.Color? = null,
    /** How wide the thumb is against its segment. A bar's tiles
        want air between them; a setting's do not. */
    thumbInset: Dp = Gap.s2,
    content: @Composable (option: T, on: Boolean) -> Unit,
) {
    val c = LocalReiad.current
    val density = LocalDensity.current
    val n = options.size
    if (n == 0) return

    val found = options.indexOf(chosen)
    val at = if (found < 0) 0 else found
    /* Nothing is chosen where nothing matches, and the thumb is
       ABSENT rather than parked on the first option: a reader
       three screens into a piece is not on Home, and a bar that
       said so would be lying quietly. */
    val hasThumb = found >= 0
    var held by remember { mutableFloatStateOf(NO_HOLD) }
    var width by remember { mutableStateOf(0) }

    /* Where the thumb sits, in segments. While a finger is down
       this is the finger, exactly, with no animation between:
       an eased thumb lags behind the thing dragging it and reads
       as the control being slow rather than smooth. Let go and it
       settles on the chosen segment on the site's own curve. */
    val resting by animateFloatAsState(
        targetValue = at.toFloat(),
        animationSpec = tween(durationMillis = Motion.QUICK_MS),
        label = "thumb",
    )
    val thumbAt = if (held >= 0f) held else resting

    /** The pointer's x, in segments, clamped to the track. */
    fun segmentOf(x: Float): Float {
        if (width <= 0) return 0f
        val span = width.toFloat() / n
        return (x / span - 0.5f).coerceIn(0f, (n - 1).toFloat())
    }

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(Corner.pill))
            .material(Kind.GROOVE, c, Corner.pill, ground = c.paperSunk)
            .alpha(if (enabled) 1f else 0.45f)
            .pointerInput(n, enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    width = size.width
                    held = segmentOf(down.position.x)

                    /* Unconsumed until the finger has actually
                       moved sideways, so a vertical drag that
                       starts here still scrolls the sheet. */
                    val past = awaitHorizontalTouchSlopOrCancellation(down.id) { change, _ ->
                        change.consume()
                        held = segmentOf(change.position.x)
                    }
                    if (past != null) {
                        horizontalDrag(past.id) { change ->
                            change.consume()
                            held = segmentOf(change.position.x)
                        }
                    }

                    /* A tap commits where it landed and a slide
                       commits where it was let go, which is the
                       same sentence and so is the same line. */
                    val landed = held
                    held = NO_HOLD
                    if (landed >= 0f) options.getOrNull(landed.roundToInt())?.let(onChoose)
                }
            },
    ) {
        val span = maxWidth / n

        /* The thumb, under the labels, riding the whole track
           rather than being one of the boxes. */
        if (hasThumb || held >= 0f) {
            Box(
                Modifier
                    .offset(x = span * thumbAt)
                    .width(span)
                    .fillMaxHeight()
                    .padding(thumbInset)
                    .clip(RoundedCornerShape(Corner.pill))
                    .material(
                        kind = Kind.CONTROL,
                        colours = c,
                        corner = Corner.pill,
                        ground = thumbGround ?: c.accent,
                    ),
            )
        }

        /* The labels, in a row over it. They draw only: nothing
           here is a target, which is the whole point. */
        androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().fillMaxHeight()) {
            for ((i, option) in options.withIndex()) {
                /* Chosen for DRAWING follows the finger, so a
                   label lights as the thumb reaches it rather
                   than after the finger comes up. */
                val on = (hasThumb || held >= 0f) && floor(thumbAt + 0.5f).toInt() == i
                Box(
                    Modifier
                        .width(span)
                        .fillMaxHeight()
                        /* One node per option so a screen reader
                           still meets a list of choices rather
                           than one opaque strip, and so a switch
                           control can still reach each of them.
                           The gesture is the track's; this is the
                           accessible name and the action. */
                        .semantics(mergeDescendants = true) {
                            this.role = Role.RadioButton
                            this.selected = hasThumb && i == at
                            contentDescription = label(option)
                            onClick { onChoose(option); true }
                        },
                    contentAlignment = Alignment.Center,
                ) { content(option, on) }
            }
        }
    }
}
