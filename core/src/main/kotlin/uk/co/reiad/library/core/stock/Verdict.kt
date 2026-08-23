package uk.co.reiad.library.core.stock

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/* ============================================================
   Judgement: from forty-four numbers to one word, and every
   reason it could be wrong printed beside it.

   `aab/tools/stock.model.js` from `scoreMetrics` down is the
   original. `ModelTest` asserts this against what it said for
   seven companies, which is the only thing keeping two
   implementations of a model this size in step.
   ============================================================ */

/** One metric, scored. `score` is null where the metric does not
    apply, which is NOT the same as a score of zero and is the
    distinction the whole financial path rests on. */
data class Scored(
    val id: String,
    val pillar: String,
    val w: Double,
    val fmt: String,
    val hi: Boolean,
    val value: Double,
    val raw: Double,
    val score: Double?,
    val grade: String,
    val na: Boolean,
)

fun scoreMetrics(f: Facts): List<Scored> = METRICS.map { m ->
    val na = m.na(f)
    val value = if (na) Double.NaN else m.get(f)
    val score = if (na) null else band(value, m.anchors)
    Scored(
        id = m.id, pillar = m.pillar, w = m.w, fmt = m.fmt, hi = m.hi,
        value = value,
        /* Evaluated even when the metric does not apply, exactly
           as the site does: a bank's P/E is still a number worth
           printing beside "not scored here". */
        raw = m.raw?.invoke(f) ?: value,
        score = score?.takeIf { it.isFinite() },
        grade = grade(score),
        na = na || score == null || !score.isFinite(),
    )
}

/** A pillar, and how much of it could be asked.

    `n of of`: eight of eight for a manufacturer's health, three
    of eight for a bank's. The page shows both, because a pillar
    scored on three metrics is a weaker statement than the same
    number scored on eight and the reader deserves to see which
    they are looking at. */
data class Pillar(val score: Double?, val n: Int, val of: Int)

/** Weighted mean of the metrics that apply. A pillar with nothing
    applicable is null, and the composite redistributes its weight
    rather than scoring it zero. */
fun scorePillars(scored: List<Scored>): Map<String, Pillar> {
    val out = LinkedHashMap<String, Pillar>()
    for (p in PILLARS) {
        val all = scored.filter { it.pillar == p }
        val live = all.filter { !it.na }
        val wsum = live.sumOf { it.w }
        out[p] = if (wsum > 0) {
            Pillar(live.sumOf { it.score!! * it.w } / wsum, live.size, all.size)
        } else {
            Pillar(null, 0, all.size)
        }
    }
    return out
}

fun composite(pillars: Map<String, Pillar>, weights: Weights): Double? {
    var num = 0.0
    var den = 0.0
    for (p in PILLARS) {
        val s = pillars[p]?.score ?: continue
        val w = weights[p] ?: 0.0
        if (!s.isFinite() || w <= 0) continue
        num += s * w
        den += w
    }
    return if (den > 0) num / den else null
}

/* ============================================================
   VETOES AND FLAGS

   A veto is a fact no amount of cheapness redeems, and it
   overrides the composite outright. There are only three,
   deliberately: negative equity, Z category, and a company losing
   money AND burning cash at the same time. Everything else that
   looks bad is a flag: loud, listed, explained, and left for the
   reader to weigh.

   The short list is the point. Vetoes are where a tool like this
   does real damage if it is trigger-happy, and plenty of good
   companies have one terrible ratio.
   ============================================================ */

/** `level` is one of veto, bad, warn, info. `vars` are the
    numbers the sentence interpolates, under the site's own
    names, so one translation table serves both. */
data class Flag(val id: String, val level: String, val vars: Map<String, Double> = emptyMap())

