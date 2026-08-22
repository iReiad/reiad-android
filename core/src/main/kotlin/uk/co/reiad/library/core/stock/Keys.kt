package uk.co.reiad.library.core.stock

/* ============================================================
   The phrase keys the stock check's screen asks for, by name.

   ---- why this is a file and not a hundred string literals ----

   The words live on the site and the screen lives here, so a key
   is the seam between two repositories that cannot see each
   other. Get one wrong and there is no error anywhere: the page
   renders, the layout is right, and the reader sees `sec.fv`
   where a heading should be.

   That is not hypothetical. Fifteen of these were guessed from
   the shape of their neighbours when this screen was first
   written, and every one of the fifteen was wrong: the site
   spells an explanatory line `sec.dragsNote` and not
   `sec.drags.why`, its fair-value section is `fair` and not `fv`,
   and its share button is `a.copyLink`. Nothing failed.

   `WordsTest` asserts every constant here against `stringKeys` in
   `content/stock.fixtures.json`, which is the site's own list of
   what it ships. So a renamed phrase is a red test rather than a
   key printed to a reader.

   The keys built from an id are NOT here and must not be: `m.roe`
   comes from the metric registry, `f.debtHeavy` from the flag the
   model raised. `WordsTest` walks those from the model itself,
   which is stronger than a list, because a metric added next year
   is covered without anybody coming back to this file.
   ============================================================ */
object Keys {

    /* ---------- the page ---------- */
    const val EYEBROW = "page.eyebrow"
    const val TITLE = "page.h1"
    const val LEDE = "page.lede"

    /* ---------- the verdict ---------- */
    const val VERDICT = "verdict.title"
    const val OUT_OF = "verdict.outOf"

    /** "Held back by the price", and the paragraph under it that
        names what the score alone would have said. The cap is
        never silent: that is the site's rule and it is the whole
        reason `earned` is carried beside `verdict`. */
    const val CAPPED = "verdict.capped"
    const val CAPPED_WHY = "verdict.cappedWhy"

    const val VETOED = "verdict.vetoed"
    const val VETOED_WHY = "verdict.vetoedWhy"

    /** The median fair value discounted by the margin a value
        buyer would want. Blank rather than invented when nothing
        could be valued. */
    const val BUY_BELOW = "verdict.buyBelow"
    const val NO_BUY_BELOW = "verdict.noBuyBelow"

    /* ---------- section headings, each with its note ---------- */
    const val SEC_PILLARS = "sec.pillars"
    const val SEC_PILLARS_NOTE = "sec.pillarsNote"
    const val SEC_FLAGS = "sec.flags"
    const val SEC_FLAGS_NOTE = "sec.flagsNote"
    const val SEC_SIGNALS = "sec.signals"
    const val SEC_SIGNALS_NOTE = "sec.signalsNote"
    const val SEC_DRAGS = "sec.drags"
    const val SEC_DRAGS_NOTE = "sec.dragsNote"
    const val SEC_FAIR = "sec.fair"
    const val SEC_FAIR_NOTE = "sec.fairNote"
    const val SEC_SHARIAH = "sec.shariah"
    const val SEC_SHARIAH_NOTE = "sec.shariahNote"
    const val SEC_SCORECARD = "sec.scorecard"
    const val SEC_SCORECARD_NOTE = "sec.scorecardNote"

    /* ---------- the weights, which are the reader's ---------- */
    const val WEIGHTS = "g.weights"
    const val WEIGHTS_NOTE = "g.weightsNote"

    /** "gives {v} pts", so a slider says what it is doing. */
    const val GIVES = "pillar.gives"
    const val NOT_COUNTED = "pillar.notCounted"

    /* ---------- fair value ---------- */
    const val FV_LOWEST = "fv.lowest"
    const val FV_MEDIAN = "fv.median"
    const val FV_HIGHEST = "fv.highest"
    const val FV_PRICE = "fv.price"
    const val FV_MOS = "fv.mos"

