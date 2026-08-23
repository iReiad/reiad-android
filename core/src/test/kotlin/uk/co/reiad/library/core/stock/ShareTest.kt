package uk.co.reiad.library.core.stock

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/* ============================================================
   A link opens the same numbers wherever it is opened.

   The stock check has shared analyses as a query string since
   before there were accounts, which is why `public.scenarios`
   stores one. So a saved check made on a phone has to open on the
   site and the other way round, and this is what says it does.

   ---- the failure this is really watching ----

   A shared link carries only what DIFFERS from a default, so
   every default is a number the link does not say and both sides
   have to already know. One of the fifty-six drifting is a link
   that opens a DIFFERENT COMPANY on the other side, correctly
   rendered, with no error on either. That is the worst shape a
   bug can have here, and it is the first test below.
   ============================================================ */
class ShareTest {

    private val fixture: JsonObject = Json
        .parseToJsonElement(
            requireNotNull(javaClass.getResourceAsStream("/fixtures/stock.json")) {
                "fixtures/stock.json is missing; the website generates it."
            }.readBytes().decodeToString(),
        ).jsonObject

    private val defaults: JsonObject = fixture["defaults"]!!.jsonObject

    /* ============================================================
       1. The defaults, which are what a link leaves out
       ============================================================ */

    @Test
    fun `every default is the site's, to the digit`() {
        val mine = fieldsOf(Inputs())

        assertEquals(
            defaults.keys, mine.keys,
            "the fields this port has and the site's are not the same set. A field only " +
                "the site has is one a shared link will drop; a field only this has is " +
                "one the site will never fill in.",
        )
        for ((key, value) in defaults) {
            val want = value.jsonPrimitive.content
            val got = jsNumber(mine.getValue(key))
            assertEquals(
                want, got,
                "the default for '$key' is $want on the site and $got here, so a link " +
                    "that does not mention it opens two different companies",
            )
        }
        assertTrue(defaults.size > 50, "only ${defaults.size} defaults; the fixture looks thin")
    }

    /* ============================================================
       2. Round trips
       ============================================================ */

    @Test
    fun `an untouched check needs no link at all`() {
        assertEquals("", shareQuery(Inputs()))
    }

    @Test
    fun `a link carries what was changed and nothing else`() {
        val q = shareQuery(Inputs().copy(price = 340.0, sector = "bank", npl = 4.5))
        assertEquals("price=340&sector=bank&npl=4.5", q)
    }

    @Test
    fun `a whole check survives the trip`() {
        val d = Inputs().copy(
            price = 87.5, shares = 640.0, sector = "textile", category = "B",
            revenue = 41000.0, netIncome = -1200.0, cfo = 900.0,
            dps = 0.0, yearsPaid = 0.0, riskFree = 11.25,
        )
        val back = readShare(shareQuery(d))
        assertEquals(d, back.inputs, "a check did not survive being sent to somebody")

        /* And the analysis it opens to is the same one, which is
           the property a reader actually cares about. */
        assertEquals(analyse(d).score, analyse(back.inputs).score)
        assertEquals(analyse(d).verdict.id, analyse(back.inputs).verdict.id)
    }

    /* The seven shapes the model has a branch for, each sent to
       somebody and opened. A bank whose sector did not survive is
       a bank scored as a manufacturer, which is the one failure
       the financial path exists to prevent. */
    @Test
    fun `every company the model has a branch for survives the trip`() {
        val cases = fixture["cases"]!!.jsonArray.map { it.jsonObject }
        assertEquals(7, cases.size)
        for (case in cases) {
            val name = case["name"]!!.jsonPrimitive.content
            var d = Inputs()
            for ((key, value) in case["input"]!!.jsonObject) {
                d = readShare("$key=${encodeQuery(value.jsonPrimitive.content)}").inputs
                    .let { patched -> merge(d, patched, key) }
            }
            val back = readShare(shareQuery(d)).inputs
            assertEquals(d, back, "$name did not survive being shared")
            assertEquals(
                analyse(d).verdict.id, analyse(back).verdict.id,
                "$name opens to a different verdict on the other side",
            )
        }
    }

    /** One field of `patched` on to `base`, by name.

        Reading each field through `readShare` rather than through
        a fifty-six-arm `when` of its own: the point of this test
        is the encoder, so building its input with a second copy
        of the decoder would be asserting the two agree with
        themselves. */
    private fun merge(base: Inputs, patched: Inputs, key: String): Inputs {
        val want = fieldsOf(patched)[key] ?: return base
        val q = shareQuery(base) .let { if (it.isEmpty()) "" else "$it&" }
        return readShare("$q$key=${encodeQuery(want)}").inputs
    }