fun checkFlags(f: Facts): List<Flag> {
    val d = f.d
    val out = mutableListOf<Flag>()
    fun push(id: String, level: String, vars: Map<String, Double> = emptyMap()) {
        out += Flag(id, level, vars)
    }

    /* --- vetoes --- */
    if (d.category == "Z") push("vetoZ", "veto")
    if (d.equity <= 0) push("vetoEquity", "veto", mapOf("equity" to d.equity))
    if (f["eps"] <= 0 && d.cfo <= 0) {
        push("vetoBurn", "veto", mapOf("eps" to f["eps"], "cfo" to d.cfo))
    }

    /* --- serious --- */
    if (f["eps"] <= 0 && d.cfo > 0) push("loss", "bad", mapOf("eps" to f["eps"]))
    if (f["interestCover"].isFinite() && f["interestCover"] < 1) {
        push("cannotCoverInterest", "bad", mapOf("cover" to f["interestCover"]))
    }
    if (d.netIncome > 0 && d.cfo < 0) push("profitNoCash", "bad", mapOf("cfo" to d.cfo))
    if (f["netDebtEbitda"].isFinite() && f["netDebtEbitda"] > 5) {
        push("debtHeavy", "bad", mapOf("x" to f["netDebtEbitda"]))
    }
    if (f["altmanZ"].isFinite() && f["altmanZ"] < 4.35) {
        push("altmanDistress", "bad", mapOf("z" to f["altmanZ"]))
    }
    if (f.isFinancial && d.npl > 8) push("nplHigh", "bad", mapOf("npl" to d.npl))

    /* --- worth knowing --- */
    if (f["payout"].isFinite() && f["payout"] > 100) {
        push("payoutOver", "warn", mapOf("payout" to f["payout"]))
    }
    if (f["accruals"].isFinite() && f["accruals"] > 0.1) {
        push("accrualGap", "warn", mapOf("a" to f["accruals"]))
    }
    if (d.freeFloat > 0 && d.freeFloat < 15) push("thinFloat", "warn", mapOf("f" to d.freeFloat))
    if (d.turnover > 0 && d.turnover < 5) push("illiquid", "warn", mapOf("t" to d.turnover))
    if (f["pb"].isFinite() && f["pb"] > 3 && f["roe"].isFinite() && f["roe"] < d.riskFree) {
        push("payingForNothing", "warn", mapOf("pb" to f["pb"], "roe" to f["roe"]))
    }
    if (f["vsMa200"].isFinite() && f["vsMa200"] < -30) {
        push("fallingKnife", "warn", mapOf("x" to f["vsMa200"]))
    }
    if (d.yearsPaid == 0.0) push("noDividend", "warn")
    if (d.category == "B") push("categoryB", "warn")
    if (d.category == "N") push("categoryN", "info")
    if (f["fcfCoverDiv"].isFinite() && f["fcfCoverDiv"] < 1 && d.dps > 0) {
        push("dividendFromDebt", "warn", mapOf("x" to f["fcfCoverDiv"]))
    }

    return out
}

/* ============================================================
   COMBINED SIGNALS

   The patterns worth naming are the ones no single ratio shows.
   A low P/E on its own is not information; a low P/E next to
   falling earnings and rising debt is the whole story, and it has
   a name. Each fires only when every one of its conditions holds.
   ============================================================ */

data class Signal(val id: String, val tone: String)

