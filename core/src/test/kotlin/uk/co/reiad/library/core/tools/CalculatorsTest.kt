package uk.co.reiad.library.core.tools

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
   The five calculators against the site's own model.

   `content/calculators.fixtures.json` is what
   `shared/calculators.ts` produced for twenty-five sets of
   inputs, and every number of it is asserted here.

   ---- why these need it more than the stock check ----

   Because every number in them is PLAUSIBLE. A stock model that
   silently drops a metric still produces a verdict somebody might
   question; a compounding calculator whose loop runs one month
   short returns a sensible balance over a sensible curve, and
   nothing on the screen could tell anybody. There is no reading
   of the output that catches it. The only way to know is to
   compare with what the other implementation said.

   ---- the tolerance ----

   1e-9 relative, for the reason `ModelTest` gives: both sides are
   IEEE 754 doing the same operations in the same order, and where
   they differ it is the last bit of a division chain. Relative,
   because these run from 0.25 to ten million.
   ============================================================ */
class CalculatorsTest {

    private val fixture: JsonObject = Json
        .parseToJsonElement(
            requireNotNull(javaClass.getResourceAsStream("/fixtures/calculators.json")) {
                "fixtures/calculators.json is missing. It is generated on the website by " +
                    "scripts/export-calculator-fixtures.ts and copied here."
            }.readBytes().decodeToString(),
        ).jsonObject

    private val tools: List<JsonObject> =
        fixture["calculators"]!!.jsonArray.map { it.jsonObject }

    private fun same(what: String, want: Double, got: Double) {
        if (want.isNaN() || got.isNaN()) {
            assertTrue(want.isNaN() && got.isNaN(), "$what: said $want, this said $got")
            return
        }
        if (want.isInfinite() || got.isInfinite()) {
            assertEquals(want, got, "$what: one side is infinite and the other is not")
            return
        }
        val scale = maxOf(abs(want), abs(got), 1.0)
        assertTrue(abs(want - got) / scale < 1e-9, "$what: said $want, this said $got")
    }

    /** JSON has no Infinity, so the generator's `JSON.stringify`
        wrote `null` where the model said one. Compounding's rule
        of 72 at a rate of nought is exactly that. */
    private fun asDouble(el: kotlinx.serialization.json.JsonElement): Double {
        val p = el.jsonPrimitive
        if (p.isString) return p.content.toDouble()
        return p.content.toDoubleOrNull() ?: Double.POSITIVE_INFINITY
    }

    /* ============================================================
       1. Every number, for every case
       ============================================================ */

    @Test
    fun `every calculator agrees with the site, on every case`() {
        var compared = 0
        assertEquals(
            tools.map { it["id"]!!.jsonPrimitive.content },
            CALCULATORS.map { it.id },
            "the five calculators, in the site's own order",
        )

        for (tool in tools) {
            val id = tool["id"]!!.jsonPrimitive.content
            val calc = requireNotNull(calculatorFor(id)) { "this port has no '$id'" }

            for (case in tool["cases"]!!.jsonArray.map { it.jsonObject }) {
                val name = "$id.${case["name"]!!.jsonPrimitive.content}"
                val input = case["input"]!!.jsonObject
                    .mapValues { (_, v) -> asDouble(v) }
                val want = case["out"]!!.jsonObject
                val got = calc.run(input)

                /* The verdict FIRST: it is what a reader actually
                   reads, and a port that computes every number
                   correctly and picks the wrong sentence is worse
                   than one that is visibly broken. */
                assertEquals(
                    want["verdict"]!!.jsonPrimitive.content, got.verdict,
                    "$name: a different sentence would be printed",
                )

                val wantValues = want["values"]!!.jsonObject
                assertEquals(
                    wantValues.keys, got.values.keys,
                    "$name: this port produces a different set of numbers",
                )
                for ((key, v) in wantValues) {
                    same("$name.$key", asDouble(v), got.values.getValue(key))
                    compared += 1
                }

                val wantSeries = want["series"]!!.jsonObject
                assertEquals(wantSeries.keys, got.series.keys, "$name: the chart series")
                for ((key, arr) in wantSeries) {
                    val wanted = arr.jsonArray.map { asDouble(it) }
                    val mine = got.series.getValue(key)
                    /* Length before values. A loop that runs one
                       iteration short produces a curve that looks
                       right and ends a year early, and comparing
                       only the overlap would never see it. */
                    assertEquals(
                        wanted.size, mine.size,
                        "$name.$key: ${mine.size} points where the site drew ${wanted.size}",
                    )
                    for ((i, w) in wanted.withIndex()) {
                        same("$name.$key[$i]", w, mine[i])
                        compared += 1
                    }
                }

                val wantNotes = want["notes"]!!.jsonObject
                    .mapValues { (_, v) -> v.jsonPrimitive.content }
                assertEquals(
                    wantNotes, got.notes,
                    "$name: a different line would be printed under a figure",
                )
            }
        }
        /* Twenty-five cases produce a little over seven hundred
           numbers between them, most of them series points. The
           floor is here so that a fixture which lost a calculator,
           or a loop that stopped iterating, fails loudly rather
           than passing with nothing to compare. */
        assertEquals(25, tools.sumOf { it["cases"]!!.jsonArray.size }, "cases in the fixture")
        assertTrue(compared > 650, "only $compared numbers compared; the fixture looks thin")
    }

