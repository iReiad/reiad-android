package uk.co.reiad.library.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import uk.co.reiad.library.core.diet.Ate
import uk.co.reiad.library.core.diet.DietEntry
import uk.co.reiad.library.core.diet.FoodLibrary
import uk.co.reiad.library.core.diet.readingFor
import uk.co.reiad.library.core.diet.tooSparse
import uk.co.reiad.library.core.diet.totalFor
import uk.co.reiad.library.core.diet.loggedFrom
import uk.co.reiad.library.core.diet.scaleTo
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/* ============================================================
   The portion library, against what /api/foods really sends.

   `fixtures/foods.json` is the endpoint's own payload, so a row
   that gains a field or a nutrient that gains a key is something
   this file finds out about rather than something the app drops
   silently.

   ---- what these are actually guarding ----

   Two different things, and they fail differently.

   The DECODE half guards the contract: a nutrient added on the
   site has to reach this build with no release, which is only
   true while nothing in `Foods.kt` names one. `everyStatedFigure`
   is what would fail the day somebody replaces the map with a
   data class, and it would fail on a library that decodes
   perfectly.

   The ARITHMETIC half guards a reader. A log wrong in the
   flattering direction is what this whole tool is built against,
   so the refusals matter more than the sums: `scaleTo` returning
   null is the tool saying it does not know, and a version of it
   that guessed would look identical on screen.
   ============================================================ */
class FoodsTest {

    private val library: FoodLibrary = run {
        val text = checkNotNull(
            object {}.javaClass.getResourceAsStream("/fixtures/foods.json"),
        ) { "missing fixture foods.json" }.readBytes().decodeToString()
        val root = Json.parseToJsonElement(text) as JsonObject
        checkNotNull(FoodLibrary.from(root)) { "the endpoint's own payload did not parse" }
    }

    private fun near(expected: Double, actual: Double, by: Double = 0.05) =
        assertTrue(abs(expected - actual) <= by, "expected $expected, was $actual")

    /* ---------- what arrived ---------- */

    @Test fun `the whole library decodes`() {
        assertTrue(library.foods.size >= 80, "only ${library.foods.size} rows")
        assertTrue(library.nutrients.size >= 19, "only ${library.nutrients.size} nutrients")
        assertEquals(4, library.groups.size)
        assertTrue(library.units.size >= 15, "only ${library.units.size} units")
    }

    /** The contract, and the reason `per` is a map.

        Every numeric field on a row that is not structural has to
        arrive, whatever it is called. The day this fails is the
        day somebody wrote the nutrient names into Kotlin, and the
        library will still decode: that is exactly why it is
        asserted against the fixture's own keys rather than
        against a list here. */
    @Test fun `every stated figure arrives, under the name the site gave it`() {
        val text = checkNotNull(
            object {}.javaClass.getResourceAsStream("/fixtures/foods.json"),
        ).readBytes().decodeToString()
        val root = Json.parseToJsonElement(text) as JsonObject
        val rows = (root["foods"] as kotlinx.serialization.json.JsonArray)
            .map { it as JsonObject }

        val structural = setOf(
            "id", "en", "bn", "place", "qty", "unit", "grams", "kcal",
            "source", "tags", "raw", "price", "currency", "pricedOn", "also",
        )
        var checked = 0
        for (row in rows) {
            val id = (row["id"] as kotlinx.serialization.json.JsonPrimitive).content
            val here = assertNotNull(library.byId(id), "row $id did not decode")
            for ((key, value) in row) {
                if (key in structural) continue
                val n = (value as? kotlinx.serialization.json.JsonPrimitive)
                    ?.content?.toDoubleOrNull() ?: continue
                near(n, assertNotNull(here.per[key], "$id lost $key"), 0.0001)
                checked += 1
            }
        }
        assertTrue(checked > 700, "only $checked figures checked")
    }

