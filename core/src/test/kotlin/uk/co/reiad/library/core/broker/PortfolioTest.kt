package uk.co.reiad.library.core.broker

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/* ============================================================
   The broker derivations against the site's own.

   `content/portfolio.fixtures.json` is what `shared/portfolio.ts`
   produced for four summaries, three sets of positions and two
   dividend histories, and every number of it is asserted here.

   The fixture's account is INVENTED and has to be. The real one
   is one person's and that file is committed; every rule in
   CLAUDE.md's Backups section about what may go in git applies,
   and "the repository is private" is not an answer.

   So the positions in it are made up, and made up to be awkward:
   a holding bought for nothing, one with no ticker, a loss, a
   summary with a string where a number belongs. A fixture of four
   healthy holdings would prove the happy path agrees and nothing
   else.
   ============================================================ */
class PortfolioTest {

    private val fixture: JsonObject = Json
        .parseToJsonElement(
            requireNotNull(javaClass.getResourceAsStream("/fixtures/portfolio.json")) {
                "fixtures/portfolio.json is missing; the website generates it."
            }.readBytes().decodeToString(),
        ).jsonObject

    private fun same(what: String, want: Double, got: Double) {
        val scale = maxOf(abs(want), abs(got), 1.0)
        assertTrue(abs(want - got) / scale < 1e-9, "$what: said $want, this said $got")
    }

    private fun why(case: JsonObject): String =
        "${case["name"]!!.jsonPrimitive.content}: ${case["why"]!!.jsonPrimitive.content}"

    private fun Totals.readOf(key: String): Double = when (key) {
        "total" -> total
        "invested" -> invested
        "cost" -> cost
        "unrealised" -> unrealised
        "unrealisedPct" -> unrealisedPct
        "realised" -> realised
        "freeCash" -> freeCash
        "inPies" -> inPies
        else -> error("no such figure: $key")
    }

    /* ============================================================
       1. The five figures
       ============================================================ */

    @Test
    fun `every summary reads the way the site reads it`() {
        val cases = fixture["summaries"]!!.jsonArray.map { it.jsonObject }
        assertEquals(4, cases.size)
        for (case in cases) {
            val name = case["name"]!!.jsonPrimitive.content
            assertTrue(case["why"]!!.jsonPrimitive.content.length > 20, why(case))
            val want = case["out"]!!.jsonObject
            val got = totalsOf(case["summary"])

            assertEquals(
                want["currency"]!!.jsonPrimitive.content, got.currency,
                "$name: an account with no currency has to fall back to one",
            )
            for (key in listOf("total", "invested", "cost", "unrealised",
                    "unrealisedPct", "realised", "freeCash", "inPies")) {
                same("$name.$key", want[key]!!.jsonPrimitive.content.toDouble(), got.readOf(key))
            }
        }
    }

    /* The failure the whole layer is defensive about. A broker
       that renames a field, or starts sending a string where a
       number was, must not take a public page down. */
    @Test
    fun `a summary the broker changed reads as noughts rather than throwing`() {
        val case = fixture["summaries"]!!.jsonArray.map { it.jsonObject }
            .first { it["name"]!!.jsonPrimitive.content == "broker-changed-a-name" }
        val got = totalsOf(case["summary"])

        /* A quoted number is NOT a number. The site tests
           `typeof v === "number"` and reads it as nought, so this
           has to as well: two implementations disagreeing here
           would show two different account values for one
           account. */
        assertEquals(0.0, got.total, "a quoted 18420.55 is not a number")
        assertEquals(16880.45, got.invested, "and a real one still is")
        assertEquals("GBP", got.currency, "with no currency sent at all")
        assertEquals(0.0, got.unrealisedPct)
    }

    /* ============================================================
       2. The holdings
       ============================================================ */

    @Test
    fun `every holding is derived the way the site derives it`() {
        var compared = 0
        val cases = fixture["positions"]!!.jsonArray.map { it.jsonObject }
        assertEquals(3, cases.size)

        for (case in cases) {
            val name = case["name"]!!.jsonPrimitive.content
            assertTrue(case["why"]!!.jsonPrimitive.content.length > 20, why(case))
            val out = case["out"]!!.jsonObject

            /* Weights are a share of what is invested, so the
               same positions under two invested figures are two
               different answers, and both are asserted. */
            for ((key, invested) in listOf("invested16880" to 16880.45, "investedNought" to 0.0)) {
                val want = out[key]!!.jsonArray.map { it.jsonObject }
                val got = holdingsOf(case["positions"], invested)
                assertEquals(want.size, got.size, "$name.$key: a holding went missing")

                for ((i, h) in got.withIndex()) {
                    val w = want[i]
                    /* Order FIRST. The list is sorted biggest
                       first, and one that reshuffles between
                       refreshes looks like trades that never
                       happened. */
                    assertEquals(w["name"]!!.jsonPrimitive.content, h.name, "$name.$key[$i]: order")
                    assertEquals(w["ticker"]!!.jsonPrimitive.content, h.ticker, "$name[$i]: ticker")
                    assertEquals(w["currency"]!!.jsonPrimitive.content, h.currency)
                    for ((field, mine) in listOf(
                        "quantity" to h.quantity, "averagePaid" to h.averagePaid,
                        "price" to h.price, "value" to h.value, "cost" to h.cost,
                        "gain" to h.gain, "gainPct" to h.gainPct,
                        "weightPct" to h.weightPct, "barPct" to h.barPct,
                    )) {
                        same("$name.$key[$i].$field",
                            w[field]!!.jsonPrimitive.content.toDouble(), mine)
                        compared += 1
                    }
                }
            }
        }
        assertTrue(compared > 60, "only $compared numbers compared")
    }

