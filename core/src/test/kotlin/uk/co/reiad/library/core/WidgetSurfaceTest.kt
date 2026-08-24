package uk.co.reiad.library.core

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/* ============================================================
   The board's two halves, held to each other.

   The catalogue is the site's and the renderers are this app's,
   which is the contract that lets a widget be renamed without an
   app release. What it cannot do is stop the two drifting into
   nonsense, and there are two ways it can:

     - this app draws a kind the site has never heard of, so the
       picker never offers it and the only way onto a board is a
       string somebody typed;
     - the FLOOR names a kind this app cannot draw, so a phone
       that has never fetched anything opens on a board with a
       hole in it.

   Both are silent. Nothing crashes, nothing logs, and the screen
   renders perfectly with one fewer thing on it.

   `DRAWABLE` lives in `app/`, which this module cannot see, so
   what is asserted here is the FLOOR against the fixture and the
   fixture against itself. `BoardReachTest` in `app/` is the
   other half.
   ============================================================ */
class WidgetSurfaceTest {

    private val site: SiteManifest = Json { ignoreUnknownKeys = true }.decodeFromString(
        SiteManifest.serializer(),
        checkNotNull(javaClass.getResourceAsStream("/fixtures/site.json")) {
            "fixtures/site.json is missing. See README, 'Refreshing the fixtures'."
        }.readBytes().decodeToString(),
    )

    /** Every entry of the floor is a real placing.

        This one holds whatever the site is serving: the floor is
        what a phone with no network draws, so it has to parse
        against itself before anything else is worth asking. */
    @Test fun `the floor is a board this app can read`() {
        val ids = BOARD_FLOOR.map { it.substringBefore(":") }.toSet()
        val placed = layoutOf(BOARD_FLOOR, ids)
        assertEquals(
            BOARD_FLOOR.size, placed.size,
            "the floor has an entry that does not parse, so a first run with no " +
                "network opens on a board with a hole in it",
        )
    }

    /** And it matches what the site would have sent.

        Vacuous until a deployment carries the catalogue, and it
        says so rather than passing quietly: a test that asserts
        nothing and reports success is the silent skip this
        repository has already been caught by. */
    @Test fun `the floor is the site's own default`() {
        val home = site.widgets.home
        if (home.isEmpty()) {
            /* Not a failure. The fixture is a real answer from the
               live endpoint and the catalogue ships with the next
               deploy; what would be wrong is passing without
               saying which of the two happened. */
            println(
                "WidgetSurfaceTest: the site fixture carries no widgets yet, so the " +
                    "floor is unchecked against it. Refresh core/src/test/resources/" +
                    "fixtures/site.json after the catalogue deploys.",
            )
            return
        }
        assertEquals(
            home, BOARD_FLOOR,
            "BOARD_FLOOR and HOME_DEFAULT in shared/widgets.ts have parted company. " +
                "The floor is only ever seen before the first fetch, so a phone would " +
                "open on one board and change to another a second later.",
        )
    }

    @Test fun `every kind the site sends is usable`() {
        val kinds = site.widgets.kinds
        if (kinds.isEmpty()) {
            println("WidgetSurfaceTest: no catalogue in the fixture yet.")
            return
        }
        for (kind in kinds) {
            assertTrue(kind.id.isNotBlank(), "a widget with no id cannot be placed")
            assertTrue(
                kind.bn.isNotBlank() && kind.en.isNotBlank(),
                "${kind.id} is said in one language, and the picker shows the other",
            )
            assertTrue(
                kind.icon.isNotBlank(),
                "${kind.id} names no icon, so the picker draws an empty box beside it",
            )
            assertTrue(
                kind.offers(kind.added()),
                "${kind.id} is added at a size it does not offer",
            )
        }
        assertEquals(
            kinds.size, kinds.map { it.id }.toSet().size,
            "two widgets share an id, and a board holds one of each",
        )
    }
}
