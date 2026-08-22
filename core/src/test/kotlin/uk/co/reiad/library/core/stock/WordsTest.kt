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
   Printing a number the way the site prints it.

   The site goes through `Intl.NumberFormat`, and this port
   deliberately does not go through Android's ICU: the two are
   different versions of the same library and a grouping that
   changed between them would print one number two ways on two
   devices for no reason anybody could ever find.

   So the site's output is written down in
   `content/stock.fixtures.json` and asserted here, 538 strings of
   it. The one that matters most is the SOUTH ASIAN GROUPING:
   Bangla groups the last three digits and then twos, so ten lakh
   is `১০,০০,০০০`. An app printing `১,০০,০০,০০০` where the site
   prints `১,২৩,৪৫,৬৭৮` is wrong in a way a reader notices
   instantly and cannot explain.
   ============================================================ */
class WordsTest {

    private val fixture: JsonObject = Json
        .parseToJsonElement(
            requireNotNull(javaClass.getResourceAsStream("/fixtures/stock.json")) {
                "fixtures/stock.json is missing; the website generates it."
            }.readBytes().decodeToString(),
        ).jsonObject

    private val formats: JsonObject = fixture["formats"]!!.jsonObject

    /** The words, with just enough in them for `fmtLakh`, which
        is the one formatter that reads a phrase. */
    private val words = ToolWords(
        strings = mapOf("t.crore" to Phrase(en = "crore", bn = "কোটি")),
    )

    @Test
    fun `every number prints the way the site prints it`() {
        var compared = 0
        for (lang in listOf("en", "bn")) {
            val want = formats[lang]!!.jsonObject
            for ((key, value) in want) {
                val expected = value.jsonPrimitive.content
                val parts = key.split(":")
                val v = if (parts.last() == "nan") Double.NaN else parts.last().toDouble()
                val got = when (parts[0]) {
                    "num0" -> fmtNum(v, lang, 0)
                    "num1" -> fmtNum(v, lang, 1)
                    "num2" -> fmtNum(v, lang, 2)
                    "int" -> fmtInt(v, lang)
                    "tk" -> fmtTk(v, lang)
                    "lakh" -> fmtLakh(v, words, lang)
                    "value" -> fmtValue(v, parts[1], lang)
                    else -> error("the fixture holds a formatter '$key' this port has none for")
                }
                assertEquals(expected, got, "$lang $key")
                compared += 1
            }
        }
        assertTrue(compared > 500, "only $compared strings compared; the fixture looks thin")
    }

    /* The grouping on its own, in case the fixture is ever
       regenerated on a machine whose ICU has changed. Then this
       says which of the two moved. */
    @Test
    fun `bangla groups in the south asian way`() {
        assertEquals("১০,০০,০০০", fmtInt(1_000_000.0, "bn"))
        assertEquals("১,২৩,৪৫,৬৭৮", fmtInt(12_345_678.0, "bn"))
        assertEquals("১,০০০", fmtInt(1000.0, "bn"))
        assertEquals("৯৯৯", fmtInt(999.0, "bn"))
        assertEquals("1,000,000", fmtInt(1_000_000.0, "en"))
        assertEquals("12,345,678", fmtInt(12_345_678.0, "en"))
    }

    /* ---------- the words themselves ---------- */

    @Test
    fun `a phrase comes back in the language asked for`() {
        val w = ToolWords(
            strings = mapOf("verdict.buy" to Phrase(en = "Strong case to buy", bn = "কেনার জোরালো যুক্তি আছে")),
        )
        assertEquals("Strong case to buy", w.t("verdict.buy", "en"))
        assertEquals("কেনার জোরালো যুক্তি আছে", w.t("verdict.buy", "bn"))
    }

    /* This table arrives over a wire from a deploy that may be
       newer than this build, so a key it has never heard of and a
       half-filled phrase both have to answer with something a
       reader can act on. A key is a clue; an empty box is not. */
    @Test
    fun `a phrase this build has never heard of prints its own key`() {
        val w = ToolWords(strings = mapOf("only.en" to Phrase(en = "English only")))
        assertEquals("nothing.here", w.t("nothing.here", "bn"))
        assertEquals("English only", w.t("only.en", "bn"), "a missing Bangla half falls back")
        assertTrue(!w.has("nothing.here"))
    }

    @Test
    fun `the numbers inside a sentence are filled in`() {
        val w = ToolWords(
            strings = mapOf(
                "flag.debtHeavy" to Phrase(en = "Net debt is {x} times EBITDA", bn = "ঋণ {x} গুণ"),
            ),
        )
        assertEquals(
            "Net debt is 6.20× times EBITDA",
            w.t("flag.debtHeavy", "en", mapOf("x" to fmtValue(6.2, "x"))),
        )
    }

    /* ============================================================
       Every id this app renders has a sentence to render

       The words and the model are two files and nothing else
       compares them. Add a metric, a flag or a signal to the model
       and forget the phrase, and what a reader sees is
       `f.debtHeavy` where an explanation should be: on a page
       whose entire argument is that it shows its own reasoning,
       that is the worst thing it can print.

       `stringKeys` in the fixture is the site's own list of key
       names, so this compares the model in `core` against the
       words in `shared/` without shipping 52KB of Bangla into a
       test resource.
       ============================================================ */

