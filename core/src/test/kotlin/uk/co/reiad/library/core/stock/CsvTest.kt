package uk.co.reiad.library.core.stock

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/* ============================================================
   The export, which outlives the page it came from.

   A spreadsheet of ratios with no caveat attached is exactly the
   artefact this tool was written not to produce, so the caveat is
   the first two rows and this asserts it stays there.

   The other half is the DISTINCTION between a metric that does
   not apply and a metric scoring nought. A spreadsheet reads an
   empty cell as missing and averages around it, and reads a nought
   as a measurement; a bank exported with elevens noughts in it
   would average to a company in distress.
   ============================================================ */
class CsvTest {

    private val words = ToolWords(
        strings = mapOf(
            Keys.DISC_BODY to Phrase(en = "What this cannot see."),
            Keys.DISC_UNITS to Phrase(en = "Figures in lakh."),
            Keys.VETOED to Phrase(en = "Overridden by a hard stop"),
            "verdict.accumulate" to Phrase(en = "Worth accumulating"),
            "verdict.avoid" to Phrase(en = "Avoid"),
        ),
    )

    private fun csv(d: Inputs = Inputs()): String =
        toCsv(analyse(d), words, WEIGHT_PRESETS.getValue("balanced"), "balanced")

    private fun section(text: String, name: String): List<String> =
        text.lines().dropWhile { it != name }.drop(1).takeWhile { it.isNotEmpty() }

    @Test
    fun `the caveat travels with the numbers`() {
        val lines = csv().lines()
        assertEquals("STOCK CHECK: reiad.co.uk/tools/stock", lines[0])
        assertTrue("What this cannot see." in lines[1], "the disclaimer is not the second row")
        assertTrue("Figures in lakh." in lines[2], "the units are not the third row")
    }

    @Test
    fun `every section the site writes is here, in the same order`() {
        val text = csv()
        val order = listOf(
            "VERDICT", "INPUTS", "PILLARS", "SCORECARD",
            "FAIR VALUE ANCHORS", "FLAGS", "PATTERNS",
        )
        var at = -1
        for (name in order) {
            val found = text.lines().indexOf(name)
            assertTrue(found > at, "$name is missing or out of order")
            at = found
        }
    }

    @Test
    fun `every input is exported, so the file can be typed back in`() {
        val rows = section(csv(), "INPUTS")
        assertEquals(fieldsOf(Inputs()).size, rows.size)
        assertTrue("price,210" in rows, "the price is not a plain number: ${rows.take(3)}")
        assertTrue("sector,pharma" in rows)
    }

    @Test
    fun `all forty-four ratios are exported, applying or not`() {
        val rows = section(csv(), "SCORECARD").drop(1)
        assertEquals(44, rows.size)
        assertTrue(rows.all { it.count { ch -> ch == ',' } >= 4 }, "a row lost a column")
    }

    /* The failure this is really watching. A bank's eleven
       inapplicable ratios exported as noughts would average to a
       company in distress in anybody's spreadsheet, and nothing
       about the file would say so. */
    @Test
    fun `a ratio that does not apply is an empty cell and never a nought`() {
        val bank = Inputs().copy(sector = "bank", car = 13.0, npl = 3.0)
        val rows = section(csv(bank), "SCORECARD").drop(1)
        val skipped = rows.filter { it.endsWith(",no") }
        assertTrue(skipped.size >= 7, "only ${skipped.size} ratios were dropped for a bank")
        for (row in skipped) {
            val cells = row.split(",")
            /* Ratio name is quoted and may hold a comma, so the
               last four are counted from the end. */
            val applies = cells[cells.size - 1]
            val score = cells[cells.size - 2]
            val value = cells[cells.size - 3]
            assertEquals("no", applies)
            assertEquals("", score, "a dropped ratio was exported with a score: $row")
            assertEquals("", value, "a dropped ratio was exported with a value: $row")
        }
    }

    @Test
    fun `a vetoed company says so in the band rather than showing its score`() {
        val z = Inputs().copy(category = "Z")
        val rows = section(csv(z), "VERDICT")
        assertTrue(
            rows.any { it.startsWith("Band,") && "hard stop" in it },
            "a vetoed company's band did not say it was overridden: $rows",
        )
    }

    @Test
    fun `a quote inside a phrase does not break the row`() {
        val awkward = ToolWords(
            strings = mapOf(Keys.DISC_BODY to Phrase(en = """He said "buy", and did not.""")),
        )
        val line = toCsv(
            analyse(Inputs()), awkward, WEIGHT_PRESETS.getValue("balanced"), "balanced",
        ).lines()[1]
        assertEquals("\"He said \"\"buy\"\", and did not.\"", line)
    }
}
