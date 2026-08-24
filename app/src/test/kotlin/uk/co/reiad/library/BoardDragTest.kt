package uk.co.reiad.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import uk.co.reiad.library.core.Placed
import uk.co.reiad.library.core.WidgetSize
import uk.co.reiad.library.core.moved
import uk.co.reiad.library.core.storedOf
import uk.co.reiad.library.ui.BoardDrag
import uk.co.reiad.library.ui.rememberBoardDrag
import kotlin.test.assertEquals

/* ============================================================
   A move sticks. Asserted, because it shipped not sticking.

   ---- the report ----

   "unable to move positions, if i change one that jumps right
   back to where it was." Two separate faults added up to that
   sentence, and each alone would have produced it:

   The remembered drag captured its `onMove` ONCE, at first
   composition, so every move after the first was computed
   against the board as it stood before any of them.
   `indexOfKey` was refreshed every recomposition three lines
   away, which is what made the gap invisible: the file clearly
   knew about stale captures.

   And each pass over a neighbour wrote the whole board to the
   store, so the next pass depended on a DataStore round trip
   having come back. Inside one gesture it usually had not.

   ---- what this drives, and what it does not ----

   The exact wiring Home hands `rememberBoardDrag`, through real
   recompositions: pick opens a working copy, each move mutates
   it and must SEE the one before, drop commits once. The
   callbacks are invoked as the gesture invokes them, without
   the pixel half: geometry never regressed, the wiring did, and
   a LazyColumn does not measure under Robolectric so a pixel
   drive here would assert nothing. The second move is the
   regression: under the old wiring it computed against the
   original order and un-did the first.
   ============================================================ */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xhdpi")
class BoardDragTest {

    @get:Rule val compose = createComposeRule()

    private val start = listOf(
        Placed("continue", WidgetSize.WIDE),
        Placed("progress", WidgetSize.WIDE),
        Placed("pulse", WidgetSize.TALL),
        Placed("market", WidgetSize.TALL),
    )

    /** What was committed to the "store", and how many times. */
    private val commits = mutableListOf<List<String>>()

    private lateinit var drag: BoardDrag
    private lateinit var gridState: androidx.compose.foundation.lazy.grid.LazyGridState
    private var placedNow: List<Placed> = start

    private fun mount() {
        compose.setContent {
            var placed by remember { mutableStateOf(start) }
            var working by remember { mutableStateOf<List<Placed>?>(null) }
            placedNow = working ?: placed
            val state = rememberLazyGridState()
            gridState = state
            drag = rememberBoardDrag(
                state = state,
                keys = (working ?: placed).map { it.id }.toSet(),
                indexOf = { key ->
                    (working ?: placed).indexOfFirst { it.id == key }.takeIf { it >= 0 }
                },
                onPick = { working = placed },
                onMove = { from, to -> working = moved(working ?: placed, from, to) },
                onDrop = {
                    working?.takeIf { it != placed }?.let {
                        commits.add(storedOf(it))
                        placed = it
                    }
                    working = null
                },
            )
            /* A REAL grid, laid out. Robolectric measures a
               lazy grid, so `layoutInfo` here holds the same
               offsets and sizes a phone's would, which is what
               lets the geometry half of this file drive the drag
               through actual pixels rather than through its
               callbacks. Two columns and one span each, so the
               four widgets make a 2x2 board. */
            LazyVerticalGrid(GridCells.Fixed(2), state = state) {
                items(placedNow, key = { it.id }) { p ->
                    Box(Modifier.fillMaxWidth().height(TILE.dp))
                }
            }
        }
        compose.waitForIdle()
    }

    /** Where a cell was laid out, in the grid's own frame. */
    private fun slotOf(key: String) =
        gridState.layoutInfo.visibleItemsInfo.first { it.key == key }

    /** Two moves in one gesture, a recomposition between them the
        way a frame delivers one. */
    @Test fun theSecondMoveSeesTheFirst() {
        mount()
        drag.onPick()
        compose.waitForIdle()
        drag.onMove(0, 1)
        compose.waitForIdle()
        /* THE REGRESSION LINE. Stale wiring computed this against
           the original order, and the card jumped back. */
        drag.onMove(1, 2)
        compose.waitForIdle()
        drag.onDrop()
        compose.waitForIdle()

        assertEquals(
            listOf("progress", "pulse", "continue", "market"),
            placedNow.map { it.id },
            "the card should have ended two places down; jumping back is the reported bug",
        )
        assertEquals(1, commits.size, "one gesture is ONE write, on the drop")
        assertEquals(
            listOf("progress:wide", "pulse:tall", "continue:wide", "market:tall"),
            commits.single(),
        )
    }

