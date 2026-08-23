package uk.co.reiad.library.core.stock

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/* ============================================================
   This port against the site's own model, number for number.

   `content/stock.fixtures.json` on the website is what the
   JavaScript said for seven deliberately different companies, and
   this asserts the Kotlin says the same. It is the only honest
   way to port twelve hundred lines of arithmetic: two
   implementations of a model this size do not stay in step by
   being written carefully.

   ---- the tolerance, and why it is not zero ----

   1e-9 RELATIVE. Both sides are IEEE 754 doubles doing the same
   operations in the same order, so they agree exactly almost
   everywhere; where they do not it is the last bit of a division
   chain, and demanding bit-equality would fail on a change that
   moved a multiplication one line.

   Relative rather than absolute because these numbers run from
   0.003 to 252000: an epsilon tight enough to catch an error in a
   ratio is meaningless on a market capitalisation.

   ---- NaN is a value here ----

   JSON has no NaN, so the generator wrote `null` where the model
   said "not testable". A Kotlin NaN against a JSON null is a
   PASS, and a number against a null is a failure, because half
   this model's correctness is in what it refuses to say.
   ============================================================ */
class ModelTest {

    private val cases: List<JsonObject> = Json
        .parseToJsonElement(
            requireNotNull(javaClass.getResourceAsStream("/fixtures/stock.json")) {
                "fixtures/stock.json is missing. It is generated on the website by " +
                    "scripts/export-stock-fixtures.ts and copied here."
            }.readBytes().decodeToString(),
        ).jsonObject["cases"]!!.jsonArray.map { it.jsonObject }

    /** The generator's input object, as `Inputs`.

        Applied OVER the defaults, exactly as the generator does
        with its spread, so a case naming three fields is the
        archetype with three changes.

        The `else -> error` is the point of writing it out: a
        field added to the model on the site fails here loudly
        rather than being ignored into a wrong answer. */
    private fun inputsOf(patch: JsonObject): Inputs {
        var d = Inputs()
        for ((key, value) in patch) {
            val text = (value as? JsonPrimitive)?.content ?: continue
            val n = text.toDoubleOrNull()
            d = when (key) {
                "price" -> d.copy(price = n!!)
                "shares" -> d.copy(shares = n!!)
                "high52" -> d.copy(high52 = n!!)
                "low52" -> d.copy(low52 = n!!)
                "ma50" -> d.copy(ma50 = n!!)
                "ma200" -> d.copy(ma200 = n!!)
                "turnover" -> d.copy(turnover = n!!)
                "freeFloat" -> d.copy(freeFloat = n!!)
                "category" -> d.copy(category = text)
                "sector" -> d.copy(sector = text)
                "benchmark" -> d.copy(benchmark = text)
                "stockReturn12m" -> d.copy(stockReturn12m = n!!)
                "indexReturn12m" -> d.copy(indexReturn12m = n!!)
                "revenue" -> d.copy(revenue = n!!)
                "grossProfit" -> d.copy(grossProfit = n!!)
                "ebit" -> d.copy(ebit = n!!)
                "depreciation" -> d.copy(depreciation = n!!)
                "interestExpense" -> d.copy(interestExpense = n!!)
                "netIncome" -> d.copy(netIncome = n!!)
                "totalAssets" -> d.copy(totalAssets = n!!)
                "currentAssets" -> d.copy(currentAssets = n!!)
                "inventory" -> d.copy(inventory = n!!)
                "cash" -> d.copy(cash = n!!)
                "currentLiabilities" -> d.copy(currentLiabilities = n!!)
                "totalDebt" -> d.copy(totalDebt = n!!)
                "equity" -> d.copy(equity = n!!)
                "reserves" -> d.copy(reserves = n!!)
                "cfo" -> d.copy(cfo = n!!)
                "capex" -> d.copy(capex = n!!)
                "dps" -> d.copy(dps = n!!)
                "divTax" -> d.copy(divTax = n!!)
                "yearsPaid" -> d.copy(yearsPaid = n!!)
                "revenuePrev" -> d.copy(revenuePrev = n!!)
                "grossProfitPrev" -> d.copy(grossProfitPrev = n!!)
                "netIncomePrev" -> d.copy(netIncomePrev = n!!)
                "totalAssetsPrev" -> d.copy(totalAssetsPrev = n!!)
                "currentAssetsPrev" -> d.copy(currentAssetsPrev = n!!)
                "currentLiabilitiesPrev" -> d.copy(currentLiabilitiesPrev = n!!)
                "totalDebtPrev" -> d.copy(totalDebtPrev = n!!)
                "cfoPrev" -> d.copy(cfoPrev = n!!)
                "sharesPrev" -> d.copy(sharesPrev = n!!)
                "netIncome3y" -> d.copy(netIncome3y = n!!)
                "car" -> d.copy(car = n!!)
                "npl" -> d.copy(npl = n!!)
                "provisionCover" -> d.copy(provisionCover = n!!)
                "costIncome" -> d.copy(costIncome = n!!)
                "adr" -> d.copy(adr = n!!)
                "sectorPE" -> d.copy(sectorPE = n!!)
                "sectorPB" -> d.copy(sectorPB = n!!)
                "sectorROE" -> d.copy(sectorROE = n!!)
                "sectorMargin" -> d.copy(sectorMargin = n!!)
                "marketPE" -> d.copy(marketPE = n!!)
                "riskFree" -> d.copy(riskFree = n!!)
                "fdr" -> d.copy(fdr = n!!)
                "inflation" -> d.copy(inflation = n!!)
                "nonCompliantIncome" -> d.copy(nonCompliantIncome = n!!)
                else -> error("the fixture sets '$key' and Inputs has no field for it")
            }
        }
        return d
    }

