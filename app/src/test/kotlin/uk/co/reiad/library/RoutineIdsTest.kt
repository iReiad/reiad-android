package uk.co.reiad.library

import kotlin.test.Test
import kotlin.test.assertTrue
import uk.co.reiad.library.core.routine.RoutineShape

/* ============================================================
   The two task ids the birds and the garden hang on.

   `flock()` and `garden()` count how many times ONE task was
   marked, so the id is the whole of the lookup: a wrong one is
   not an error, it is a zero. An empty sky and a bare garden on
   a page that renders perfectly, for a reader who has fed the
   birds four hundred times.

   The first draft guessed `birds` and `plants` from the English
   names. The ids are `brd` and `pln`, which is the sort of thing
   only the data can tell you.
   ============================================================ */
class RoutineIdsTest {
    private val shape: RoutineShape =
        fixtureField("routine.json", "shape", RoutineShape.serializer())

    @Test fun theTwoIdsAreReal() {
        val ids = shape.tasks.map { it.id }.toSet()
        assertTrue(ids.size > 5, "the fixture should hold a real routine")
        for (id in listOf("brd", "pln")) {
            assertTrue(id in ids, "$id is not a task in a real routine; ids are $ids")
        }
    }
}