    /** Two moves with NO recomposition between them: pointer
        events can land twice in one frame, and both must count.
        This is what reading the state at CALL time buys. */
    @Test fun twoMovesInOneFrameBothLand() {
        mount()
        drag.onPick()
        drag.onMove(0, 1)
        drag.onMove(1, 2)
        drag.onDrop()
        compose.waitForIdle()
        assertEquals(listOf("progress", "pulse", "continue", "market"), placedNow.map { it.id })
    }

    @Test fun aDropWithNoMoveWritesNothing() {
        mount()
        drag.onPick()
        drag.onDrop()
        compose.waitForIdle()
        assertEquals(0, commits.size)
    }

    /* ============================================================
       And the half that IS pixels.

       Everything above drives the callbacks the way a gesture
       drives them, which is what caught the stale-capture bug.
       What it cannot see is the arithmetic BETWEEN the finger and
       those callbacks, and that arithmetic is what came back as
       "jumpy": a card that twitched away from the thumb at every
       neighbour it passed.

       These drive `pick`/`moveTo` with real positions over a real
       laid-out grid, so the two questions that matter are
       answerable: is the card exactly where the finger is, and
       does it swap when its own middle crosses a neighbour.
       ============================================================ */

    /** THE IDENTITY THE WHOLE FILE RESTS ON.

        A carried card is drawn at `finger - grab`, and nothing
        accumulates. Move the finger 120 down and 30 across and
        the card is 120 down and 30 across: not approximately, not
        after a swap has been compensated for, exactly. */
    @Test fun theCardIsWhereTheFingerIs() {
        mount()
        drag.pick("continue", Offset(40f, 30f))
        assertEquals(Offset.Zero, drag.shift("continue"))

        drag.moveTo(Offset(70f, 150f))
        assertEquals(Offset(30f, 120f), drag.shift("continue"))
    }

    /** Nothing else on the board moves with it. */
    @Test fun theOthersStayWhereTheyAre() {
        mount()
        drag.pick("continue", Offset(40f, 30f))
        drag.moveTo(Offset(40f, 200f))
        assertEquals(Offset.Zero, drag.shift("progress"))
        assertEquals(Offset.Zero, drag.shift("pulse"))
    }

    /** Carried far enough that its own middle sits over the
        widget below, the two exchange places, live, under the
        finger. */
    @Test fun aCardSwapsWhenItsMiddleReachesTheNext() {
        mount()
        val from = slotOf("continue")
        val to = slotOf("pulse")
        drag.pick("continue", Offset(20f, 20f))

        /* Far enough that the CARD's centre lands inside the
           third cell, which is what decides a swap: the finger
           itself is deliberately not the test. */
        val travel = (to.offset.y - from.offset.y) + to.size.height / 2f
        drag.moveTo(Offset(20f, 20f + travel))
        compose.waitForIdle()

        /* A MOVE rather than an exchange: the card takes the
           third place and the two it passed close up behind it,
           which is `moved()`'s own semantics and what a home
           screen does. An exchange would leave a widget sitting
           where the reader dragged FROM, which is the thing
           nobody expects. */
        assertEquals(
            listOf("progress", "pulse", "continue", "market"),
            placedNow.map { it.id },
            "the carried card should have taken the third place",
        )
    }

    /** And a nudge is not a swap: half a tile is a hesitation. */
    @Test fun aSmallNudgeSwapsNothing() {
        mount()
        drag.pick("continue", Offset(20f, 20f))
        drag.moveTo(Offset(20f, 40f))
        compose.waitForIdle()
        assertEquals(start.map { it.id }, placedNow.map { it.id })
    }

    /** The drop hands the card to the settle and commits the
        board in the order the finger let go in, rather than
        waiting for the animation to finish: an interrupted glide
        must not be able to lose an arrangement. */
    @Test fun theBoardIsCommittedOnReleaseNotOnArrival() {
        mount()
        val from = slotOf("continue")
        val to = slotOf("pulse")
        drag.pick("continue", Offset(20f, 20f))
        drag.moveTo(Offset(20f, 20f + (to.offset.y - from.offset.y) + to.size.height / 2f))
        drag.drop()

        assertEquals(1, commits.size, "the drop writes once, immediately")
        assertEquals(
            listOf("progress:wide", "pulse:tall", "continue:wide", "market:tall"),
            commits.single(),
        )
    }
}

/** Tall enough that a 2x2 board of them is taller than the
    viewport is wide, so the geometry is not degenerate. */
private const val TILE = 160