    /** The same number, or the same refusal. */
    private fun same(what: String, expected: JsonElement?, got: Double) {
        if (expected == null || expected is JsonNull) {
            assertTrue(got.isNaN(), "$what: the model said nothing and this said $got")
            return
        }
        val want = expected.jsonPrimitive.doubleOrNull ?: return
        assertTrue(!got.isNaN(), "$what: the model said $want and this said nothing")
        val scale = maxOf(abs(want), abs(got), 1.0)
        assertTrue(
            abs(want - got) / scale < 1e-9,
            "$what: the model said $want and this said $got",
        )
    }

    @Test
    fun `every case is a shape the model has a branch for`() {
        assertEquals(7, cases.size)
        val names = cases.map { it["name"]!!.jsonPrimitive.content }.toSet()
        assertTrue("bank" in names, "the financial path is untested without a bank")
        assertTrue("no-prior-year" in names, "not-testable is untested without one")
        assertTrue("loss-maker" in names)
        /* Each case says what it is FOR, in the fixture, so one
           added later without a reason is visible. */
        for (case in cases) {
            assertTrue(
                case["why"]!!.jsonPrimitive.content.length > 20,
                "${case["name"]} does not say what it is for",
            )
        }
    }

    @Test
    fun `every ratio matches the site's model`() {
        var compared = 0
        for (case in cases) {
            val name = case["name"]!!.jsonPrimitive.content
            val d = inputsOf(case["input"]!!.jsonObject)
            val want = case["out"]!!.jsonObject["ratios"]!!.jsonObject
            val got = ratios(d)

            for ((key, value) in want) {
                /* Four of the fixture's keys are not ratios:
                   `isFinancial` is a boolean, and the three
                   Piotroski fields are asserted on their own
                   terms below. */
                if (key in setOf("isFinancial", "fScore", "fTested", "fChecks")) continue
                assertTrue(key in got, "$name: this port has no ratio called '$key'")
                same("$name.$key", value, got.getValue(key))
                compared += 1
            }
        }
        assertTrue(compared > 400, "only $compared ratios compared; the fixture looks thin")
    }

