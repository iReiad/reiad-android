package uk.co.reiad.library.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import uk.co.reiad.library.core.nav.Destination
import uk.co.reiad.library.core.nav.destinationOf

/* ============================================================
   Every address shape this site has, and where it goes.

   The app registers `https://reiad.co.uk` as an app link, so
   every one of these OPENS THE APP whether or not the app knows
   what to do with it. That is the whole argument for this test:
   an unhandled shape is not an error, it is a reader landing on
   the front page having asked for a lesson.

   The shapes are read off CLAUDE.md's own address rules, and the
   two that keep `.html` are here because they are the two that
   look like mistakes and are not.
   ============================================================ */
class AddressTest {

    private val site = SiteManifest(
        ladders = listOf(
            LadderSchool(key = "money"),
            LadderSchool(key = "deutsch"),
            LadderSchool(key = "quran"),
            LadderSchool(key = "english"),
        ),
        sections = listOf(
            ReadingSection(id = "insights"),
            ReadingSection(id = "cooking"),
            ReadingSection(id = "travel"),
        ),
    )

    private fun to(url: String) = destinationOf(url, site)

    @Test fun theFrontPage() {
        assertEquals(Destination.Home, to("https://reiad.co.uk/"))
        assertEquals(Destination.Home, to("https://reiad.co.uk"))
        assertEquals(Destination.Home, to("/"))
    }

    @Test fun aSchoolAndItsLessons() {
        assertEquals(Destination.School("money"), to("https://reiad.co.uk/money"))
        assertEquals(Destination.School("deutsch"), to("https://reiad.co.uk/deutsch/"))
        assertEquals(
            Destination.Lesson("deutsch", "stufe-1", "hallo.html"),
            to("https://reiad.co.uk/deutsch/stufe-1/hallo.html"),
        )
    }

    /** `.html` is part of the SLUG on both of the two shapes that
        keep it, so it is not stripped: a slug without it matches
        no row and is in nobody's library. */
    @Test fun theSuffixIsPartOfTheSlug() {
        val lesson = to("https://reiad.co.uk/money/basics-1/bo-account.html")
        assertEquals(Destination.Lesson("money", "basics-1", "bo-account.html"), lesson)
        val piece = to("https://reiad.co.uk/insights/why-index-funds.html")
        assertEquals(Destination.Piece("insights", "why-index-funds.html"), piece)
    }

    /** A stage's `base` decides where its pages go, so the money
        school's eighteen term pages live under `/money/terms/`
        while being lessons of `basics-1`. Three segments either
        way, and the endpoint resolves the middle one. */
    @Test fun theGlossaryIsLessonsAtAnotherAddress() {
        assertEquals(
            Destination.Lesson("money", "terms", "sanchayapatra.html"),
            to("https://reiad.co.uk/money/terms/sanchayapatra.html"),
        )
    }

    @Test fun theReadingHubs() {
        assertEquals(Destination.Hub("insights"), to("https://reiad.co.uk/insights"))
        assertEquals(Destination.Hub("cooking"), to("https://reiad.co.uk/cooking/"))
    }

    @Test fun theTools() {
        assertEquals(Destination.Tool("tools"), to("https://reiad.co.uk/tools"))
        assertEquals(Destination.Tool("stock"), to("https://reiad.co.uk/tools/stock"))
        assertEquals(Destination.Tool("live"), to("https://reiad.co.uk/tools/live"))
        assertEquals(Destination.Tool("routine"), to("https://reiad.co.uk/tools/routine"))
    }

    @Test fun theAccount() {
        assertEquals(Destination.Account, to("https://reiad.co.uk/account"))
    }

    /** Anything this app does not draw opens where it does exist,
        which is the one behaviour that must never quietly become
        "go to the front page". */
    @Test fun everythingElseOpensOnTheSite() {
        for (url in listOf(
            "https://reiad.co.uk/portfolio",
            "https://reiad.co.uk/skills/courses/",
            "https://reiad.co.uk/tools/diet/journal",
            "https://reiad.co.uk/admin",
            "https://reiad.co.uk/about",
        )) {
            assertTrue(to(url) is Destination.Elsewhere, "$url should open on the site")
        }
    }

    /**
     * Another host is never this site's, and the check is not
     * decoration: a lesson branch reached by an address on
     * somebody else's domain would be this app fetching their page
     * and drawing it as a lesson.
     */
    @Test fun anotherHostIsNotThisSite() {
        for (url in listOf(
            "https://reiad.co.uk.evil.example/money/basics-1/x.html",
            "https://evil.example/money/basics-1/x.html",
            "https://notreiad.co.uk/money",
            "javascript:alert(1)",
            "file:///etc/passwd",
        )) {
            assertTrue(to(url) is Destination.Elsewhere, "$url must not resolve as ours")
        }
    }

    @Test fun theWwwFormIsOurs() {
        assertEquals(Destination.School("money"), to("https://www.reiad.co.uk/money"))
    }

    /** A query and a fragment are not part of the address. The
        stock check shares its whole state in a query string, so a
        shared check has one and still has to open the tool. */
    @Test fun aQueryAndAFragmentAreDropped() {
        assertEquals(Destination.Tool("stock"), to("https://reiad.co.uk/tools/stock?p=630&eps=42"))
        assertEquals(
            Destination.Piece("insights", "why-index-funds.html"),
            to("https://reiad.co.uk/insights/why-index-funds.html#comments"),
        )
    }

    /** Before the first manifest fetch, a school is not yet a
        known key. It opens on the site rather than resolving
        wrongly, and the effect re-runs when the manifest lands. */
    @Test fun withNoManifestTheShapesThatNeedOneDefer() {
        assertTrue(destinationOf("https://reiad.co.uk/money", null) is Destination.Elsewhere)
        assertEquals(Destination.Home, destinationOf("https://reiad.co.uk/", null))
        assertEquals(Destination.Tool("stock"), destinationOf("https://reiad.co.uk/tools/stock", null))
    }
}
