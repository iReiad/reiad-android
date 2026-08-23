package uk.co.reiad.library.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/* ============================================================
   Where a reader stands, against the real ladder.

   Seeded from the money school's own fixture rather than from a
   list typed here, because the thing that goes wrong is not the
   division: it is which lessons count, and that is a property of
   the rows.
   ============================================================ */
private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; isLenient = true }

private fun <T> ladderFixture(name: String, of: kotlinx.serialization.KSerializer<T>): T =
    json.decodeFromString(
        of,
        checkNotNull(object {}.javaClass.getResourceAsStream("/fixtures/$name")) {
            "missing fixture $name"
        }.readBytes().decodeToString(),
    )

class StandingTest {

    private val ladder: LadderResponse = ladderFixture("money.json", LadderResponse.serializer())
    private val rungs = rungsOf(ladder.stages)

    @Test fun onlyWrittenLessonsAreInTheDenominator() {
        assertTrue(rungs.isNotEmpty(), "the fixture produced no rungs at all")
        val all = ladder.stages.sumOf { it.lessons.size }
        assertTrue(
            rungs.size <= all,
            "more rungs (${rungs.size}) than lessons ($all)",
        )
        val promised = ladder.stages.flatMap { it.lessons }.count { !it.isWritten || it.isSoon }
        assertEquals(
            all - promised,
            rungs.size,
            "a promised-and-unwritten lesson in the denominator is a bar that can " +
                "never fill, however much somebody reads",
        )
    }

    /** The money school's eighteen term pages are ticked under a
        bare slug and everything else under `<stage>/<lesson>`.
        That is `lessonId`'s rule and it is in real accounts, so
        the rungs have to carry the same shape. */
    @Test fun aRungCarriesTheIdATickIsFiledUnder() {
        val terms = rungs.filter { it.stage == "basics-1" }
        if (terms.isNotEmpty()) {
            assertTrue(
                terms.all { it.id == it.slug },
                "basics-1 is the exception: those pages are ticked under a bare slug",
            )
        }
        val others = rungs.filter { it.stage != "basics-1" }
        assertTrue(
            others.all { it.id == "${it.stage}/${it.slug}" },
            "every other lesson is <stage>/<lesson>",
        )
    }

    @Test fun nothingReadIsNotStarted() {
        val at = standingOf(rungs, emptySet())
        assertEquals(0, at.done)
        assertEquals(rungs.size, at.total)
        assertEquals(0.0, at.pct)
        assertTrue(!at.touched, "a school with no ticks and no bookmark has not been started")
        assertEquals(rungs.first().id, at.next?.id, "the first unread lesson is the next one")
    }

    @Test fun everythingReadHasNoNext() {
        val at = standingOf(rungs, rungs.map { it.id }.toSet())
        assertEquals(rungs.size, at.done)
        assertEquals(100.0, at.pct)
        assertNull(at.next, "a finished school should offer nothing to carry on with")
        assertTrue(at.touched)
    }

    /** The bookmark decides where to carry on FROM, and stops
        deciding once that lesson is ticked.

        A resume card that sends a reader back to something they
        have already finished is a card nobody presses twice, and
        that is the failure this branch exists for. */
    @Test fun theBookmarkPointsForwardAndNotBack() {
        val third = rungs[2]
        val read = setOf(rungs[0].id, rungs[1].id, third.id)
        val at = standingOf(rungs, read, last = third.id)
        assertEquals(
            rungs[3].id,
            at.next?.id,
            "the bookmark's own lesson is finished, so the next one is what follows it",
        )
    }

    /** And a bookmark ahead of the reading still walks forward,
        rather than sending them back to the first gap. */
    @Test fun aBookmarkAheadOfTheReadingCarriesOnFromThere() {
        val read = setOf(rungs[0].id)
        val at = standingOf(rungs, read, last = rungs[4].id)
        assertEquals(
            rungs[4].id,
            at.next?.id,
            "the bookmark is unread, so it IS the next thing",
        )
    }

    /** A bookmark on a lesson that no longer exists falls back to
        the first gap rather than to nothing. A lesson can be
        renamed or unpublished, and a reader whose bookmark points
        at it must not lose their way in. */
    @Test fun aStaleBookmarkFallsBack() {
        val at = standingOf(rungs, emptySet(), last = "a-stage/a-lesson-that-was-deleted")
        assertEquals(rungs.first().id, at.next?.id)
    }

    /** Checkpoints are counted and are NEVER in the bar.

        A checkpoint is a tick inside a lesson: five things a
        reader does over a fortnight. Counting them towards a
        ladder would make a school look finished because somebody
        worked through one chapter thoroughly. */
    @Test fun checkpointsAreASecondSentenceAndNotTheBar() {
        val one = rungs[0].id
        val two = rungs[1].id
        val at = standingOf(
            rungs,
            read = emptySet(),
            checks = setOf("$one#0", "$one#1", "$one#2", "$two#0"),
        )
        assertEquals(0, at.done, "a checkpoint is not a lesson")
        assertEquals(0.0, at.pct)
        assertEquals(4, at.checksDone)
        assertEquals(2, at.checksLessons, "four checkpoints across two lessons")
        assertTrue(at.touched, "somebody ticking checkpoints has started this school")
    }
}