    /* ============================================================
       2. The branches, which are what a fixture set gets wrong
       ============================================================ */

    @Test
    fun `every case says what it is for, and every branch is reached`() {
        /* How many distinct sentences each calculator can print.
           A fixture set that reaches fewer has left one of them
           untested, and an untested branch in a calculator is the
           one a reader hits on the day it matters. */
        val branches = mapOf(
            "compounding" to 2, "sanchayapatra" to 3,
            "inflation" to 2, "emi" to 2, "position" to 3,
        )
        for (tool in tools) {
            val id = tool["id"]!!.jsonPrimitive.content
            val cases = tool["cases"]!!.jsonArray.map { it.jsonObject }
            assertTrue(cases.size >= 5, "$id has only ${cases.size} cases")

            for (case in cases) {
                assertTrue(
                    case["why"]!!.jsonPrimitive.content.length > 20,
                    "$id.${case["name"]!!.jsonPrimitive.content} does not say what it is for",
                )
            }

            val reached = cases.map { it["out"]!!.jsonObject["verdict"]!!.jsonPrimitive.content }
                .toSet()
            assertEquals(
                branches.getValue(id), reached.size,
                "$id reaches $reached, and it has ${branches.getValue(id)} things it can say",
            )
        }
    }

    /* ============================================================
       3. The fields and the formats, which the screen reads
       ============================================================ */

    @Test
    fun `every field is the site's, to the digit`() {
        for (tool in tools) {
            val id = tool["id"]!!.jsonPrimitive.content
            val calc = requireNotNull(calculatorFor(id))
            val want = tool["fields"]!!.jsonArray.map { it.jsonObject }

            assertEquals(want.size, calc.fields.size, "$id: a field went missing")
            for ((i, f) in calc.fields.withIndex()) {
                val w = want[i]
                assertEquals(w["name"]!!.jsonPrimitive.content, f.name, "$id: field $i")
                same("$id.${f.name}.min", asDouble(w["min"]!!), f.min)
                same("$id.${f.name}.max", asDouble(w["max"]!!), f.max)
                same("$id.${f.name}.step", asDouble(w["step"]!!), f.step)
                /* The default above all: it is the number a
                   reader sees before touching anything, and a
                   link that omits a field means this one. */
                same("$id.${f.name}.value", asDouble(w["value"]!!), f.value)
            }

            assertEquals(
                tool["figures"]!!.jsonArray.map { it.jsonPrimitive.content }, calc.figures,
                "$id: the headline figures",
            )
            assertEquals(
                tool["lines"]!!.jsonArray.map { it.jsonPrimitive.content }, calc.lines,
                "$id: the chart series",
            )
        }
    }

