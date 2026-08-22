package uk.co.reiad.library.core.stock

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/* ============================================================
   The judgement half, against the site's model.

   `ModelTest` covers the arithmetic: sixty-seven ratios, Altman
   and Piotroski. This covers everything built on top of them, and
   it is the half where a port goes wrong invisibly, because every
   number here is plausible on its own. A metric silently reading
   `na` on every company still produces a score, a grade and a
   verdict; it just produces the WRONG ones, and nothing about the
   output says so.

   So the assertions below are deliberately about the shape of the
   disagreement rather than only its size: which metrics applied,
   how many of a pillar were asked, which flags fired, which
   signals, and whether the price cap bit.

   Every number comes from `content/stock.fixtures.json`, which
   `scripts/export-stock-fixtures.ts` writes out of the
   JavaScript. Change a weight there and this fails.
   ============================================================ */
class VerdictTest {

    private val cases: List<JsonObject> = Json
        .parseToJsonElement(
            requireNotNull(javaClass.getResourceAsStream("/fixtures/stock.json")) {
                "fixtures/stock.json is missing. It is generated on the website by " +
                    "scripts/export-stock-fixtures.ts and copied here."
            }.readBytes().decodeToString(),
        ).jsonObject["cases"]!!.jsonArray.map { it.jsonObject }

    private fun name(case: JsonObject) = case["name"]!!.jsonPrimitive.content
    private fun out(case: JsonObject) = case["out"]!!.jsonObject

    /** The generator's input patch, as `Inputs`.

        Deliberately a second copy of `ModelTest`'s reader rather
        than a shared helper: a helper that quietly dropped a
        field would drop it from both files at once, and the
        `else -> error` is the only thing here that catches a
        field added on the site. */
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

    /** The same number, or the same refusal. JSON has no NaN, so
        the generator wrote null where the model said "not
        testable", and a null against a NaN is a PASS. */
    private fun same(what: String, expected: JsonElement?, got: Double) {
        if (expected == null || expected is JsonNull) {
            assertTrue(got.isNaN(), "$what: the model said nothing and this said $got")
            return
        }
        val want = expected.jsonPrimitive.doubleOrNull ?: return
        assertTrue(!got.isNaN(), "$what: the model said $want and this said nothing")
        val scale = maxOf(abs(want), abs(got), 1.0)
        assertTrue(abs(want - got) / scale < 1e-9, "$what: said $want, this said $got")
    }

    /** The same number, where either side may be absent. */
    private fun sameOrNull(what: String, expected: JsonElement?, got: Double?) {
        same(what, expected, got ?: Double.NaN)
    }

    /* ============================================================
       1. Forty-four metrics, each with a score AND an answer to
          "does this apply here"
       ============================================================ */

    @Test
    fun `every metric scores what the site scored`() {
        var compared = 0
        var skipped = 0
        for (case in cases) {
            val n = name(case)
            val want = out(case)["scored"]!!.jsonObject
            val got = scoreMetrics(Facts.of(inputsOf(case["input"]!!.jsonObject)))
                .associateBy { it.id }

            assertEquals(
                want.keys, got.keys,
                "$n: this port's metrics are not the site's. A metric added on one side " +
                    "and not the other changes every pillar it is in.",
            )

            for ((id, entry) in want) {
                val w = entry.jsonObject
                val g = got.getValue(id)

                /* `na` first, because it is the one that decides
                   whether the rest means anything. A metric that
                   has silently gone `na` still produces a pillar
                   score; it is just the wrong one. */
                assertEquals(
                    w["na"]!!.jsonPrimitive.boolean, g.na,
                    "$n.$id: one of the two thinks this metric does not apply here",
                )
                if (g.na) skipped += 1

                sameOrNull("$n.$id.score", w["score"], g.score)
                same("$n.$id.value", w["value"], g.value)
                same("$n.$id.raw", w["raw"], g.raw)
                assertEquals(w["grade"]!!.jsonPrimitive.content, g.grade, "$n.$id: grade")
                assertEquals(w["pillar"]!!.jsonPrimitive.content, g.pillar, "$n.$id: pillar")
                assertEquals(
                    w["w"]!!.jsonPrimitive.content.toDouble(), g.w,
                    "$n.$id: weight inside its pillar",
                )
                assertEquals(w["fmt"]!!.jsonPrimitive.content, g.fmt, "$n.$id: format")
                assertEquals(w["hi"]!!.jsonPrimitive.boolean, g.hi, "$n.$id: direction")
                compared += 1
            }
        }
        assertEquals(44 * 7, compared, "every metric of every case should have been compared")
        /* If nothing was ever skipped, the `na` half of this is
           asserting nothing: a port that scored everything would
           pass. The bank alone drops eleven. */
        assertTrue(skipped > 20, "only $skipped metrics were dropped across seven companies")
    }