    /** The price is above every anchor there is, which is a
        different sentence from a negative percentage. */
    const val FV_MOS_OVER = "fv.mosOver"

    /** Four anchors disagreeing by more than three times IS the
        answer: this cannot be valued from these inputs, and a
        single number would be false precision. */
    const val FV_WIDE = "fv.wide"
    const val FV_NONE = "fv.none"

    /* ---------- the Shariah screen ---------- */
    const val SH_PASS = "sh.pass"
    const val SH_FAIL = "sh.fail"

    /** Ratio screens only. Whether the business is permissible is
        a judgement for a scholar rather than a calculator, and
        saying so is not a disclaimer, it is the honest limit of
        what arithmetic can answer. */
    const val SH_CAVEAT = "sh.caveat"

    /* ---------- what a metric says when it does not apply ---------- */
    const val NA_REASON = "na.reason"

    /* ---------- the buttons ---------- */
    const val COPY_LINK = "a.copyLink"
    const val COPIED = "a.copied"
    const val DOWNLOAD = "a.download"
    const val RESET = "a.reset"

    /* ---------- and what this page cannot see ---------- */
    const val DISC_TITLE = "disc.title"
    const val DISC_BODY = "disc.body"
    const val DISC_UNITS = "disc.units"

    /** Every one of the above, for the test that asserts they
        exist. A constant missing from here is the one gap this
        file cannot close on its own, so it is short and in the
        same order as the block above it. */
    val ALL: List<String> = listOf(
        EYEBROW, TITLE, LEDE,
        VERDICT, OUT_OF, CAPPED, CAPPED_WHY, VETOED, VETOED_WHY, BUY_BELOW, NO_BUY_BELOW,
        SEC_PILLARS, SEC_PILLARS_NOTE, SEC_FLAGS, SEC_FLAGS_NOTE,
        SEC_SIGNALS, SEC_SIGNALS_NOTE, SEC_DRAGS, SEC_DRAGS_NOTE,
        SEC_FAIR, SEC_FAIR_NOTE, SEC_SHARIAH, SEC_SHARIAH_NOTE,
        SEC_SCORECARD, SEC_SCORECARD_NOTE,
        WEIGHTS, WEIGHTS_NOTE, GIVES, NOT_COUNTED,
        FV_LOWEST, FV_MEDIAN, FV_HIGHEST, FV_PRICE, FV_MOS, FV_MOS_OVER, FV_WIDE, FV_NONE,
        SH_PASS, SH_FAIL, SH_CAVEAT,
        NA_REASON,
        COPY_LINK, COPIED, DOWNLOAD, RESET,
        DISC_TITLE, DISC_BODY, DISC_UNITS,
    )
}

/** The band a score of exactly `min` lands in, and the one just
    below it.

    The headroom line reads "4.2 points lower → Hold", so it needs
    the band on the other side of an edge rather than the band the
    score is in. Written as a lookup on the edge's own value,
    which is what the site does, so the two say the same thing
    about a score sitting exactly on 62. */
fun bandAt(min: Double): String = BANDS.firstOrNull { it.min == min }?.id ?: "avoid"

fun bandBelow(min: Double): String {
    val i = BANDS.indexOfFirst { it.min == min }
    return if (i in 0 until BANDS.size - 1) BANDS[i + 1].id else "avoid"
}

/** The median fair value, discounted by the margin a value buyer
    would want. Not a target and not a prediction: a number to
    compare the price against. NaN where nothing could be valued,
    because inventing one would be the false precision the whole
    fair-value section refuses. */
const val BUY_BELOW_DISCOUNT = 0.75

fun buyBelow(fair: Fair): Double =
    if (fair.mid.isFinite()) fair.mid * BUY_BELOW_DISCOUNT else Double.NaN

/** Past this the anchors are not saying one thing. The site's own
    threshold, and the sentence it prints is `fv.wide`. */
const val SPREAD_TOO_WIDE = 3.0
