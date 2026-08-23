package uk.co.reiad.library.ui

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

/* ============================================================
   Pick a widget up and put it somewhere else.

   ---- why not the arrows ----

   There were arrows first, and the reasoning for them was that a
   drag cannot be reached by a switch or a screen reader. That is
   still true, so the arrows have not gone: they are what the
   semantics of each strip still offer, and a keyboard or a
   screen reader still moves a widget one step at a time. What was
   wrong was making a finger use them.

   ---- what makes this different from the usual ----

   It reorders LIVE, as the finger passes each neighbour, rather
   than computing a destination on release. That is not polish: a
   board where the cards do not move until you let go is a board
   where you are guessing, and the guess is worst exactly where
   the widgets are tall and different heights, which is here.

   ---- and the heights are real ----

   A board holds a one-line card and a four-school panel, so
   nothing here may assume a row height. Every question about
   where the finger is goes to `LazyListState.layoutInfo`, which
   is what the list actually laid out. Dividing a drag distance by
   an assumed height is the bug this whole file exists to avoid,
   and it is invisible until a tall widget is on the board.
   ============================================================ */

/** What is being carried, and how far it has come. */
class BoardDrag internal constructor(
    private val state: LazyListState,
) {
    /** The key of the widget in the hand, or null. */
    var carrying by mutableStateOf<Any?>(null)
        private set

    /** How far it has been dragged from where it was picked up,
        in pixels, for the lift. */
    var offset by mutableFloatStateOf(0f)
        private set

    private var startedAt: LazyListItemInfo? = null

    private fun itemFor(key: Any?): LazyListItemInfo? =
        state.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }

    fun pick(key: Any) {
        onPick()
        carrying = key
        offset = 0f
        startedAt = itemFor(key)
    }

    fun drag(dy: Float) {
        val held = carrying ?: return
        offset += dy
        val from = startedAt ?: itemFor(held)?.also { startedAt = it } ?: return

        /* The middle of the card in the hand, in the list's own
           coordinates. Its own centre rather than the finger,
           because a finger that grabbed the bottom of a tall
           widget would otherwise swap it a card early. */
        val centre = from.offset + offset + from.size / 2f

        val over = state.layoutInfo.visibleItemsInfo.firstOrNull { other ->
            other.key != held &&
                centre.toInt() in other.offset..(other.offset + other.size)
        } ?: return

        val a = indexOfKey(held) ?: return
        val b = indexOfKey(over.key) ?: return
        if (a == b) return

        onMove(a, b)
        /* The list is about to relayout with this card in its new
           place, so the distance it has travelled restarts from
           there. Without this the offset keeps growing and the
           card runs away down the screen. */
        startedAt = null
        offset = 0f
    }

    fun drop() {
        carrying = null
        offset = 0f
        startedAt = null
        onDrop()
    }

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

@Composable
fun rememberBoardDrag(
    state: LazyListState,
    indexOf: (Any) -> Int?,
    onMove: (from: Int, to: Int) -> Unit,
    onPick: () -> Unit = {},
    onDrop: () -> Unit = {},
): BoardDrag {
    val drag = remember(state) { BoardDrag(state) }
    drag.indexOfKey = indexOf
    drag.onMove = onMove
    drag.onPick = onPick
    drag.onDrop = onDrop
    return drag
}

/** Long-press here to pick the widget up.

    On the STRIP rather than on the widget, and only while the
    board is being arranged. A long press on a card a reader is
    reading belongs to the card: a board that pounced on it would
    make selecting a headline impossible. */
fun Modifier.dragHandle(drag: BoardDrag, key: Any): Modifier = this.pointerInput(key) {
    detectDragGesturesAfterLongPress(
        onDragStart = { drag.pick(key) },
        onDrag = { change, amount ->
            change.consume()
            drag.drag(amount.y)
        },
        onDragEnd = { drag.drop() },
        onDragCancel = { drag.drop() },
    )
}
