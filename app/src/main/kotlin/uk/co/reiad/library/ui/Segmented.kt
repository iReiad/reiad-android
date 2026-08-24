package uk.co.reiad.library.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
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
    /** What the thumb is made of, when it is more than paint.
        The navigation bar passes the frosted glass here, cut to
        the thumb's own pill, so the pill is a LENS: the page
        underneath shows through it differently from the bar
        around it, which is most of what makes it read as a
        thing riding ON the bar rather than a painted state. */
    thumbBackdrop: Modifier = Modifier,
    content: @Composable (option: T, on: Boolean) -> Unit,
) {
    val c = LocalReiad.current
    val density = LocalDensity.current
    val n = options.size
    if (n == 0) return

    /* THE GESTURE READS THE PRESENT, NOT ITS FIRST COMPOSITION.

       `pointerInput(n, enabled)` restarts only when the count or
       the enablement changes, so its closure kept the FIRST
       composition's `options` and `onChoose`. For a settings row
       that is invisible: the handlers do not read captured
       state. For the navigation bar it was the whole fault a
       reader phrased as "pressing menu button opens that, but
       never closes": the More stop's handler closed over
       drawerOpen as it stood at first composition, false, so
       every press for ever said "open". The accessibility path
       rebuilds per composition, which is why a screen reader
       could close the menu and a thumb could not, and why the
       first test of this passed against the broken build.

       `rememberUpdatedState` is one answer and this is the same
       answer BoardDrag needed on the same day for the same
       disease: a long-lived lambda holding a composition-time
       value. */
    val liveOptions by rememberUpdatedState(options)
    val liveChoose by rememberUpdatedState(onChoose)

    val found = options.indexOf(chosen)
    val at = if (found < 0) 0 else found
    /* Nothing is chosen where nothing matches, and the thumb is
       ABSENT rather than parked on the first option: a reader
       three screens into a piece is not on Home, and a bar that
       said so would be lying quietly. */
    val hasThumb = found >= 0
    /* Read in the GESTURE and in the layer, never in
       composition: a finger moving across the track would
       otherwise recompose this whole row sixty times a second,
       and the labels do not change until a boundary is crossed. */
    val held = remember { mutableFloatStateOf(NO_HOLD) }
    var width by remember { mutableStateOf(0) }
    /* Which segment the thumb is OVER, as a whole number. This
       is what the labels light from, so they change once per
       boundary rather than once per frame. */
    var over by remember { mutableStateOf(-1) }

    /* Where the thumb sits, in segments. While a finger is down
       this is the finger, exactly, with no animation between:
       an eased thumb lags behind the thing dragging it and reads
       as the control being slow rather than smooth. Let go and
       it settles on the chosen segment on a SPRING, with a
       little overshoot: liquid settles, it does not park. */
    val resting = animateFloatAsState(
        targetValue = at.toFloat(),
        animationSpec = spring(
            dampingRatio = 0.72f,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "thumb",
    )

    /* The zoom under the finger. Held glass swells, which is the
       press being ANSWERED: the thumb grows a twentieth and
       settles back on the same spring when the finger lifts. */
    val swell = animateFloatAsState(
        targetValue = if (over >= 0 && held.floatValue >= 0f) 1.06f else 1f,
        animationSpec = spring(
            dampingRatio = 0.5f,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "swell",
    )

    /* A knock as the thumb crosses each boundary, which is the
       feel of a detent: the finger learns the segments without
       looking. */
    val knock = LocalHapticFeedback.current

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
                    /* One place the finger is turned into a
                       thumb position, and the same place the
                       detent and the lit label are decided, so
                       the three can never disagree. */
                    fun follow(x: Float) {
                        val to = segmentOf(x)
                        held.floatValue = to
                        val nowOver = to.roundToInt()
                        if (over != -1 && over != nowOver) {
                            knock.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        if (over != nowOver) over = nowOver
                    }

                    val down = awaitFirstDown(requireUnconsumed = false)
                    width = size.width
                    follow(down.position.x)

                    /* Unconsumed until the finger has actually
                       moved sideways, so a vertical drag that
                       starts here still scrolls the sheet. */
                    val past = awaitHorizontalTouchSlopOrCancellation(down.id) { change, _ ->
                        change.consume()
                        follow(change.position.x)
                    }
                    if (past != null) {
                        horizontalDrag(past.id) { change ->
                            change.consume()
                            follow(change.position.x)
                        }
                    }

                    /* A tap commits where it landed and a slide
                       commits where it was let go, which is the
                       same sentence and so is the same line. */
                    val landed = held.floatValue
                    held.floatValue = NO_HOLD
                    over = -1
                    if (landed >= 0f) {
                        liveOptions.getOrNull(landed.roundToInt())?.let { liveChoose(it) }
                    }
                }
            },
    ) {
        val span = maxWidth / n

        /* The thumb, under the labels, riding the whole track
           rather than being one of the boxes. */
        if (hasThumb || over >= 0) {
            val spanPx = with(density) { span.toPx() }
            Box(
                Modifier
                    .width(span)
                    .fillMaxHeight()
                    .padding(thumbInset)
                    /* MOVED IN THE LAYER, not in the layout.

                       This was `offset(x = span * thumbAt)`,
                       which is a layout modifier reading an
                       animation: every frame of the settle
                       re-measured the whole track and landed the
                       thumb on a whole pixel, so a spring that
                       should have flowed arrived as a series of
                       small steps. That is the jumpiness in the
                       report, and it was in the one control
                       every switch on the site goes through.

                       A layer reads its lambda at draw time, so
                       none of this recomposes anything, and it
                       moves in fractions of a pixel. */
                    .graphicsLayer {
                        val to = held.floatValue
                        val now = if (to >= 0f) to else resting.value
                        translationX = spanPx * now

                        /* AND IT STRETCHES ON THE WAY.

                           A thumb that travels rigidly is a tile
                           sliding; one that leans out towards
                           where it is going and gathers itself
                           up when it arrives is a thing with
                           liquid in it. The stretch is the
                           distance still to go, capped, so it is
                           widest mid flight and exactly nought
                           at both ends: no state, no second
                           animation to fall out of step with the
                           first. */
                        val toGo = (at.toFloat() - now)
                        val pull = kotlin.math.abs(toGo).coerceAtMost(1f)
                        scaleX = swell.value * (1f + pull * 0.16f)
                        scaleY = swell.value * (1f - pull * 0.05f)
                        /* Stretched from the TRAILING edge, so
                           the thumb reaches forward rather than
                           swelling in both directions. */
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(
                            if (toGo >= 0f) 0f else 1f,
                            0.5f,
                        )
                    }
                    .clip(RoundedCornerShape(Corner.pill))
                    .then(thumbBackdrop)
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
                val on = if (over >= 0) over == i else hasThumb && at == i
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

/** Which language a calculator opens in.

    The stock check and the calculators each drew this as two
    loose chips, which is two separate hit targets and the shape
    that stopped six settings working. One control, one gesture,
    and the same one the settings sheet uses.

    Narrow, because two words do not need the width of the screen
    and a switch that spans it reads as a pair of tabs. */
@Composable
fun LangSwitch(lang: String, onLang: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = LocalReiad.current
    val options = listOf("bn" to "বাংলা", "en" to "English")
    Box(modifier.width(210.dp)) {
        Segmented(
            options = options,
            chosen = options.firstOrNull { it.first == lang },
            onChoose = { onLang(it.first) },
            height = Gap.tap,
            label = { it.second },
        ) { option, on ->
            Text(
                option.second,
                style = MaterialTheme.typography.labelLarge,
                color = if (on) c.paper else c.inkSoft,
            )
        }
    }
}
