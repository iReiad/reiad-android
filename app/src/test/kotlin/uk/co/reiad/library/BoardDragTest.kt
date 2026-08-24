package uk.co.reiad.library

import androidx.compose.foundation.layout.Box
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
    private var placedNow: List<Placed> = start

    private fun mount() {
        compose.setContent {
            var placed by remember { mutableStateOf(start) }
            var working by remember { mutableStateOf<List<Placed>?>(null) }
            placedNow = working ?: placed
            val state = rememberLazyGridState()
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
            Box(Modifier)
        }
        compose.waitForIdle()
    }

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
}