fun signals(f: Facts, pillars: Map<String, Pillar>): List<Signal> {
    val d = f.d
    val out = mutableListOf<Signal>()
    fun fire(id: String, tone: String) { out += Signal(id, tone) }

    val cheapPE = f["peRel"].isFinite() && f["peRel"] < 0.8
    val shrinking = f["niGrowth"].isFinite() && f["niGrowth"] < -5
    val levered = f["netDebtEbitda"].isFinite() && f["netDebtEbitda"] > 3

    if (cheapPE && shrinking && levered) fire("cheapForReason", "bad")

    if (f["roe"].isFinite() && f["roe"] > 18 &&
        f["debtEquity"].isFinite() && f["debtEquity"] < 0.6 &&
        f["peRel"].isFinite() && f["peRel"] <= 1.05
    ) fire("qualityFairPrice", "good")

    if (f["pb"].isFinite() && f["pb"] < 0.8 &&
        f["roe"].isFinite() && f["roe"] < d.riskFree && d.dps == 0.0
    ) fire("valueTrap", "bad")

    if (f["payout"].isFinite() && f["payout"] > 90 &&
        f["divCover"].isFinite() && f["divCover"] < 1.2
    ) fire("dividendAtRisk", "bad")

    if (f["peg"].isFinite() && f["peg"] > 2.5 &&
        f["peRel"].isFinite() && f["peRel"] > 1.4
    ) fire("growthPricedIn", "warn")

    if (f["altmanZ"].isFinite() && f["altmanZ"] < 4.35 &&
        f["interestCover"].isFinite() && f["interestCover"] < 2
    ) fire("balanceSheetStress", "bad")

    if (f["cashConversion"].isFinite() && f["cashConversion"] < 0.6 &&
        f["accruals"].isFinite() && f["accruals"] > 0.06
    ) fire("earningsQualityGap", "bad")

    if (d.freeFloat > 0 && d.freeFloat < 20 && d.turnover > 0 && d.turnover < 15) {
        fire("thinlyTraded", "warn")
    }

    if (f.f.tested >= 7 && f.f.score >= 7 && f["pb"].isFinite() && f["pb"] < 1.2) {
        fire("turnaround", "good")
    }

    if (f["earningsYieldSpread"].isFinite() && f["earningsYieldSpread"] > 0 &&
        f["yieldSpread"].isFinite() && f["yieldSpread"] > 0
    ) fire("beatsSafe", "good")

    if (f["divYield"].isFinite() && f["divYield"] < d.fdr &&
        f["earningsYield"].isFinite() && f["earningsYield"] < d.riskFree
    ) fire("losesToSafe", "warn")

    val value = pillars["value"]?.score
    if (f["range52"].isFinite() && f["range52"] > 80 && value != null && value < 35) {
        fire("momentumVsFundamentals", "warn")
    }

    if (f["vsMa200"].isFinite() && f["vsMa200"] < -25 && f.f.tested >= 7 && f.f.score <= 3) {
        fire("fallingWithReason", "bad")
    }

    if (f["assetTurnover"].isFinite() && f["assetTurnover"] < 0.5 &&
        f["netMargin"].isFinite() && f["netMargin"] < 6 && d.capex > d.cfo * 0.6
    ) fire("capitalHungry", "warn")

    if (f["roe"].isFinite() && f["roe"] > 15 &&
        f["dupontLeverage"].isFinite() && f["dupontLeverage"] > 3
    ) fire("borrowedReturn", "warn")

    return out
}

/* ============================================================
   FAIR VALUE, TRIANGULATED

   Four or five anchors, none authoritative, deliberately shown as
   a range rather than one number. A DCF here would be false
   precision: that belongs in the portfolio, where there is room
   to build the WACC properly. What is useful here is the SPREAD.
   When all of them land above the price there is something worth
   a second look, and when they scatter across a 4× range the
   honest answer is that this cannot be valued from these inputs.
   ============================================================ */

data class Anchor(val id: String, val value: Double)

data class Fair(
    val anchors: List<Anchor>,
    val low: Double,
    val mid: Double,
    val high: Double,
    val marginOfSafety: Double,
    val spread: Double,
)

fun fairValue(f: Facts): Fair {
    val d = f.d
    val anchors = mutableListOf<Anchor>()
    val req = max(d.riskFree + 3, 8.0) / 100          // required return, roughly

    if (f["eps"] > 0 && d.sectorPE > 0) anchors += Anchor("sectorPe", f["eps"] * d.sectorPE)
    if (f["bvps"] > 0 && d.sectorPB > 0) anchors += Anchor("sectorPb", f["bvps"] * d.sectorPB)

    /* Graham's number: sqrt(22.5 × EPS × BVPS), which is "P/E ×
       P/B should not exceed 22.5" rearranged. Old, blunt, and
       still a useful floor-ish reference for a defensive buyer. */
    if (f["eps"] > 0 && f["bvps"] > 0) {
        anchors += Anchor("graham", sqrt(22.5 * f["eps"] * f["bvps"]))
    }

    /* Gordon growth on the dividend, only where one is paid and
       the growth assumption stays under the discount rate. Growth
       is capped well below `req`: a payer growing AT the discount
       rate produces an infinite value, which is a maths artefact
       and not a valuation. */
    if (d.dps > 0) {
        /* The site writes `(r.epsCagr3y || 0)`, and NaN is falsy
           in JavaScript, so a company with no prior year discounts
           at zero growth rather than producing NaN. Written out
           because Kotlin has no such coercion and the silent
           version of this is a fair value that vanishes. */
        val cagr3y = f["epsCagr3y"]
        val g = min(max((if (cagr3y.isFinite()) cagr3y else 0.0) / 100, 0.0), req - 0.02)
        if (req - g > 0.01) anchors += Anchor("ddm", (d.dps * (1 + g)) / (req - g))
    }

    /* Earnings power: capitalise this year's EPS at the required
       return and assume no growth at all. The most pessimistic of
       them, and the one that asks what happens if nothing
       improves. */
    if (f["eps"] > 0) anchors += Anchor("earningsPower", f["eps"] / req)

    val values = anchors.map { it.value }.filter { it.isFinite() && it > 0 }.sorted()
    if (values.isEmpty()) {
        return Fair(anchors, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN)
    }
    val mid = if (values.size % 2 == 1) {
        values[(values.size - 1) / 2]
    } else {
        (values[values.size / 2 - 1] + values[values.size / 2]) / 2
    }

    return Fair(
        anchors = anchors,
        low = values.first(),
        mid = mid,
        high = values.last(),
        marginOfSafety = ((mid - d.price) / mid) * 100,
        spread = values.last() / values.first(),
    )
}