    /** Absent is not nought, and it is the whole schema. A
        nutrient nobody looked up for a dish must not arrive as a
        zero: §15 counts presence, so a nought bought here is
        coverage the log has not got. */
    @Test fun `a nutrient nobody looked up is absent, never nought`() {
        val thin = library.foods.filter { row ->
            library.coverage.any { it !in row.per }
        }
        assertTrue(thin.isNotEmpty(), "no row is missing a coverage nutrient")
        for (row in thin) {
            for (key in library.coverage) {
                if (key in row.per) continue
                assertNull(row.per[key], "${row.id} carries a phantom $key")
            }
        }
    }

    /** A measured nought IS a figure. An oil carries no sugar and
        saying so is knowledge, so it survives the decode. */
    @Test fun `a measured nought survives`() {
        val zeros = library.foods.count { row -> row.per.values.any { it == 0.0 } }
        assertTrue(zeros > 0, "no row states a nought, so the decode may be dropping them")
    }

    @Test fun `both languages, on every row`() {
        for (row in library.foods) {
            assertTrue(row.en.isNotBlank(), "${row.id} has no English")
            assertTrue(row.bn.isNotBlank(), "${row.id} has no Bangla")
            assertFalse(row.bn == row.en, "${row.id} says the same thing twice")
        }
    }

    /** A unit with no word is a portion said in one language. */
    @Test fun `every unit any row uses has a word in both languages`() {
        for (row in library.foods) {
            val word = assertNotNull(library.units[row.unit], "${row.unit} has no word")
            assertTrue(word.bn.isNotBlank(), "${row.unit} has no Bangla")
        }
        assertNotNull(library.units["g"], "grams has no word, and every row can be asked in them")
    }

    /* ---------- the two lists that split a scaled row ---------- */

    @Test fun `the macro list and the coverage list both arrive and do not overlap`() {
        assertEquals(listOf("protein", "carbs", "fat", "fibre"), library.macros)
        assertTrue(library.coverage.size >= 15)
        assertTrue(
            library.macros.none { it in library.coverage },
            "a nutrient in both lists would be counted twice",
        )
    }

    /* ---------- the search ---------- */

    @Test fun `a name that starts with what was typed leads`() {
        val hits = library.search("rice", "bd")
        assertTrue(hits.isNotEmpty())
        assertTrue(
            hits.first().en.lowercase().startsWith("rice") ||
                hits.first().en.lowercase().contains("rice"),
        )
    }

    /** The other place's rows are returned and come LAST. A
        Bangladeshi reader in Manchester eats both lists. */
    @Test fun `the reader's own place leads and the other is still there`() {
        val bd = library.forPlace("bd")
        val uk = library.forPlace("uk")
        assertTrue(bd.isNotEmpty() && uk.isNotEmpty())

        val onlyUk = library.foods.filter { "uk" in it.place && "bd" !in it.place }
        assertTrue(onlyUk.isNotEmpty(), "no row is UK-only, so this proves nothing")
        val word = onlyUk.first().en.split(" ").first().lowercase()
        val hits = library.search(word, "bd")
        assertTrue(hits.any { "bd" !in it.place }, "the other place's rows were hidden")

        val firstAway = hits.indexOfFirst { "bd" !in it.place }
        val lastHome = hits.indexOfLast { "bd" in it.place }
        if (lastHome >= 0 && firstAway >= 0) {
            assertTrue(firstAway > lastHome, "an away row came before a home row")
        }
    }

    @Test fun `an empty search is the whole of the reader's place`() {
        assertEquals(library.forPlace("bd").map { it.id }, library.search("  ", "bd").map { it.id })
    }

    @Test fun `search reads Bangla too`() {
        val row = library.foods.first { it.bn.isNotBlank() }
        val needle = row.bn.take(3)
        assertTrue(library.search(needle, "bd").isNotEmpty(), "no hit for $needle")
    }

    /* ---------- what was actually eaten ---------- */

    private val rice get() = assertNotNull(library.byId("rice-white-cooked-cup"))