    private val keys: Set<String> =
        fixture["stringKeys"]!!.jsonArray.map { it.jsonPrimitive.content }.toSet()

    private fun needs(what: String, vararg wanted: String) {
        val missing = wanted.filter { it !in keys }
        assertTrue(
            missing.isEmpty(),
            "$what has no phrase: $missing. A reader would see the key itself.",
        )
    }

    @Test
    fun `every metric has a name and an explanation`() {
        assertEquals(44, METRICS.size)
        for (m in METRICS) needs("the metric ${m.id}", "m.${m.id}", "m.${m.id}.why")
    }

    /* The keys the screen names for itself, as opposed to the
       ones it derives from an id. Fifteen of these were guessed
       from the shape of their neighbours when the screen was
       first written and every one of the fifteen was wrong, with
       nothing failing anywhere: the page rendered and the reader
       saw `sec.fv` where a heading should be. */
    @Test
    fun `every key the screen names for itself exists`() {
        assertTrue(Keys.ALL.size > 40, "only ${Keys.ALL.size} keys listed; ALL looks short")
        assertEquals(
            Keys.ALL.size, Keys.ALL.toSet().size,
            "a key is listed twice in Keys.ALL",
        )
        for (key in Keys.ALL) needs("the screen's '$key'", key)
    }

    /* And the families it builds from an id, which are the other
       half. Walked from the model rather than listed, so a sector
       or a field added next year is covered without anybody coming
       back to this file. */
    @Test
    fun `every field, group, sector and anchor the screen names has a label`() {
        for (f in FIELDS) needs("the field ${f.id}", "i.${f.id}")
        for (g in GROUPS) needs("the group $g", "g.$g")
        for (sector in SECTORS.keys) needs("the sector $sector", "sector.$sector")

        /* The fair-value anchors, from a company that produces all
           five, so this is the model's list rather than one typed
           here. */
        val fair = fairValue(Facts.of(Inputs()))
        assertEquals(5, fair.anchors.size, "the default company should build all five anchors")
        for (a in fair.anchors) needs("the anchor ${a.id}", "fv.${a.id}")

        for (test in shariahScreen(Facts.of(Inputs())).tests) {
            needs("the screen ${test.id}", "sh.${test.id}")
        }
        /* `fmtLakh` reads this one, so it is not decoration. */
        needs("the crore unit", "t.crore")
    }

    @Test
    fun `every pillar and every grade has a name`() {
        for (p in PILLARS) needs("the pillar $p", "pillar.$p")
        for (g in listOf("strong", "good", "fair", "weak", "poor", "na")) {
            needs("the grade $g", "grade.$g")
        }
        for (b in BANDS) needs("the verdict ${b.id}", "verdict.${b.id}", "verdict.${b.id}.why")
        for (s in WEIGHT_PRESETS.keys) needs("the preset $s", "style.$s")
    }

    /* Driven off the MODEL rather than off a list typed here, so a
       flag added to `checkFlags` is covered without anybody
       remembering to come back. Seven companies is enough to fire
       most of them; the ones that need a shape the fixtures do not
       have are named, and naming them is the point: a list that
       says which are untested is worth more than a number. */
    @Test
    fun `every flag and signal the model can fire has a sentence`() {
        val fired = mutableSetOf<String>()
        val fires = mutableSetOf<String>()
        for (case in fixture["cases"]!!.jsonArray.map { it.jsonObject }) {
            val f = Facts.of(inputsOf(case["input"]!!.jsonObject))
            checkFlags(f).forEach { fired += it.id }
            signals(f, scorePillars(scoreMetrics(f))).forEach { fires += it.id }
        }
        assertTrue(fired.size >= 8, "only ${fired.size} distinct flags fired across seven cases")

        for (id in fired) needs("the flag $id", "f.$id")
        for (id in fires) needs("the signal $id", "s.$id", "s.$id.why")

        /* And the whole vocabulary, whether or not a fixture
           company happens to trip it. These two lists ARE the
           model's, read off the strings the site ships, so a flag
           renamed on one side and not the other fails here. */
        val flagKeys = keys.filter { it.startsWith("f.") }.map { it.removePrefix("f.") }
        assertEquals(19, flagKeys.size, "the site ships ${flagKeys.size} flag sentences")
        val signalKeys = keys.filter { it.startsWith("s.") && !it.endsWith(".why") }
            .map { it.removePrefix("s.") }
        assertEquals(15, signalKeys.size)
    }

    /** The fixture's input patch, as `Inputs`, through the share
        decoder: this file is about words, so building an `Inputs`
        with a third fifty-six-arm `when` would be three copies of
        one thing. */
    private fun inputsOf(patch: JsonObject): Inputs {
        val q = patch.entries.joinToString("&") { (k, v) ->
            "${encodeQuery(k)}=${encodeQuery(v.jsonPrimitive.content)}"
        }
        return readShare(q).inputs
    }
}
