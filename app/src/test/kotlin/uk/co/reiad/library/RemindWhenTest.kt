package uk.co.reiad.library

import uk.co.reiad.library.read.REMIND_TIMES
import uk.co.reiad.library.read.RemindWorker
import java.time.Duration
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/* ============================================================
   When the next reminder is.

   One piece of arithmetic and three ways to get it wrong, all of
   them silent: the reminder simply arrives at the wrong hour, or
   never, and nothing logs anything.

     - the time has already passed today, so "today at nine" is
       a negative delay and WorkManager runs it immediately;
     - the time is exactly now, which is the boundary and the one
       an `isBefore` gets wrong;
     - a stored value that is not a time at all, from a build
       that spelled it differently, which must be OFF rather than
       a default hour somebody did not choose.
   ============================================================ */
class RemindWhenTest {

    private val zone = ZoneId.of("Europe/London")
    private fun at(h: Int, m: Int) = ZonedDateTime.of(2026, 8, 23, h, m, 0, 0, zone)

    @Test fun `later today is later today`() {
        val until = RemindWorker.untilNext(LocalTime.of(21, 0), at(9, 0))
        assertEquals(Duration.ofHours(12), until)
    }

    @Test fun `already gone is tomorrow`() {
        val until = RemindWorker.untilNext(LocalTime.of(8, 0), at(9, 30))
        assertEquals(Duration.ofHours(22).plusMinutes(30), until)
    }

    /** The boundary. Exactly now is TOMORROW, not a zero delay:
        a reminder that fires the instant it is switched on is a
        notification the reader did not ask for, arriving while
        they are looking at the setting that caused it. */
    @Test fun `exactly now is tomorrow`() {
        assertEquals(Duration.ofDays(1), RemindWorker.untilNext(LocalTime.of(9, 0), at(9, 0)))
    }

    @Test fun `nothing unreadable becomes a time`() {
        assertNull(RemindWorker.parse(null))
        assertNull(RemindWorker.parse(""))
        assertNull(RemindWorker.parse("half nine"))
        assertNull(RemindWorker.parse("25:00"))
        assertEquals(LocalTime.of(21, 0), RemindWorker.parse("21:00"))
    }

    /** Every offer round-trips through the same parse the stored
        value goes through. An option whose own id this build
        cannot read is a chip that looks selected and is off. */
    @Test fun `every offered time survives being stored`() {
        assertTrue(REMIND_TIMES.size >= 3, "there should be more than one time on offer")
        assertNull(REMIND_TIMES.first().second, "the first offer has to be off")
        for ((label, time) in REMIND_TIMES) {
            assertTrue(label.isNotBlank(), "an offer with no label")
            if (time == null) continue
            assertEquals(time, RemindWorker.parse(time.toString()), "$label does not round-trip")
        }
    }
}