    /* ============================================================
       2. The pillars, and how much of each could be asked
       ============================================================ */

    @Test
    fun `every pillar matches, including how many metrics it had`() {
        for (case in cases) {
            val n = name(case)
            val want = out(case)["pillars"]!!.jsonObject
            val got = scorePillars(scoreMetrics(Facts.of(inputsOf(case["input"]!!.jsonObject))))

            assertEquals(want.keys.toList(), got.keys.toList(), "$n: the pillars, in order")
            for ((p, entry) in want) {
                val w = entry.jsonObject
                val g = got.getValue(p)
                sameOrNull("$n.$p.score", w["score"], g.score)
                assertEquals(
                    w["n"]!!.jsonPrimitive.content.toInt(), g.n,
                    "$n.$p: a different number of metrics applied, which changes the mean " +
                        "even where the score happens to land in the same place",
                )
                assertEquals(w["of"]!!.jsonPrimitive.content.toInt(), g.of, "$n.$p: of")
            }
        }
    }

    /* A bank exists in the fixtures for exactly this: the
       financial path is the one place where "not applicable" and
       "scored zero" produce wildly different verdicts, and the
       generic screener that marks a sound bank as distressed is
       the failure the whole branch was written to avoid. */
    @Test
    fun `a bank is scored on a bank's ratios and not marked down for the rest`() {
        val bank = cases.first { name(it) == "bank" }
        val f = Facts.of(inputsOf(bank["input"]!!.jsonObject))
        val scored = scoreMetrics(f).associateBy { it.id }

        assertTrue(f.isFinancial, "the bank fixture is not being read as a financial")
        for (id in listOf("debtEquity", "netDebtEbitda", "interestCover", "altmanZ",
                "evEbitda", "ps", "grossMargin")) {
            assertTrue(scored.getValue(id).na, "$id should not apply to a bank")
        }
        for (id in listOf("car", "npl", "provisionCover", "costIncome", "adr")) {
            assertTrue(!scored.getValue(id).na, "$id is what a bank IS supervised on")
        }
        val health = scorePillars(scoreMetrics(f)).getValue("health")
        assertTrue(
            health.score != null && health.score!! > 40,
            "a sound bank scored ${health.score} on health, which is the generic-screener bug",
        )
    }

    /* ============================================================
       3. The composite, the grade and the band
       ============================================================ */

    @Test
    fun `the score, the grade and the verdict band match`() {
        for (case in cases) {
            val n = name(case)
            val o = out(case)
            val a = analyse(inputsOf(case["input"]!!.jsonObject))

            sameOrNull("$n.score", o["score"], a.score)
            assertEquals(o["grade"]!!.jsonPrimitive.content, grade(a.score), "$n: grade")
            assertEquals(
                o["band"]!!.jsonObject["id"]!!.jsonPrimitive.content, a.earned.id,
                "$n: the band the score alone earns",
            )
            val cap = o["priceCap"]!!
            assertEquals(
                if (cap is JsonNull) null else cap.jsonPrimitive.content,
                priceCap(a.pillars),
                "$n: the price ceiling",
            )
        }
    }

    /* The cap is the model's answer to a plain weighted mean
       being unable to answer the question this page asks, and
       both halves of that argument are asserted here.

       Valuation is one pillar of six, so on balanced weights it
       controls about a fifth of the score. Raising the default
       company's price by 62% collapses its valuation pillar from
       46 to 24 and moves the composite less than five points,
       which still reads as "worth accumulating". That is fatal
       for a tool whose entire question is whether to buy AT THIS
       PRICE, and the cap is what fixes it. */
    @Test
    fun `a mean alone cannot answer the price question, so the cap does`() {
        val fair = analyse(Inputs())
        val dear = analyse(Inputs().copy(price = 340.0))

        val valueFall = fair.pillars.getValue("value").score!! - dear.pillars.getValue("value").score!!
        val scoreFall = fair.score!! - dear.score!!
        assertTrue(valueFall > 20, "valuation should have collapsed, and fell $valueFall")
        assertTrue(
            scoreFall < 6,
            "the composite fell $scoreFall, so a mean WOULD have answered this and the cap " +
                "is no longer proving anything. Re-pick the price.",
        )

        assertEquals("accumulate", dear.earned.id, "the score alone still says accumulate")
        assertEquals("hold", priceCap(dear.pillars), "the ceiling should have come down")
        assertEquals("hold", dear.verdict.id, "and the verdict with it")
        assertTrue(dear.capped, "the page has to SAY it was capped; a silent correction is worse")
    }