    @Test fun `one of its own portions is the row itself`() {
        val one = assertNotNull(scaleTo(rice, Ate(1.0, "cup"), library.macros, library.coverage))
        near(rice.kcal, one.kcal)
        near(1.0, one.factor)
        near(assertNotNull(rice.grams), assertNotNull(one.grams))
    }

    @Test fun `two portions is twice, in every figure and not only the calories`() {
        val two = assertNotNull(scaleTo(rice, Ate(2.0, "cup"), library.macros, library.coverage))
        near(rice.kcal * 2, two.kcal, 0.2)
        for ((key, value) in two.macros) near(assertNotNull(rice.per[key]) * 2, value, 0.05)
        for ((key, value) in two.micros) near(assertNotNull(rice.per[key]) * 2, value, 0.2)
        assertTrue(two.macros.isNotEmpty() && two.micros.isNotEmpty())
    }

    @Test fun `grams work where the row says what its portion weighs`() {
        val grams = assertNotNull(rice.grams)
        val half = assertNotNull(
            scaleTo(rice, Ate(grams / 2, "g"), library.macros, library.coverage),
        )
        near(rice.kcal / 2, half.kcal, 0.2)
    }

    /** The refusals, which are the point. */
    @Test fun `grams are refused where the row never says what it weighs`() {
        val weightless = library.foods.firstOrNull { it.grams == null }
        if (weightless != null) {
            assertNull(
                scaleTo(weightless, Ate(100.0, "g"), library.macros, library.coverage),
                "${weightless.id} answered a question in grams it cannot answer",
            )
        }
    }

    @Test fun `a unit the row is not measured in is refused`() {
        assertNull(scaleTo(rice, Ate(1.0, "plate"), library.macros, library.coverage))
    }

    @Test fun `nothing, nought and less than nought are all refused`() {
        assertNull(scaleTo(rice, Ate(0.0, "cup"), library.macros, library.coverage))
        assertNull(scaleTo(rice, Ate(-1.0, "cup"), library.macros, library.coverage))
        assertNull(scaleTo(rice, Ate(Double.NaN, "cup"), library.macros, library.coverage))
    }

    /** A tenth of a cup rounds to 0.1 and is a real amount, so it
        must not be rounded away to nothing. */
    @Test fun `a small honest amount is kept`() {
        assertNotNull(scaleTo(rice, Ate(0.1, "cup"), library.macros, library.coverage))
        assertNull(
            scaleTo(rice, Ate(0.004, "cup"), library.macros, library.coverage),
            "an amount that rounds to nought at two places is not an amount",
        )
    }

    /* ---------- what the log stores ---------- */

    @Test fun `a logged row carries both names and says where it came from`() {
        val entry = assertNotNull(
            loggedFrom(rice, Ate(1.5, "cup"), "2026-08-23", library, atTime = "13:20"),
        )
        assertEquals(rice.en, entry.label)
        assertEquals(rice.bn, entry.labelBn)
        assertEquals("library", entry.source)
        assertEquals(rice.id, entry.sourceId)
        assertEquals(1.5, entry.qty)
        assertEquals("cup", entry.unit)
        assertEquals("13:20", entry.atTime)
        assertEquals("2026-08-23", entry.date)
        assertTrue(assertNotNull(entry.macros).isNotEmpty())
        assertTrue(assertNotNull(entry.micros).isNotEmpty())
    }

    /** `diet_entries.qty` is `numeric(9,2)`. A row that scaled by
        1.333 and stored 1.33 is a row whose own numbers do not
        follow from each other. */
    @Test fun `qty is rounded before anything is scaled by it`() {
        val entry = assertNotNull(
            loggedFrom(rice, Ate(1.3333333, "cup"), "2026-08-23", library),
        )
        assertEquals(1.33, entry.qty)
        near(rice.kcal * 1.33, assertNotNull(entry.kcal), 0.2)
    }