/* ============================================================
   SHARIAH SCREEN

   The DSES exists and a large number of Bangladeshi investors
   will only buy from it, so a tool that ignores the question is
   ignoring them. These are the AAOIFI-style ratio screens as
   commonly applied. The business-activity screen is a judgement
   no arithmetic can make, so it is stated as a question rather
   than answered.
   ============================================================ */

data class ScreenTest(val id: String, val value: Double, val limit: Double, val pass: Boolean)

data class Shariah(val tests: List<ScreenTest>, val pass: Boolean, val tested: Int)

fun shariahScreen(f: Facts): Shariah {
    val d = f.d
    /* Market capitalisation is the divisor where there is one and
       total assets where there is not, which is the site's
       `r.mcap || d.totalAssets`. A zero or NaN mcap falls through
       in JavaScript; written out here because it must. */
    val denom = f["mcap"].let { if (it.isFinite() && it != 0.0) it else d.totalAssets }
    val debtRatio = div(d.totalDebt, denom) * 100
    val cashRatio = div(d.cash, denom) * 100
    val income = d.nonCompliantIncome

    val tests = listOf(
        ScreenTest("debt", debtRatio, 33.0, debtRatio < 33),
        ScreenTest("cash", cashRatio, 33.0, cashRatio < 33),
        ScreenTest("income", income, 5.0, income < 5),
    )
    return Shariah(tests, tests.all { it.pass }, tests.size)
}

/* ============================================================
   THE VERDICT
   ============================================================ */

data class Band(val id: String, val min: Double)

val BANDS: List<Band> = listOf(
    Band("buy", 75.0),
    Band("accumulate", 62.0),
    Band("hold", 48.0),
    Band("trim", 35.0),
    Band("avoid", Double.NEGATIVE_INFINITY),
)

fun bandFor(score: Double?): Band =
    if (score == null || !score.isFinite()) BANDS.last()
    else BANDS.first { score >= it.min }

/** The score needed to reach the next band up, and the one below
    which it drops, so the page can say how much room there is
    either side rather than presenting a verdict as though it were
    solid. */
data class Edges(val up: Double?, val down: Double?)

fun bandEdges(score: Double?): Edges {
    /* A non-finite score matches no band, and the site would
       index past the end of the list here. It never reaches this
       because `composite` returns null rather than NaN and
       `analyse` checks; the guard is here so that calling this
       directly cannot throw. */
    if (score == null || !score.isFinite()) return Edges(null, null)
    val i = BANDS.indexOfFirst { score >= it.min }
    return Edges(
        up = if (i > 0) BANDS[i - 1].min else null,
        down = if (BANDS[i].min == Double.NEGATIVE_INFINITY) null else BANDS[i].min,
    )
}

/* ============================================================
   WHAT IS DRAGGING IT DOWN

   For each metric: how many points of the final composite it is
   leaving on the table. That is its share of the composite times
   its distance from 100. Sorted, it is the shortest honest answer
   to "what would have to change".
   ============================================================ */

data class Drag(
    val id: String,
    val pillar: String,
    val score: Double,
    val cost: Double,
    /** Share of the FINAL composite this one metric controls. */
    val share: Double,
)