    /* Past the point where the mean does catch up, the cap must
       stop claiming credit for a verdict the score reached on its
       own, or the page prints a correction that did not happen. */
    @Test
    fun `a cap that agrees with the score is not reported as a cap`() {
        val a = analyse(Inputs().copy(price = 630.0))
        assertEquals("hold", a.earned.id)
        assertEquals("hold", priceCap(a.pillars))
        assertTrue(!a.capped, "nothing was capped: the score got there by itself")
    }

    @Test
    fun `a veto overrides the score outright and says which one`() {
        val z = cases.first { name(it) == "tiny-illiquid" }
        val a = analyse(inputsOf(z["input"]!!.jsonObject))
        assertTrue(a.vetoed, "a Z-category company is a veto")
        assertEquals("avoid", a.verdict.id)
        assertTrue(a.veto != null && a.veto!!.level == "veto")
        assertTrue(!a.capped, "a vetoed company is not additionally capped")
    }

    /* ============================================================
       4. Flags and signals, which are most of what a reader reads
       ============================================================ */

    @Test
    fun `the same flags fire, in the same order, with the same numbers`() {
        var fired = 0
        for (case in cases) {
            val n = name(case)
            val want = out(case)["flags"]!!.jsonArray.map { it.jsonObject }
            val got = checkFlags(Facts.of(inputsOf(case["input"]!!.jsonObject)))

            assertEquals(
                want.map { it["id"]!!.jsonPrimitive.content }, got.map { it.id },
                "$n: the flags disagree",
            )
            for ((i, flag) in got.withIndex()) {
                assertEquals(
                    want[i]["level"]!!.jsonPrimitive.content, flag.level,
                    "$n.${flag.id}: a veto read as a warning, or the reverse",
                )
                /* The numbers inside the sentence, which is what
                   a reader actually sees: "interest cover 0.4×". */
                val vars = want[i]["vars"]!!.jsonObject
                assertEquals(vars.keys, flag.vars.keys, "$n.${flag.id}: the interpolated names")
                for ((k, v) in vars) same("$n.${flag.id}.$k", v, flag.vars.getValue(k))
                fired += 1
            }
        }
        assertTrue(fired > 12, "only $fired flags fired across seven companies; too few to trust")
    }

    @Test
    fun `the same signals fire, in the same order`() {
        var fired = 0
        for (case in cases) {
            val n = name(case)
            val d = inputsOf(case["input"]!!.jsonObject)
            val f = Facts.of(d)
            val want = out(case)["signals"]!!.jsonArray.map { it.jsonObject }
            val got = signals(f, scorePillars(scoreMetrics(f)))

            assertEquals(
                want.map { it["id"]!!.jsonPrimitive.content }, got.map { it.id },
                "$n: the combined signals disagree",
            )
            for ((i, s) in got.withIndex()) {
                assertEquals(want[i]["tone"]!!.jsonPrimitive.content, s.tone, "$n.${s.id}: tone")
            }
            fired += got.size
        }
        assertTrue(fired > 5, "only $fired signals fired; the fixtures are not exercising these")
    }

    /* ============================================================
       5. Fair value, the Shariah screen and the drags
       ============================================================ */

    @Test
    fun `fair value triangulates to the same range`() {
        for (case in cases) {
            val n = name(case)
            val want = out(case)["fairValue"]!!.jsonObject
            val got = fairValue(Facts.of(inputsOf(case["input"]!!.jsonObject)))

            val wantAnchors = want["anchors"]!!.jsonArray.map { it.jsonObject }
            assertEquals(
                wantAnchors.map { it["id"]!!.jsonPrimitive.content }, got.anchors.map { it.id },
                "$n: a different set of anchors could be built, which changes the median",
            )
            for ((i, a) in got.anchors.withIndex()) {
                same("$n.fair.${a.id}", wantAnchors[i]["value"], a.value)
            }
            same("$n.fair.low", want["low"], got.low)
            same("$n.fair.mid", want["mid"], got.mid)
            same("$n.fair.high", want["high"], got.high)
            same("$n.fair.marginOfSafety", want["marginOfSafety"], got.marginOfSafety)
            same("$n.fair.spread", want["spread"], got.spread)
        }
    }