    @Test fun `a refused amount logs nothing at all`() {
        assertNull(loggedFrom(rice, Ate(0.0, "cup"), "2026-08-23", library))
        assertNull(loggedFrom(rice, Ate(1.0, "furlong"), "2026-08-23", library))
    }

    /* ---------- a day, added up ---------- */

    private fun logged(row: String, n: Double, unit: String): DietEntry =
        assertNotNull(
            loggedFrom(assertNotNull(library.byId(row)), Ate(n, unit), "2026-08-23", library),
        )

    /** THE ONE THAT WOULD HAVE SHIPPED WRONG.

        Coverage is weighted by ENERGY and not by the number of
        entries. Two rows, one of them a 780 kcal plate with no
        composition at all, is not a half-known day: it is a day
        about a fifth of which is known, and counting rows would
        have called it 50%. That is a mistake in the flattering
        direction, which is the one the whole tool is built
        against. */
    @Test fun `coverage is the share of the day's ENERGY, not of its rows`() {
        val entries = listOf(
            logged("rice-white-cooked-cup", 1.0, "cup"),
            DietEntry(date = "2026-08-23", label = "a plate at a restaurant", kcal = 780.0),
        )
        val day = totalFor(entries, library.macros)
        val riceKcal = assertNotNull(logged("rice-white-cooked-cup", 1.0, "cup").kcal)
        near(riceKcal / (riceKcal + 780.0), day.coverage, 0.01)
        assertTrue(day.coverage < 0.3, "counted rows instead of energy: ${day.coverage}")
        assertEquals(2, day.count)
    }

    @Test fun `a nutrient is totalled out of the rows that state it`() {
        val protein = assertNotNull(library.nutrient("protein"))
        val entries = listOf(logged("rice-white-cooked-cup", 2.0, "cup"))
        val day = totalFor(entries, library.macros)
        val reading = readingFor(protein, day)
        near(assertNotNull(rice.per["protein"]) * 2, assertNotNull(reading.amount), 0.2)
        near(1.0, reading.seen, 0.001)
    }

    /** Where the figure lives is read off the nutrient rather than
        guessed. A macro is a top-level total and everything else
        is in micros, so reading the wrong one gives nothing on a
        day that has plenty of it. */
    @Test fun `a micro is found in micros and a macro in macros`() {
        val day = totalFor(listOf(logged("rice-white-cooked-cup", 1.0, "cup")), library.macros)
        for (key in listOf("protein", "fibre")) {
            val n = library.nutrient(key) ?: continue
            assertNotNull(readingFor(n, day).amount, "$key came back not-known")
        }
        for (key in listOf("iron", "potassium")) {
            val n = library.nutrient(key) ?: continue
            if (rice.per[key] == null) continue
            assertNotNull(readingFor(n, day).amount, "$key came back not-known")
        }
    }

    /** Not known is not nought. A nutrient no row states comes
        back null, and a screen that printed a nought would be
        saying "there is none of it" about food nobody looked
        up. */
    @Test fun `a nutrient nothing states is not known rather than nought`() {
        val bare = DietEntry(date = "2026-08-23", label = "a plate", kcal = 100.0)
        val day = totalFor(listOf(bare), library.macros)
        for (n in library.nutrients) {
            if (n.reads == "total") continue
            assertNull(readingFor(n, day).amount, "${n.key} invented a figure")
        }
    }

    /** Under half the day, nothing is drawn at all. The site's own
        most important rule, and a phone that drew the figures
        anyway would be the more dangerous of the two. */
    @Test fun `a day under the coverage floor is too sparse to read`() {
        val thin = listOf(
            logged("rice-white-cooked-cup", 1.0, "cup"),
            DietEntry(date = "2026-08-23", label = "a plate", kcal = 900.0),
        )
        assertTrue(tooSparse(totalFor(thin, library.macros)))

        val full = listOf(logged("rice-white-cooked-cup", 2.0, "cup"))
        assertFalse(tooSparse(totalFor(full, library.macros)))

        /* An EMPTY day is not sparse. It has nothing wrong with
           it, and the panel says "nothing logged" rather than
           warning somebody at breakfast. */
        assertFalse(tooSparse(totalFor(emptyList(), library.macros)))
    }

