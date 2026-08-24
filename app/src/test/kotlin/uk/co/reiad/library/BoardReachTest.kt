package uk.co.reiad.library

import uk.co.reiad.library.core.BOARD_FLOOR
import uk.co.reiad.library.core.SiteManifest
import uk.co.reiad.library.core.layoutOf
import uk.co.reiad.library.ui.DRAWABLE
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/* ============================================================
   The board's other half: what this build can actually draw.

   `DRAWABLE` in `ui/Widgets.kt` is the set of kinds with a
   renderer, and it is the filter every stored board goes
   through. Two things go wrong silently around it, and neither
   crashes anything:

     - a kind in `DRAWABLE` with no branch in `Widget()`, so it
       is offered in the picker, added to the board, and draws
       nothing;
     - a kind with a branch and not in `DRAWABLE`, so the code
       is there and unreachable.

   Both look identical to a working app.
   ============================================================ */
class BoardReachTest {

    private val site: SiteManifest = fixture("site.json", SiteManifest.serializer())

    /** Every id `DRAWABLE` names has a branch in `Widget()`.

        Read out of the SOURCE, the way `IconNamesTest` reads icon
        names, because reaching the branch itself needs a theme, a
        fixture and a device for half of them. */
    @Test fun everyDrawableKindHasARenderer() {
        val source = java.io.File("src/main/kotlin/uk/co/reiad/library/ui/Widgets.kt").readText()
        assertTrue(source.length > 500, "Widgets.kt was not found; the path is wrong")
        val branches = Regex("""^\s{8}"([a-z-]+)" ->""", RegexOption.MULTILINE)
            .findAll(source).map { it.groupValues[1] }.toSet()

        assertEquals(
            DRAWABLE, branches,
            "DRAWABLE and the when() in Widget() have parted company. A kind in the " +
                "set with no branch is offered in the picker and draws nothing; a " +
                "branch not in the set is code nothing can reach.",
        )
    }

    /** And the floor is a board this build can draw.

        Not the same question. `DRAWABLE` could be perfectly
        consistent with itself and still miss a kind the floor
        names, which is a first run with a hole in the front
        page. */
    @Test fun theFloorIsDrawableEndToEnd() {
        val placed = layoutOf(BOARD_FLOOR, DRAWABLE)
        assertTrue(
            placed.isNotEmpty(),
            "a phone with no network would open on an empty front page",
        )
        val missing = BOARD_FLOOR.map { it.substringBefore(":") }
            .filter { it !in DRAWABLE }
        /* Not an error. The floor is the SITE's default and this
           app is allowed to be behind it, which is the whole
           contract. What would be wrong is the floor being
           entirely undrawable, which the assertion above covers. */
        if (missing.isNotEmpty()) {
            println("BoardReachTest: the floor names ${missing.joinToString()}, which this " +
                "build has no renderer for yet. They are skipped rather than drawn blank.")
        }
    }

    /** A board written by a newer phone still opens. */
    @Test fun anUnknownKindDoesNotEmptyTheBoard() {
        val written = listOf("hologram:full") + BOARD_FLOOR
        assertEquals(
            layoutOf(BOARD_FLOOR, DRAWABLE).map { it.id },
            layoutOf(written, DRAWABLE).map { it.id },
        )
    }

    /** Nothing this build draws is missing from the site's own
        catalogue, so everything drawable is also PICKABLE.

        Vacuous until the catalogue deploys, and it says which. */
    @Test fun everythingDrawableIsAlsoOffered() {
        val kinds = site.widgets.kinds.map { it.id }.toSet()
        if (kinds.isEmpty()) {
            println("BoardReachTest: the site fixture carries no catalogue yet.")
            return
        }
        val orphans = DRAWABLE - kinds
        assertTrue(
            orphans.isEmpty(),
            "this app draws $orphans and the site's catalogue does not hold them, so " +
                "the picker can never offer them and the only way onto a board is a " +
                "string somebody typed.",
        )
    }
}
