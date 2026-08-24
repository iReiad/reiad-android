package uk.co.reiad.library

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/* ============================================================
   EVERY SITE PATH THIS APP WRITES BY HAND, LISTED ON PURPOSE.

   ---- the bug, twice ----

   The admin's course card opened `/courses`. There is no such
   page: the route is `/skills/courses`, and what the reader got
   was a browser tab with a not-found page in it, which they
   reported, correctly, as "that button doesn't open anything".

   Looking for a second one found a second one the same afternoon:
   the diet screen's way out to the site pointed at
   `/tools/diet/log`, also a 404, also silent.

   Neither is the kind of fault a compiler, a snapshot or an
   arithmetic test can see. A path is a string; a wrong one is a
   perfectly good string; the Custom Tab opens either way and the
   404 page is the SITE's, so nothing in this app ever learns it
   was wrong. The reader is the error handler, which is the worst
   possible place to put one.

   ---- what this does about it ----

   Every hand-written site path in the app has to appear in the
   table below, and every entry in the table was opened against
   the live site by a person, on the date at the top. A new path
   fails this test until somebody has checked it and written it
   down, which is the whole of the intended cost: thirty seconds
   with curl, once, instead of a dead button shipping.

   ---- and the rule underneath it ----

   The table is deliberately SHORT and should stay short. Every
   destination the site's own menu carries arrives in
   `/api/site` with its href attached, and reading it from there
   is how a link stays right when the site moves it. These seven
   are the ones with no manifest entry to read: three tool pages
   the app only half implements, the reading hub, and the admin's
   shelf, which is admin-only and so is in nobody's menu.
   ============================================================ */
class SitePathsTest {

    /** Checked against reiad.co.uk on 2026-08-24, each one
        answering 200. Anything not on this list is either a
        typo or a page somebody has to open once. */
    private val checked = setOf(
        /* The routine tool, and the single day inside it. */
        "/tools/routine",
        "/tools/routine/day",
        /* The live portfolio. */
        "/tools/live",
        /* The diet tool's own front page, which is today's log
           and the way to its other thirteen pages. */
        "/tools/diet",
        /* The site's food pages: search, the barcode scanner
           and the two public databases, none of which this app
           has. `/api/foods` answers 404, so this is where a
           reader who cannot find a dish actually goes. */
        "/tools/diet/foods",
        /* The reading hub. */
        "/insights",
        /* The admin's course shelf. Admin-only, so it is in no
           manifest menu and has to be written by hand.

           It is no longer a browser hand-off: the app draws the
           section itself, because a Custom Tab could never carry
           the session that gates it. The path stays on this list
           anyway, and it has to: `core/Courses.kt` builds all
           five of the section's addresses out of it, and one of
           them is written into `courses-last`, which is a
           bookmark the SITE reads. A wrong prefix here would put
           a dead address into somebody's account. */
        "/skills/courses",
    )

    /**
     * Both modules, and `core` is not an afterthought.
     *
     * It walked `app/` alone, which was true of the app on the day
     * it was written and stopped being true the moment an address
     * moved: `/skills/courses` now lives in `core/Courses.kt`,
     * where the section's five addresses are built from it, and
     * that is the RIGHT place for it. Had this kept looking only
     * at `app/`, the move would have quietly taken the one entry
     * in the table below out of scope, and the next hand-written
     * path in `core` would have gone unchecked for as long as
     * nobody noticed.
     *
     * `core` is a sibling directory rather than a source set of
     * this module, hence the `..`: this test runs with the app
     * module as its working directory.
     */
    private fun sources(): List<File> =
        listOf(File("src/main/kotlin"), File("../core/src/main/kotlin"))
            .filter { it.isDirectory }
            .flatMap { it.walkTopDown().filter { file -> file.extension == "kt" } }

    /** A string that looks like a path on this site: it starts
        with one slash and holds only the characters a route
        here uses. Query strings and anchors are cut off, so
        `/tools/diet?day=x` is checked as `/tools/diet`. */
    private val looksLikeAPath = Regex("""^/[a-z][a-z0-9/-]*$""")

    /** The ones that are NOT site routes and never were: an API
        endpoint is a different contract, checked by the code
        that calls it, and a store key that begins with a slash
        is not an address at all. */
    private fun ours(path: String): Boolean =
        !path.startsWith("/api/") && !path.startsWith("/_")

    @Test fun everyHandWrittenPathHasBeenOpened() {
        val found = mutableMapOf<String, MutableSet<String>>()
        for (file in sources()) {
            for (match in Regex(""""(/[a-z][a-z0-9/-]*)"""").findAll(file.readText())) {
                val path = match.groupValues[1]
                if (!looksLikeAPath.matches(path) || !ours(path)) continue
                found.getOrPut(path) { mutableSetOf() }.add(file.name)
            }
        }

        val strangers = found.keys - checked
        assertTrue(
            strangers.isEmpty(),
            "these site paths are written into the app and nobody has said they exist: " +
                strangers.sorted().joinToString(", ") { "$it (${found[it]?.joinToString()})" } +
                ". Open each in a browser, and if it answers, add it to `checked` with the " +
                "date. A path that 404s is a button that does nothing, and the reader is " +
                "the only thing that finds out.",
        )
    }

    /** And the table does not rot: an entry nothing uses any
        more is an entry nobody will re-check, and it makes the
        list look longer than the app's actual surface. */
    @Test fun theTableHoldsNothingTheAppNoLongerUses() {
        val text = sources().joinToString("\n") { it.readText() }
        val stale = checked.filterNot { """"$it"""" in text }
        assertTrue(
            stale.isEmpty(),
            "checked paths nothing writes any more: " + stale.joinToString(", ") +
                ". Take them out; a list that describes an older app is worse than none.",
        )
    }
}