    /** And nothing EXTRA, because a ratio this port invents is
        one the site will not agree with the day somebody uses
        it. */
    @Test
    fun `this port invents no ratios of its own`() {
        val want = cases.first()["out"]!!.jsonObject["ratios"]!!.jsonObject.keys
        val got = ratios(Inputs()).keys
        assertTrue(
            (got - want).isEmpty(),
            "this port has ratios the site does not: ${got - want}",
        )
    }

    @Test
    fun `altman and piotroski match the site's model`() {
        for (case in cases) {
            val name = case["name"]!!.jsonPrimitive.content
            val d = inputsOf(case["input"]!!.jsonObject)
            val out = case["out"]!!.jsonObject
            val r = ratios(d)

            same("$name.altman", out["altman"], altmanZ(d))

            val f = piotroski(d, r)
            val wantF = out["piotroski"]!!.jsonObject
            assertEquals(
                wantF["score"]!!.jsonPrimitive.content.toInt(),
                f.score,
                "$name: the F-score disagrees",
            )
            assertEquals(
                wantF["tested"]!!.jsonPrimitive.content.toInt(),
                f.tested,
                "$name: the number of TESTABLE checks disagrees, which is the difference " +
                    "between 'we cannot tell' and 'it is bad'",
            )

            val wantChecks = wantF["checks"]!!.jsonArray.map { it.jsonObject }
            assertEquals(wantChecks.size, f.checks.size, "$name: a check went missing")
            for ((i, check) in f.checks.withIndex()) {
                val expected = wantChecks[i]
                assertEquals(
                    expected["id"]!!.jsonPrimitive.content,
                    check.id,
                    "$name: check $i is a different question",
                )
                val pass = expected["pass"]!!
                if (pass is JsonNull) {
                    assertEquals(null, check.pass, "$name.${check.id} should be skipped")
                } else {
                    assertEquals(
                        pass.jsonPrimitive.content.toBoolean(),
                        check.pass,
                        "$name.${check.id} disagrees",
                    )
                }
            }
        }
    }

    /* ---------- the primitives, on their own ---------- */

    @Test
    fun `a band interpolates rather than bucketing`() {
        val anchors = listOf(0.4 to 100.0, 1.0 to 60.0, 2.0 to 15.0)
        assertEquals(100.0, band(0.2, anchors))
        assertEquals(100.0, band(0.4, anchors))
        assertEquals(60.0, band(1.0, anchors))
        assertEquals(15.0, band(3.0, anchors))
        /* The whole reason anchors are not thresholds: 14.9 and
           15.1 are the same company. */
        val a = band(0.999, anchors)!!
        val b = band(1.001, anchors)!!
        assertTrue(abs(a - b) < 0.2, "a band should not jump: $a then $b")
    }

    @Test
    fun `a band refuses a number that is not there`() {
        assertEquals(null, band(Double.NaN, listOf(0.0 to 1.0)))
    }

    @Test
    fun `division by nothing is nothing, never infinity`() {
        assertTrue(div(1.0, 0.0).isNaN(), "Infinity would sail through a comparison")
        assertTrue(div(Double.NaN, 2.0).isNaN())
        assertEquals(0.5, div(1.0, 2.0))
    }

    @Test
    fun `a change from a negative base has no readable sign`() {
        assertTrue(pctChange(5.0, -10.0).isNaN())
        assertTrue(pctChange(5.0, 0.0).isNaN())
        assertEquals(50.0, pctChange(15.0, 10.0))
    }

    @Test
    fun `a cagr across a sign change is a number with no content`() {
        assertTrue(cagr(100.0, -50.0, 3.0).isNaN())
        assertTrue(cagr(-100.0, 50.0, 3.0).isNaN())
        assertTrue(cagr(100.0, 50.0, 0.0).isNaN())
    }

    @Test
    fun `the grade bands are the site's`() {
        assertEquals("strong", grade(80.0))
        assertEquals("good", grade(62.0))
        assertEquals("fair", grade(45.0))
        assertEquals("weak", grade(28.0))
        assertEquals("poor", grade(27.9))
        assertEquals("na", grade(null))
        assertEquals("na", grade(Double.NaN))
    }
}