    /* ============================================================
       3. The weights, which are the reader's half of the link
       ============================================================ */

    @Test
    fun `a preset travels by name and hand-set weights travel by value`() {
        val income = WEIGHT_PRESETS.getValue("income")
        val byName = shareQuery(Inputs(), income, style = "income")
        assertTrue("style=income" in byName)

        val back = readShare(byName)
        assertEquals("income", back.style)
        assertEquals(income, back.weights)
    }

    @Test
    fun `a link with weights of its own is not any of the presets`() {
        val mine = WEIGHT_PRESETS.getValue("balanced") + ("momentum" to 40.0)
        val q = shareQuery(Inputs(), mine)
        assertEquals("w.momentum=40", q)

        val back = readShare(q)
        assertEquals(
            "custom", back.style,
            "a chip showing as chosen would be claiming weights the page is not using",
        )
        assertEquals(40.0, back.weights["momentum"])
        assertEquals(20.0, back.weights["value"], "the rest should still be balanced")
    }

    @Test
    fun `weights equal to balanced are not written out`() {
        assertEquals("", shareQuery(Inputs(), WEIGHT_PRESETS.getValue("balanced")))
    }

    /* ============================================================
       4. A link from a stranger
       ============================================================ */

    @Test
    fun `a field this build does not know is ignored rather than refused`() {
        val back = readShare("price=340&somethingNew=7&sector=bank")
        assertEquals(340.0, back.inputs.price)
        assertEquals("bank", back.inputs.sector)
    }

    /* A number in a link has to be read the SAME WAY on both
       sides, and neither language's default parser is that
       agreement: JavaScript's `Number()` reads "0x2" as two and
       "" as nought, and Java's `parseDouble` reads "4d" as four.
       What both read alike is a sign, digits and an exponent. */
    @Test
    fun `a number is read the way both sides read it`() {
        assertEquals(1.5, readShare("price=1.5").inputs.price)
        assertEquals(-4.0, readShare("price=-4").inputs.price)
        assertEquals(3.0, readShare("price=%2B3").inputs.price, "a leading + is a sign")
        assertEquals(
            95000.0, readShare("revenue=9.5e4").inputs.revenue,
            "scientific notation is a reasonable thing to type into a revenue field, " +
                "and JavaScript reads it, so refusing it would BE the divergence",
        )
        assertEquals(4.0, readShare("price=+4+").inputs.price, "Number() trims, so this does")
    }

    /* Each of these is a decision. An empty parameter is not the
       number nought; an infinity carries through every ratio it
       touches and comes out the far end as a verdict; and a hex
       literal is not something anybody types into a share link. */
    @Test
    fun `a value that is not plainly a number leaves the field alone`() {
        for (odd in listOf("", "0x2", "4d", "4f", "Infinity", "-Infinity", "NaN", "abc",
                "1,5", "1.2.3", "--4", "1e", "e4")) {
            assertEquals(
                Inputs().price, readShare("price=${encodeQuery(odd)}").inputs.price,
                "'$odd' was read as a price",
            )
        }
    }

    @Test
    fun `a language only travels when it is one of the two`() {
        assertEquals("bn", readShare("lang=bn").lang)
        assertEquals("en", readShare("lang=en").lang)
        assertEquals(null, readShare("lang=de").lang, "a third language is not a thing here")
        assertEquals(null, readShare("").lang)
    }

    @Test
    fun `an empty or malformed query is an untouched check`() {
        for (q in listOf("", "?", "&&", "=", "price", "?&=&")) {
            assertEquals(Inputs(), readShare(q).inputs, "'$q' should have changed nothing")
        }
    }

    /* ============================================================
       5. The encoding itself, which is form encoding and not URI
          encoding
       ============================================================ */

    @Test
    fun `a space is a plus, because URLSearchParams says so`() {
        assertEquals("a+b", encodeQuery("a b"))
        assertEquals("a b", decodeQuery("a+b"))
        assertEquals("a b", decodeQuery("a%20b"))
    }

    @Test
    fun `bangla survives, as its own bytes`() {
        val bn = "টাকা"
        val there = encodeQuery(bn)
        assertTrue(there.all { it.code < 128 }, "the encoding left non-ASCII in: $there")
        assertEquals(bn, decodeQuery(there))
    }

    @Test
    fun `a percent that is not an escape is left as itself`() {
        assertEquals("100%", decodeQuery("100%"))
        assertEquals("50%x", decodeQuery("50%x"))
    }

    @Test
    fun `the link is the tool's own address`() {
        assertTrue(shareLink(Inputs()).endsWith("/tools/stock.html"))
        assertTrue(shareLink(Inputs().copy(price = 5.0)).endsWith("/tools/stock.html?price=5"))
    }
}