fun drags(
    scored: List<Scored>,
    pillars: Map<String, Pillar>,
    weights: Weights,
    limit: Int = 6,
): List<Drag> {
    val wsum = PILLARS.sumOf { p ->
        val w = weights[p] ?: 0.0
        if (pillars[p]?.score != null && w > 0) w else 0.0
    }
    if (wsum == 0.0) return emptyList()

    val out = mutableListOf<Drag>()
    for (p in PILLARS) {
        val w = weights[p] ?: 0.0
        if (pillars[p]?.score == null || w <= 0) continue
        val live = scored.filter { it.pillar == p && !it.na }
        val inner = live.sumOf { it.w }
        if (inner == 0.0) continue
        for (s in live) {
            val share = (w / wsum) * (s.w / inner)
            out += Drag(s.id, p, s.score!!, share * (100 - s.score), share)
        }
    }
    /* Stable within equal cost, which `Array.prototype.sort` is
       also required to be since ES2019, so two metrics dragging
       identically come back in registry order on both. */
    return out.sortedByDescending { it.cost }.take(limit)
}

/* ============================================================
   THE PRICE CEILING

   A plain weighted mean cannot answer the question this page
   asks. Valuation is one pillar of six, so on balanced weights it
   controls about a fifth of the score, and TRIPLING the share
   price of the default company moved the total from 69 to 62 and
   left the verdict reading "worth accumulating". The valuation
   pillar had collapsed from 46 to 15, exactly as it should; it
   simply could not drag a mean far enough.

   That is fatal for a tool whose entire question is whether to
   buy AT THIS PRICE. Price is the one variable the reader
   controls, and a tool insensitive to it is worse than no tool.

   So the verdict is capped by the valuation pillar. Not a veto:
   the score is still shown and still honest. But a good business
   bought badly is a bad investment, and no quality score should
   be able to argue otherwise. The cap is stated on the page
   whenever it bites, so it is never a silent correction.
   ============================================================ */

private const val CAP_HOLD = 30.0
private const val CAP_ACCUMULATE = 42.0

fun priceCap(pillars: Map<String, Pillar>): String? {
    val v = pillars["value"]?.score ?: return null
    if (!v.isFinite()) return null
    if (v < CAP_HOLD) return "hold"
    if (v < CAP_ACCUMULATE) return "accumulate"
    return null
}

private fun bandRank(id: String): Int = BANDS.indexOfFirst { it.id == id }

/** Everything the model says about one company.

    `verdict` is what the page prints and `earned` is what the
    score alone would have said; `capped` is the difference, and
    the page has to show it, because a silent correction is the
    one thing a tool like this must never do. */
data class Analysis(
    val d: Inputs,
    val f: Facts,
    val scored: List<Scored>,
    val pillars: Map<String, Pillar>,
    val score: Double?,
    val verdict: Band,
    val earned: Band,
    val capped: Boolean,
    val vetoed: Boolean,
    val veto: Flag?,
    val edges: Edges,
    val flags: List<Flag>,
    val signals: List<Signal>,
    val fair: Fair,
    val shariah: Shariah,
    val drags: List<Drag>,
)

fun analyse(d: Inputs, weights: Weights = WEIGHT_PRESETS.getValue("balanced")): Analysis {
    val f = Facts.of(d)
    val scored = scoreMetrics(f)
    val pillars = scorePillars(scored)
    val score = composite(pillars, weights)
    val flags = checkFlags(f)
    val veto = flags.firstOrNull { it.level == "veto" }

    val earned = bandFor(score)
    val cap = if (veto != null) null else priceCap(pillars)
    val capped = cap != null && bandRank(cap) > bandRank(earned.id)
    val verdict = when {
        veto != null -> BANDS.last()
        capped -> BANDS[bandRank(cap!!)]
        else -> earned
    }

    return Analysis(
        d = d, f = f, scored = scored, pillars = pillars,
        score = score,
        verdict = verdict,
        earned = earned,
        capped = capped,
        vetoed = veto != null,
        veto = veto,
        edges = bandEdges(score),
        flags = flags,
        signals = signals(f, pillars),
        fair = fairValue(f),
        shariah = shariahScreen(f),
        drags = drags(scored, pillars, weights),
    )
}