    @Test
    fun `a holding bought for nothing is not an infinite gain`() {
        val case = fixture["positions"]!!.jsonArray.map { it.jsonObject }
            .first { it["name"]!!.jsonPrimitive.content == "mixed" }
        val free = holdingsOf(case["positions"], 16880.45).first { it.ticker == "FREE" }
        assertEquals(0.0, free.gainPct, "a free share gained infinitely, which renders as a ∞%")
        assertEquals(3.0, free.gain, "and the gain in money is still real")
    }

    @Test
    fun `an endpoint that stopped sending a list is an empty list`() {
        val case = fixture["positions"]!!.jsonArray.map { it.jsonObject }
            .first { it["name"]!!.jsonPrimitive.content == "not-an-array" }
        assertEquals(emptyList(), holdingsOf(case["positions"], 100.0))
    }

    /* ============================================================
       3. The dividends
       ============================================================ */

    @Test
    fun `a year of dividends buckets the way the site buckets it`() {
        /* The generator froze its clock and so does this: a
           fixture built from the real one would change every
           month and this would fail on the first of it. */
        val now = fixture["now"]!!.jsonPrimitive.content
        val year = now.substring(0, 4).toInt()
        val month = now.substring(5, 7).toInt()

        for (case in fixture["dividends"]!!.jsonArray.map { it.jsonObject }) {
            val name = case["name"]!!.jsonPrimitive.content
            assertTrue(case["why"]!!.jsonPrimitive.content.length > 20, why(case))
            val want = case["out"]!!.jsonObject
            val months = dividendMonths(case["items"], year, month)

            val wantMonths = want["months"]!!.jsonArray.map { it.jsonObject }
            assertEquals(12, months.size, "$name: a year is twelve columns")
            assertEquals(
                wantMonths.map { it["key"]!!.jsonPrimitive.content }, months.map { it.key },
                "$name: the twelve months, oldest first",
            )
            for ((i, m) in months.withIndex()) {
                same("$name[$i]",
                    wantMonths[i]["amount"]!!.jsonPrimitive.content.toDouble(), m.amount)
            }
            same("$name.total", want["total"]!!.jsonPrimitive.content.toDouble(),
                dividendTotal(months))
        }
    }

    /* The empty months are the point: a chart of only the months
       that paid has no gaps in it, which reads as a monthly
       income where there is none. */
    @Test
    fun `the months with nothing in them are still months`() {
        val case = fixture["dividends"]!!.jsonArray.map { it.jsonObject }
            .first { it["name"]!!.jsonPrimitive.content == "sparse" }
        val months = dividendMonths(case["items"], 2026, 8)
        assertEquals(12, months.size)
        assertEquals(9, months.count { it.amount == 0.0 }, "nine of the twelve paid nothing")
        assertEquals(15.5, months.last().amount, "and August summed its two")
        assertEquals("2025-09", months.first().key)
        assertEquals("2026-08", months.last().key)
    }

    /* Counted in months rather than by subtracting days. The 31st
       of March minus one month is a date that does not exist, and
       a window crossing a year boundary is where an arithmetic
       shortcut shows up. */
    @Test
    fun `the window crosses a year without losing a month`() {
        val months = dividendMonths(null, 2026, 2)
        assertEquals("2025-03", months.first().key)
        assertEquals("2026-02", months.last().key)
        assertEquals(12, months.map { it.key }.toSet().size, "a month was repeated")

        val fromJanuary = dividendMonths(null, 2026, 1)
        assertEquals("2025-02", fromJanuary.first().key)
        assertEquals("2026-01", fromJanuary.last().key)
    }

    /* ============================================================
       4. What a stranger is shown
       ============================================================ */

    @Test
    fun `the public view survives a payload with almost nothing in it`() {
        val json = Json { ignoreUnknownKeys = true }
        val thin = json.decodeFromString(PublicAnswer.serializer(), """{"portfolio":{}}""")
        val p = requireNotNull(thin.portfolio)
        assertEquals(0, p.count)
        assertEquals(
            null, p.holdings,
            "holdings absent is not holdings empty: an admin can turn the list off, and " +
                "'none shown' and 'none held' are different sentences",
        )

        val full = json.decodeFromString(
            PublicAnswer.serializer(),
            """{"portfolio":{"at":"2026-08-22T00:00:00Z","count":2,"investedPct":91.6,
               "cashPct":8.4,"returnPct":11.8,"holdings":[
                 {"name":"Holding 1","ticker":"","weightPct":56.1,"returnPct":null},
                 {"name":"Apple Inc","ticker":"AAPL","weightPct":43.9,"returnPct":32}]}}""",
        )
        val holdings = requireNotNull(full.portfolio?.holdings)
        assertEquals(2, holdings.size)
        assertEquals(null, holdings[0].returnPct, "a return an admin chose not to show is null")
        assertEquals(32.0, holdings[1].returnPct)
    }
}
