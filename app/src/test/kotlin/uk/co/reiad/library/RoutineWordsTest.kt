package uk.co.reiad.library

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import uk.co.reiad.library.core.RoutineWords
import uk.co.reiad.library.core.SiteManifest
import uk.co.reiad.library.core.routine.GARDEN
import uk.co.reiad.library.core.routine.MOODS
import uk.co.reiad.library.core.routine.SEASONS
import uk.co.reiad.library.core.routine.gardenFrom
import uk.co.reiad.library.core.routine.moodsFrom
import uk.co.reiad.library.core.routine.seasonsFrom

/* ============================================================
   The site's copy wins, and the compiled one is the floor.

   The four moods, the six seasons and the five plants were a
   Kotlin copy of `shared/routine.ts`, which is the exact shape
   the DATA/CODE contract refuses: every one of them is an id, a
   name in each language and a colour, so a fifth mood should
   reach a phone on the next fetch rather than in a release.

   Two halves to prove, and they fail differently. If the served
   list is ignored, a change on the site never arrives and nothing
   looks wrong. If the compiled list is dropped, a first run with
   no network draws no mood row at all.
   ============================================================ */
class RoutineWordsTest {

    private val site: SiteManifest = fixture("site.json", SiteManifest.serializer())

    @Test fun theFixtureCarriesTheWords() {
        assertTrue(site.routine.moods.isNotEmpty(), "/api/site should send the moods")
        assertTrue(site.routine.seasons.isNotEmpty(), "and the seasons")
        assertTrue(site.routine.garden.isNotEmpty(), "and the garden")
        assertEquals("brd", site.routine.grown["birds"])
        assertEquals("pln", site.routine.grown["plants"])
    }

    @Test fun theSiteWins() {
        val sent = RoutineWords(
            moods = listOf(
                uk.co.reiad.library.core.MoodWord("new", "নতুন", "New", "#123456"),
            ),
        )
        val drawn = moodsFrom(sent)
        assertEquals(1, drawn.size, "a fifth mood should arrive without a release")
        assertEquals("নতুন", drawn.first().bn)
        assertEquals("#123456", drawn.first().colour)
    }

    /** Null is a first run with no network, and empty is a site
        that sent the key with nothing in it. Both fall back:
        an app that drew no moods because a field was missing
        would be a screen with a gap in it and no error. */
    @Test fun theCompiledCopyIsTheFloor() {
        assertEquals(MOODS, moodsFrom(null))
        assertEquals(MOODS, moodsFrom(RoutineWords()))
        assertEquals(SEASONS.size, seasonsFrom(null).size)
        assertEquals(GARDEN, gardenFrom(RoutineWords()))
    }

    /** And what the site actually sends today is what is
        compiled in, which is the assertion that catches the two
        drifting apart. */
    @Test fun theTwoAgreeToday() {
        assertEquals(MOODS.map { it.id }, site.routine.moods.map { it.id })
        assertEquals(MOODS.map { it.colour }, site.routine.moods.map { it.colour })
        assertEquals(SEASONS.map { it.id }, site.routine.seasons.map { it.id })
        assertEquals(GARDEN.map { it.at }, site.routine.garden.map { it.at })
    }
}