    @Test
    fun `every number knows how it is printed`() {
        val want = fixture["formats"]!!.jsonObject
            .mapValues { (_, v) -> v.jsonPrimitive.content }
        val mine = FORMATS.mapValues { (_, k) -> k.name.lowercase() }
        assertEquals(
            want, mine,
            "a value that is money on the site and a bare decimal here is a figure a " +
                "reader cannot read, and nothing about the screen would say so",
        )

        /* And nothing a calculator produces is left out of it. */
        for (calc in CALCULATORS) {
            for (name in calc.run(calc.defaults).values.keys) {
                assertTrue(name in FORMATS, "${calc.id} produces '$name' and FORMATS omits it")
            }
        }
    }

    /* ============================================================
       4. The two coercions, which are the browser's
       ============================================================ */

    @Test
    fun `a missing term is a year rather than a division by nought`() {
        /* `Number(v.years) || 1` on the site. A term of nought
           divides by zero three lines later in three of the five,
           and what a reader would see is every figure reading a
           dash at once. */
        val withNought = emi.run(emi.defaults + ("years" to 0.0))
        val withOne = emi.run(emi.defaults + ("years" to 1.0))
        assertEquals(withOne.values["emi"], withNought.values["emi"])
        assertTrue(withNought.values.getValue("emi").isFinite())
    }

    @Test
    fun `a missing amount is nought rather than nothing`() {
        val empty = compounding.run(mapOf("years" to 20.0, "rate" to 10.0))
        assertEquals(0.0, empty.values["final"])
        assertEquals(0.0, empty.values["paid"])
        /* And the growth percentage does not divide by it. */
        assertEquals(0.0, empty.values["growthPct"])
        assertEquals("", empty.notes["growth"], "there is nothing to say about no growth")
    }

    @Test
    fun `a rate of nought is an answer and not an absence`() {
        val flat = compounding.run(compounding.defaults + ("rate" to 0.0))
        assertEquals("flat", flat.verdict)
        assertTrue(
            flat.values.getValue("doubles").isInfinite(),
            "the rule of 72 at nought per cent must refuse rather than divide",
        )
        /* The money still adds up: a habit with no return is
           still a habit. */
        same("flat.paid", 50_000.0 + 5000.0 * 240, flat.values.getValue("paid"))
        same("flat.final", flat.values.getValue("paid"), flat.values.getValue("final"))
    }

    /* ============================================================
       5. What the calculators are FOR, said as a test
       ============================================================ */

    /* Position sizing is the only one that can tell somebody not
       to take a trade, and the temptation in a port is to make it
       answer anyway. */
    @Test
    fun `a stop above the entry is a refusal rather than a number`() {
        for (stop in listOf(45.0, 50.0, 4000.0)) {
            val out = position.run(position.defaults + ("stop" to stop))
            assertEquals("noStop", out.verdict, "a stop at $stop should be refused")
            assertEquals(0.0, out.values["shares"], "and no share count invented")
            assertEquals(0.0, out.values["cost"])
        }
    }

    @Test
    fun `the share count is floored, because a rule that rounds up is not one`() {
        /* 2,000 taka of risk over 70 taka a share is 28.57. */
        val out = position.run(
            position.defaults + mapOf("entry" to 4000.0, "stop" to 3930.0),
        )
        assertEquals(28.0, out.values["shares"])
        assertEquals(112_000.0, out.values["cost"])
    }

    /* Fisher rather than subtraction. At 15 against 10 the two
       differ by half a point, which is most of a fixed deposit's
       real return, and the subtraction is the version everybody
       writes first. */
    @Test
    fun `a real return is fisher rather than a subtraction`() {
        val out = inflation.run(
            inflation.defaults + mapOf("nominal" to 15.0, "inflation" to 10.0),
        )
        val real = out.values.getValue("real")
        assertTrue(abs(real - 5.0) > 0.4, "this looks like 15 minus 10: $real")
        same("fisher", (1.15 / 1.10 - 1) * 100, real)
    }
}
