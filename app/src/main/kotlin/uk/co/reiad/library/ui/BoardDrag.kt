package uk.co.reiad.library.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.lazy.grid.LazyGridItemInfo
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/* ============================================================
   Pick a widget up and put it somewhere else.

   ---- why not the arrows ----

   There were arrows first, and the reasoning for them was that a
   drag cannot be reached by a switch or a screen reader. That is
   still true, so the arrows have not gone: they are what the
   semantics of each widget still offer, and a keyboard or a
   screen reader still moves one a step at a time. What was
   wrong was making a finger use them.

   ---- what makes this different from the usual ----

   It reorders LIVE, as the finger passes each neighbour, rather
   than computing a destination on release. That is not polish: a
   board where the cards do not move until you let go is a board
   where you are guessing, and the guess is worst exactly where
   the widgets are different sizes, which is here.

   ---- THE CARD IS WHERE THE FINGER IS ----

   And it is worked out that way rather than accumulated that
   way, which is the whole of why this file was rewritten.

   The first version added up drag deltas and then tried to undo
   its own arithmetic every time the board reordered underneath
   it: on each swap it zeroed the running total and hoped the
   card's new slot had landed where the finger was. It mostly had
   not, and the miss is what came back as "jumpy": a card that
   twitched away from the thumb at every neighbour it passed, and
   then TELEPORTED into place on release, because the drop set
   the offset to nought in one frame.

   Now nothing accumulates. The card's shift is DERIVED, every
   frame, from three things that are all true right now: where
   the finger is, where on the card it landed when it was picked
   up, and where the grid has just laid that card's slot. Reorder
   underneath it as much as you like: the shift recomputes and
   the card stays welded to the thumb. Nothing to compensate,
   nothing to drift.

   Release, and the SAME number is handed to a spring that runs
   it down to nought, so the card travels from under the finger
   into its slot the way a thing with weight would. It is the
   one place a settle beats a cut: everywhere else in this app a
   fast fade reads as decisive, and here a card that vanished
   from the thumb and appeared in a row is a card the reader has
   to find again.
   ============================================================ */

/** Where a carried widget is, and how far off the board it has
    been lifted. */
class BoardDrag internal constructor(
    private val state: LazyGridState,
    private val scope: CoroutineScope,
) {
    /** The key of the widget under the finger, or null. */
    var carrying by mutableStateOf<Any?>(null)
        private set

    /** The key of the widget still gliding into its slot after
        the finger has gone, or null. It is drawn lifted and
        above its neighbours for exactly as long as that takes,
        because a card mid flight that is already behind the
        board reads as a card that fell through it. */
    var settling by mutableStateOf<Any?>(null)
        private set

    /** Both of those: is this widget out of the board's plane. */
    fun holding(key: Any?): Boolean = key != null && (carrying == key || settling == key)

    /** Where the finger is INSIDE THE CELL the grid laid out,
        which is the one frame of reference that cannot chase its
        own tail: see the note above about deriving rather than
        accumulating. The handle is installed on the cell, which
        does not move, while the picture inside it does. */
    private var finger by mutableStateOf(Offset.Zero)

    /** Where in that cell the finger landed, so a widget grabbed
        by its corner is carried by its corner rather than
        jumping its own middle under the thumb. */
    private var grab = Offset.Zero

    /** What the settle is running down, once the finger is off.
        Null while the finger is still on: the shift is derived
        then, not stored. */
    private var glide by mutableStateOf<Offset?>(null)

    private val runDown = Animatable(Offset.Zero, Offset.VectorConverter)

    /** How far this widget is drawn from the slot the grid put it
        in. Read it inside a draw or layer block: it moves every
        frame while a finger is down.

        `finger - grab` and nothing else. If the board reorders
        underneath, the cell moves by some amount and the finger's
        position INSIDE that cell changes by exactly the opposite
        amount, so the card does not budge. That identity is the
        whole trick, and it is why there is no compensation
        arithmetic anywhere in this file. */
    fun shift(key: Any): Offset {
        glide?.let { if (settling == key) return it }
        if (carrying != key) return Offset.Zero
        return finger - grab
    }

    private fun itemFor(key: Any?): LazyGridItemInfo? =
        state.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }

    /** Picked up at `at`, which is where in the cell the finger
        went down. */
    fun pick(key: Any, at: Offset) {
        onPick()
        grab = at
        finger = at
        glide = null
        carrying = key
        settling = null
    }

    /** The finger has moved to `at`, again inside the cell. */
    fun moveTo(at: Offset) {
        val held = carrying ?: return
        finger = at

        val slot = itemFor(held) ?: return
        val shift = shift(held)
        /* The card's own middle decides what it is over, never
           the finger: a thumb on the bottom edge of a large
           widget would otherwise swap it a row early, and the
           bigger the widget the wronger it would be. */
        val centre = Offset(
            slot.offset.x + shift.x + slot.size.width / 2f,
            slot.offset.y + shift.y + slot.size.height / 2f,
        )
        val over = state.layoutInfo.visibleItemsInfo.firstOrNull { other ->
            other.key != held && other.key in keys && centre.inside(other)
        } ?: return

        val a = indexOfKey(held) ?: return
        val b = indexOfKey(over.key) ?: return
        if (a == b) return
        onMove(a, b)
    }

    fun drop() {
        val held = carrying ?: return
        val from = shift(held)
        /* Committed FIRST, and the glide is only a picture. If
           the animation is cut short by a recomposition, a
           rotation or the reader leaving the screen, the board
           is already saved in the order they let go in. */
        onDrop()
        carrying = null
        settling = held
        glide = from
        scope.launch {
            runDown.snapTo(from)
            runDown.animateTo(Offset.Zero, SETTLE) { glide = value }
            glide = null
            settling = null
        }
    }

    /** Which keys are widgets rather than the head and foot of
        the board, so a card cannot be swapped with the greeting.

        The board's own list, refreshed on every composition for
        the reason both callbacks below are. */
    internal var keys: Set<Any> = emptySet()

    /** Where a key sits in the board, which the caller owns.

        BOTH callbacks are `var`s reassigned on every
        recomposition, and the second one used to be a constructor
        capture. That was the snap-back a reader reported as
        "unable to move positions, if i change one that jumps
        right back": the remembered lambda closed over the FIRST
        composition's `placed`, so every move after the first was
        computed against the board as it stood before any move,
        and each write restored the old order under the finger.
        A capture that is refreshed for one callback and not its
        neighbour is worse than none, because the file looks like
        it knows about the trap. */
    internal var indexOfKey: (Any) -> Int? = { null }
    internal var onMove: (from: Int, to: Int) -> Unit = { _, _ -> }

    /** The SESSION, which is what made the reorder stick.

        Each pass over a neighbour used to write the whole board
        to the store and wait for the flow to come back round
        before the next pass could see it, so a finger crossing
        two neighbours quickly computed its second move against
        the board before the first. The caller now opens a local
        working copy on `onPick`, mutates it synchronously in
        `onMove`, and commits it ONCE on `onDrop`: one write per
        gesture, and nothing between the finger and the list. */
    internal var onPick: () -> Unit = {}
    internal var onDrop: () -> Unit = {}
}