    @Test
    fun `the shariah screen agrees on every test and on the answer`() {
        for (case in cases) {
            val n = name(case)
            val want = out(case)["shariah"]!!.jsonObject
            val got = shariahScreen(Facts.of(inputsOf(case["input"]!!.jsonObject)))

            val wantTests = want["tests"]!!.jsonArray.map { it.jsonObject }
            assertEquals(wantTests.size, got.tests.size, "$n: a screen went missing")
            for ((i, t) in got.tests.withIndex()) {
                assertEquals(wantTests[i]["id"]!!.jsonPrimitive.content, t.id, "$n: screen $i")
                same("$n.shariah.${t.id}", wantTests[i]["value"], t.value)
                same("$n.shariah.${t.id}.limit", wantTests[i]["limit"], t.limit)
                assertEquals(
                    wantTests[i]["pass"]!!.jsonPrimitive.boolean, t.pass,
                    "$n.shariah.${t.id}: pass",
                )
            }
            assertEquals(want["pass"]!!.jsonPrimitive.boolean, got.pass, "$n: the screen's answer")
        }
    }

    @Test
    fun `the drags name the same metrics in the same order`() {
        for (case in cases) {
            val n = name(case)
            val d = inputsOf(case["input"]!!.jsonObject)
            val f = Facts.of(d)
            val scored = scoreMetrics(f)
            val pillars = scorePillars(scored)
            val want = out(case)["drags"]!!.jsonArray.map { it.jsonObject }
            val got = drags(scored, pillars, WEIGHT_PRESETS.getValue("balanced"))

            assertEquals(
                want.map { it["id"]!!.jsonPrimitive.content }, got.map { it.id },
                "$n: 'what would have to change' is a different answer here",
            )
            for ((i, g) in got.withIndex()) {
                same("$n.drag.${g.id}.cost", want[i]["cost"], g.cost)
                same("$n.drag.${g.id}.share", want[i]["share"], g.share)
                same("$n.drag.${g.id}.score", want[i]["score"], g.score)
            }
        }
    }

    /* The share of the composite each metric controls has to add
       up, or "this costs you 6 points" is not a statement about
       anything. Only over the metrics that survive the limit, so
       this is asserted on the whole list rather than the top six. */
    @Test
    fun `the shares of the composite add to one`() {
        val f = Facts.of(Inputs())
        val scored = scoreMetrics(f)
        val pillars = scorePillars(scored)
        val all = drags(scored, pillars, WEIGHT_PRESETS.getValue("balanced"), limit = 99)
        assertEquals(1.0, all.sumOf { it.share }, 1e-9)
    }

    /* ============================================================
       6. The weights are the reader's, which is the model's whole
          argument about itself
       ============================================================ */

    @Test
    fun `changing the weights changes the verdict`() {
        val d = Inputs()
        val balanced = analyse(d, WEIGHT_PRESETS.getValue("balanced"))
        val income = analyse(d, WEIGHT_PRESETS.getValue("income"))
        assertTrue(
            abs(balanced.score!! - income.score!!) > 1,
            "an income investor and a balanced one got the same score, so the weights do nothing",
        )
        /* And a preset that zeroes a pillar must redistribute
           rather than score it nought. */
        val growth = WEIGHT_PRESETS.getValue("growth")
        assertEquals(0.0, growth["income"])
        val g = analyse(d, growth)
        assertTrue(g.score!! > 0)
    }

    @Test
    fun `the four presets are the site's, to the point`() {
        assertEquals(setOf("balanced", "value", "growth", "income"), WEIGHT_PRESETS.keys)
        for ((id, w) in WEIGHT_PRESETS) {
            assertEquals(PILLARS.toSet(), w.keys, "$id names a pillar that does not exist")
            assertEquals(100.0, w.values.sum(), 1e-9, "$id's weights do not add to 100")
        }
    }

    /* ============================================================
       7. The bands, and the room either side of a verdict
       ============================================================ */

    @Test
    fun `a band edge says how much room there is either side`() {
        val e = bandEdges(70.0)                          // accumulate: 62 to 75
        assertEquals(75.0, e.up)
        assertEquals(62.0, e.down)

        val top = bandEdges(90.0)
        assertEquals(null, top.up, "nothing is above buy")

        val bottom = bandEdges(10.0)
        assertEquals(35.0, bottom.up)
        assertEquals(null, bottom.down, "nothing is below avoid")

        /* The site would index past the end of the list here. It
           cannot be reached through `analyse`, and it must not
           throw if it is reached any other way. */
        assertEquals(Edges(null, null), bandEdges(Double.NaN))
        assertEquals(Edges(null, null), bandEdges(null))
    }

    @Test
    fun `a company nothing can be said about lands on avoid rather than on nothing`() {
        assertEquals("avoid", bandFor(null).id)
        assertEquals("avoid", bandFor(Double.NaN).id)
    }
}
