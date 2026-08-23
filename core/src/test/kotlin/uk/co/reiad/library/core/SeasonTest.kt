package uk.co.reiad.library.core

import kotlin.test.Test
import kotlin.test.assertEquals
import uk.co.reiad.library.core.routine.SEASONS
import uk.co.reiad.library.core.routine.Season
import uk.co.reiad.library.core.routine.seasonOf

/* ============================================================
   Six seasons, and the one that wraps the year.

   The boundaries used to be a `when` with six literal pairs in
   it, which agreed with `shared/routine.ts` because somebody had
   copied them. They are read off each row's `from` now, so a
   seventh season, or a boundary moved by a week, arrives with no
   app release.

   Which makes the WRAP the thing worth a test. Winter starts on
   the 15th of December and runs through January to the 15th of
   February, so a date in January is before every boundary in its
   own year and belongs to the one that began in the year before.
   A plain "first boundary at or before this date" returns nothing
   for January, and a version that fell back to the FIRST season
   in the list would put January in গ্রীষ্ম, at 40 degrees.
   ============================================================ */
class SeasonTest {

    private fun on(iso: String) = seasonOf(iso, SEASONS).id

    @Test fun eachSeasonStartsWhereItSays() {
        assertEquals("grishmo", on("2026-04-15"))
        assertEquals("barsha", on("2026-06-15"))
        assertEquals("sharat", on("2026-08-15"))
        assertEquals("hemanta", on("2026-10-15"))
        assertEquals("sheet", on("2026-12-15"))
        assertEquals("bosonto", on("2026-02-15"))
    }

    @Test fun theDayBeforeIsStillThePreviousOne() {
        assertEquals("bosonto", on("2026-04-14"))
        assertEquals("grishmo", on("2026-06-14"))
        assertEquals("hemanta", on("2026-12-14"))
    }

    /** The whole of January, and the first half of February. */
    @Test fun winterWrapsTheYear() {
        for (iso in listOf("2026-01-01", "2026-01-17", "2026-01-31", "2026-02-14")) {
            assertEquals("sheet", on(iso), "$iso should be winter")
        }
    }

    /** And it wraps against a list that came DOWN, in whatever
        order the site sent it: the answer is the boundaries, not
        the position in the array. */
    @Test fun theOrderOfTheListDoesNotDecide() {
        val shuffled = SEASONS.reversed()
        assertEquals("sheet", seasonOf("2026-01-17", shuffled).id)
        assertEquals("barsha", seasonOf("2026-07-01", shuffled).id)
    }

    /** A seventh, added on the site and never compiled here. */
    @Test fun aSeventhSeasonJustWorks() {
        val seven = SEASONS + Season("monsoon2", "নতুন", "New", "#000000", 7 to 1)
        assertEquals("monsoon2", seasonOf("2026-07-05", seven).id)
        assertEquals("barsha", seasonOf("2026-06-30", seven).id)
    }
}
