package uk.co.reiad.library.core.stock

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/* ============================================================
   A saved check opens with the numbers it was saved with.

   `public.scenarios` stores the stock check's QUERY STRING
   rather than a blob of the fifty-six fields, because that is
   the format the check has shared analyses in since it was
   written and there is exactly one encoder for it. This asserts
   the property that makes that safe: what goes in comes out.

   `ShareTest` already holds the encoder byte-for-byte against
   the site's. This holds the ROUND TRIP, which is a different
   question and the one a reader asks: a check saved on a laptop
   opens on a phone with the same figures in the same boxes.
   ============================================================ */
class ScenarioTest {

    /** A company that is nothing like the defaults, because a
        round trip through a format that only carries what DIFFERS
        is a round trip that passes trivially on a default. */
    private val filled = Inputs(
        sector = "general",
        price = 118.4,
        shares = 44.2,
        high52 = 141.0,
        low52 = 96.5,
        revenue = 4820.0,
        netIncome = 403.5,
        totalAssets = 9310.0,
        totalDebt = 1440.0,
        cash = 288.0,
    )

    @Test fun everyFigureSurvivesTheTrip() {
        val query = shareQuery(filled, WEIGHT_PRESETS.getValue("value"), style = "value", lang = "bn")
        val back = readShare(query)

        assertEquals(filled, back.inputs, "every field, not the ones a test remembers to name")
        assertEquals("value", back.style)
        assertEquals("bn", back.lang)
    }

    /** And the whole analysis, not only the fields.

        The point of storing inputs rather than an answer is that
        the answer is recomputed, so the test that matters is that
        the recomputed one is the same. */
    @Test fun theVerdictIsTheSameOnTheOtherSide() {
        val weights = WEIGHT_PRESETS.getValue("balanced")
        val here = analyse(filled, weights)
        val back = readShare(shareQuery(filled, weights, style = "balanced", lang = null))
        val there = analyse(back.inputs, back.weights)

        assertEquals(here.verdict.id, there.verdict.id)
        assertEquals(here.score, there.score)
        assertEquals(here.vetoed, there.vetoed)
    }

    /** The summary line is one line and it is in ENGLISH.

        Stored once and read for ever: a summary that remembers
        which language somebody happened to be reading in on the
        day is a list that is half in each. */
    @Test fun theSummaryIsOneEnglishLine() {
        val words = ToolWords(
            langs = listOf("en", "bn"),
            strings = mapOf(
                "verdict.accumulate" to Phrase("Worth accumulating", "জমানোর মতো"),
                "verdict.hold" to Phrase("Hold", "ধরে রাখুন"),
                "verdict.buy" to Phrase("A clear buy", "স্পষ্ট কেনা"),
                "verdict.trim" to Phrase("Trim", "কমান"),
                "verdict.sell" to Phrase("Sell", "বেচুন"),
                "verdict.vetoed" to Phrase("Vetoed", "বাতিল"),
            ),
        )
        val a = analyse(filled, WEIGHT_PRESETS.getValue("balanced"))
        val line = summarise(a, words)

        assertTrue(" · " in line, "the summary is a score and a band: '$line'")
        val score = line.substringBefore(" · ")
        assertTrue(
            Regex("""^-?\d+\.\d$""").matches(score) || score == "no score",
            "the score should be one decimal or 'no score', and is '$score'",
        )
        val band = line.substringAfter(" · ")
        assertTrue(
            band.all { it.code < 0x0980 || it.code > 0x09FF },
            "the summary carried Bangla, so it remembers a language it should not: '$line'",
        )
    }

    /** An empty query is a check nobody can open, and it must
        come back as the defaults rather than throwing. A row can
        be written by a version that stored something else. */
    @Test fun anEmptyQueryIsTheDefaults() {
        val back = readShare("")
        assertEquals(Inputs(), back.inputs, "an empty query is the defaults, whole")
    }
}
