package uk.co.reiad.library.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import uk.co.reiad.library.core.diet.FoodLibrary
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/* ============================================================
   Everything /api/foods sends, held to what this app does with it.

   `check-app-surface.ts` asks the question from the site's end: is
   there a table `shared/foods.ts` holds that nothing sends? This
   is the other end, and it is the one that has actually gone
   wrong here before: a field arrives, decodes, and is never drawn,
   so the screen renders perfectly with something missing that
   nobody can see is missing.

   ---- so a field is either USED or NAMED ----

   `NOT_CARRIED` is the second, keyed by the field with the reason
   it stays on the server, and it fails on a STALE exemption for
   the reason every list of exceptions in this repository is
   checked both ways: a name that is gone is a reason nobody will
   read again.

   What this cannot see is a Compose file, which lives in `app/`.
   So what it asserts is that the CORE carries the field; whether
   a screen draws it is `ScreensLookTest`'s two snapshots, which
   are built from this same payload for exactly that reason.
   ============================================================ */
class FoodSurfaceTest {

    private val root: JsonObject = Json.parseToJsonElement(
        checkNotNull(javaClass.getResourceAsStream("/fixtures/foods.json")) {
            "fixtures/foods.json is missing. See README, 'Refreshing the fixtures'."
        }.readBytes().decodeToString(),
    ) as JsonObject

    private val library: FoodLibrary = checkNotNull(FoodLibrary.from(root))

    /** A field on a row that the endpoint sends and this app does
        not carry, with the reason it stays behind. */
    private val notCarried = mapOf(
        "raw" to
            "whether the figures are for the food dry or cooked. `foods.ts`'s own " +
                "rule is that a row in scope states its state IN THE NAME in both " +
                "languages, which is what a reader is shown, and no arithmetic here " +
                "branches on the flag.",
        "price" to
            "section 17's cost per gram of protein, which is a page this app does " +
                "not have. A figure with a date and a currency on it, and drawing it " +
                "without both would be quoting a shop.",
        "currency" to "half of `price`, and it goes when that page arrives.",
        "pricedOn" to "the third part of `price`: all three, or none of them.",
    )

    /* ---------- every row field is used or named ---------- */

    @Test fun `no field of a row is silently dropped`() {
        val rows = (root["foods"] as JsonArray).map { it as JsonObject }
        val fields = rows.flatMap { it.keys }.toSet()

        val carried = setOf("id", "en", "bn", "place", "qty", "unit", "grams", "kcal",
            "source", "tags")

        val orphans = fields.filter { it !in carried && it !in notCarried }
            /* Anything numeric is a nutrient and reaches `per`
               whatever it is called, which is the whole contract:
               a nutrient added next year needs no release. */
            .filter { field -> rows.none { it[field]?.toString()?.toDoubleOrNull() != null } }
        assertTrue(
            orphans.isEmpty(),
            "the endpoint sends $orphans and nothing here carries it. Use it, or put " +
                "it in NOT_CARRIED with the reason it stays on the server.",
        )
    }

    /** And an exemption that has gone stale fails too. A reason
        nobody will read again is worse than no reason. */
    @Test fun `every exemption still names a field the endpoint sends`() {
        val fields = (root["foods"] as JsonArray)
            .flatMap { (it as JsonObject).keys }.toSet()
        for ((field, why) in notCarried) {
            assertTrue(
                field in fields,
                "NOT_CARRIED still names `$field` ($why) and the endpoint no longer " +
                    "sends it, so the reason is about nothing.",
            )
        }
    }

    /* ---------- every top-level key lands somewhere ---------- */

    @Test fun `every top-level key of the payload is carried`() {
        val landed = mapOf(
            "place" to library.place.isNotEmpty(),
            "foods" to library.foods.isNotEmpty(),
            "nutrients" to library.nutrients.isNotEmpty(),
            "groups" to library.groups.isNotEmpty(),
            "units" to library.units.isNotEmpty(),
            "coverage" to library.coverage.isNotEmpty(),
            "macros" to library.macros.isNotEmpty(),
        )
        for (key in root.keys) {
            assertTrue(
                landed[key] == true,
                "the endpoint sends `$key` and it reaches nothing on this side",
            )
        }
    }

    /* ---------- and every nutrient field is drawable ---------- */

    /** Every sentence on a nutrient arrives non-empty in both
        languages. A nutrient whose `whyBn` came back blank draws a
        gap on the one screen a Bangla reader most needs it, and
        the panel would look finished. */
    @Test fun `every nutrient says all of itself in both languages`() {
        for (n in library.nutrients) {
            assertTrue(n.en.isNotBlank() && n.bn.isNotBlank(), "${n.key} has no name")
            assertTrue(n.unit.isNotBlank() && n.unitBn.isNotBlank(), "${n.key} has no unit")
            assertTrue(n.whyEn.isNotBlank() && n.whyBn.isNotBlank(), "${n.key} has no why")
            assertTrue(n.refEn.isNotBlank() && n.refBn.isNotBlank(), "${n.key} has no reference")
            assertTrue(
                n.reads in setOf("total", "micros", "both"),
                "${n.key} reads `${n.reads}`, which nothing knows how to look up",
            )
        }
    }

    /** A nutrient whose group is not one of the four is a nutrient
        no heading covers, so the panel draws every other one and
        leaves it out. Silent, and the screen looks complete. */
    @Test fun `every nutrient falls under a group the panel draws`() {
        val groups = library.groups.map { it.id }.toSet()
        for (n in library.nutrients) {
            assertTrue(n.group in groups, "${n.key} is in `${n.group}`, which is not a group")
        }
        for (g in library.groups) {
            assertNotNull(
                library.inGroup(g.id).firstOrNull(),
                "the group `${g.id}` has no nutrients, so it draws as a bare heading",
            )
        }
    }

    /** Every key the coverage list names is a nutrient the panel
        can draw. A key counted for coverage and never shown is a
        denominator nobody can see the numerator of. */
    @Test fun `every coverage key is a nutrient`() {
        for (key in library.coverage) {
            assertNotNull(library.nutrient(key), "`$key` is counted and never drawn")
        }
        for (key in library.macros) {
            assertNotNull(library.nutrient(key), "`$key` is totalled and never drawn")
        }
    }
}