    /** A figure drawn from a third of the day is a FLOOR: the rest
        of the day can only have added to it. "About" would be the
        one wrong word available. */
    @Test fun `a thin reading says at least rather than about`() {
        val sodium = library.nutrient("sodium")
        if (sodium != null) {
            val mixed = listOf(
                logged("rice-white-cooked-cup", 1.0, "cup"),
                DietEntry(
                    date = "2026-08-23", label = "an egg", kcal = 400.0,
                    micros = mapOf("iron" to 2.0),
                ),
            )
            val day = totalFor(mixed, library.macros)
            val reading = readingFor(sodium, day)
            if (reading.amount != null) {
                assertTrue(reading.seen < 0.5, "seen was ${reading.seen}")
                assertTrue(reading.thin)
            }
        }
    }

    /** A planned row is what a reader intends to eat. Adding
        tomorrow's plan to today's total is a day that reads twice
        what it was. */
    @Test fun `a planned row is not part of the day`() {
        val day = totalFor(
            listOf(
                logged("rice-white-cooked-cup", 1.0, "cup"),
                DietEntry(
                    date = "2026-08-23", label = "tomorrow's dinner",
                    kcal = 900.0, planned = true,
                ),
            ),
            library.macros,
        )
        assertEquals(1, day.count)
        near(assertNotNull(logged("rice-white-cooked-cup", 1.0, "cup").kcal), day.kcal, 0.2)
    }

    /** A restaurant plate is not knowable. The midpoint goes into
        the total and the WIDTH is what the page has to say beside
        it. */
    @Test fun `the day carries how wide its estimates are`() {
        val day = totalFor(
            listOf(
                DietEntry(
                    date = "2026-08-23", label = "kacchi biryani", kcal = 900.0,
                    estLow = 700.0, estHigh = 1100.0,
                ),
            ),
            library.macros,
        )
        near(400.0, day.spread, 0.01)
    }

    @Test fun `an empty day is nought out of nought and not a division by it`() {
        val protein = assertNotNull(library.nutrient("protein"))
        val day = totalFor(emptyList(), library.macros)
        assertEquals(0.0, day.kcal)
        assertEquals(0.0, day.coverage)
        assertNull(readingFor(protein, day).amount)
    }

    /* ---------- saying a portion ---------- */

    @Test fun `Bangla counts with the numeral attached and English pluralises`() {
        assertEquals("2 cups", library.portionWords("2", "cup", "en"))
        assertEquals("1 cup", library.portionWords("1", "cup", "en"))
        assertEquals("২ কাপ", library.portionWords("২", "cup", "bn"))
        assertEquals("২টা", library.portionWords("২", "piece", "bn"))
    }

    @Test fun `a unit with no word falls back to the token, never to nothing`() {
        assertEquals("1 sachet", library.portionWords("1", "sachet", "en"))
    }

    /* ---------- a library that will not parse ---------- */

    @Test fun `an answer that is not one is null rather than an empty library`() {
        assertNull(FoodLibrary.from(Json.parseToJsonElement("{}") as JsonObject))
        assertNull(FoodLibrary.from(Json.parseToJsonElement("""{"foods":[]}""") as JsonObject))
    }

    /** A row this build cannot read is DROPPED and the rest of
        the library arrives. A phone installed in March reads a
        deploy from August. */
    @Test fun `one unreadable row does not take the library with it`() {
        val root = Json.parseToJsonElement(
            """{"foods":[{"nonsense":true},
               {"id":"x","en":"x","qty":1,"unit":"g","kcal":1}],
               "coverage":["iron"],"macros":["protein"]}""",
        ) as JsonObject
        val small = assertNotNull(FoodLibrary.from(root))
        assertEquals(1, small.foods.size)
        assertEquals("x", small.foods.first().id)
    }
}