/** Heavy enough to be seen, damped enough not to wobble.

    A card carries the weight of a thing being put down: it
    arrives, gives once, and stops. `0.72` is under the critical
    damping that would make it arrive dead, which is what the
    word liquid is asking for, and `380` keeps the whole journey
    inside a third of a second from anywhere on the screen. */
private val SETTLE: AnimationSpec<Offset> = spring(
    dampingRatio = 0.72f,
    stiffness = 380f,
    visibilityThreshold = Offset(0.5f, 0.5f),
)

private fun IntOffset.toOffset() = Offset(x.toFloat(), y.toFloat())

private fun Offset.inside(item: LazyGridItemInfo): Boolean =
    x >= item.offset.x && x <= item.offset.x + item.size.width &&
        y >= item.offset.y && y <= item.offset.y + item.size.height

@Composable
fun rememberBoardDrag(
    state: LazyGridState,
    keys: Set<Any>,
    indexOf: (Any) -> Int?,
    onMove: (from: Int, to: Int) -> Unit,
    onPick: () -> Unit = {},
    onDrop: () -> Unit = {},
): BoardDrag {
    val scope = rememberCoroutineScope()
    val drag = remember(state) { BoardDrag(state, scope) }
    drag.keys = keys
    drag.indexOfKey = indexOf
    drag.onMove = onMove
    drag.onPick = onPick
    drag.onDrop = onDrop
    return drag
}

/** Touch a loose widget and it is yours after a short rest.

    Only while the board is being arranged, which is what makes a
    SHORT hold safe: entering the mode already took a real long
    press, so a finger on a widget here almost always means to
    move it. It was a second full long-press, and that was
    reported as "cards rearranging are NOT working": everyone
    drags immediately in a jiggle mode, the way their phone's own
    home screen taught them, and a drag that ignores the first
    four hundred milliseconds reads as a drag that ignores them.

    160ms is the discriminator, not a politeness delay: a flick
    that means to SCROLL leaves within it and is not claimed
    (nothing is consumed before the claim, so the list takes it),
    while a finger that means to carry a widget naturally rests
    at least that long before moving. */
fun Modifier.dragHandle(drag: BoardDrag, key: Any): Modifier = this.pointerInput(key) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val slop = viewConfiguration.touchSlop
        var meant = true
        withTimeoutOrNull(160L) {
            while (true) {
                val event = awaitPointerEvent()
                val touch = event.changes.firstOrNull { it.id == down.id }
                if (touch == null || !touch.pressed) {
                    meant = false
                    break
                }
                if ((touch.position - down.position).getDistance() > slop) {
                    meant = false
                    break
                }
            }
        }
        if (!meant) return@awaitEachGesture

        drag.pick(key, down.position)
        while (true) {
            val event = awaitPointerEvent()
            val touch = event.changes.firstOrNull { it.id == down.id } ?: break
            if (!touch.pressed) break
            touch.consume()
            /* The POSITION, not the delta. Everything about
               where the card should be is worked out from where
               the finger is now, so a frame the pointer skipped
               and a reorder that moved the slot both come out
               right without anything being carried forward. */
            drag.moveTo(touch.position)
        }
        drag.drop()
    }
}
