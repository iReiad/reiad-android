package uk.co.reiad.library.core.stock

/* ============================================================
   The forty-four metrics, and the four numbers each one carries.

   `METRICS` in `aab/tools/stock.model.js` is the original. Every
   weight, every anchor and every "does this apply" test below is
   that file's, and `ModelTest` asserts all forty-four against
   what it produced for seven companies.

   ---- what each field is ----

   `w` is the weight inside its OWN pillar, not across the page.
   The pillar weights are the reader's, and they are separate.

   `na` returning true means "this ratio has no meaning here". The
   metric is DROPPED and its weight redistributed across the rest
   of its pillar, rather than scoring zero. A bank with no current
   ratio must not be marked down for not having one, and that
   distinction is most of what makes the financial path work.

   `fmt` and `hi` are for the display layer: how to print the raw
   number, and which direction is good.
   ============================================================ */

/** Everything a metric can ask about, in one place.

    The site's `na` and `get` take `(r, d)` and reach into both.
    Two of the three things they reach for are not numbers, so the
    ratios map alone cannot serve them: `isFinancial` decides
    which half of the health pillar applies at all, and the
    Piotroski pair is a score out of a count.

    `get(key)` answers NaN for a name the map does not hold, which
    is what JavaScript does with an absent property once it
    reaches `Number.isFinite`. A typo therefore reads as "not
    testable" rather than throwing, and the fixture comparison is
    what catches it: a metric that has quietly gone `na` on every
    company fails against a fixture that scored it. */
class Facts(val d: Inputs, val r: Ratios, val f: FScore) {
    val isFinancial: Boolean = isFinancialSector(d.sector)

    operator fun get(key: String): Double = r[key] ?: Double.NaN

    companion object {
        /** The whole derivation, from what a reader typed. */
        fun of(d: Inputs): Facts {
            val r = ratios(d)
            return Facts(d, r, piotroski(d, r))
        }
    }
}

/** One anchor, written the way the site writes it: `0.4 at 100`
    for `[0.4, 100]`. A pair of bare doubles would need `.0` on
    every one of the three hundred numbers below, and every `.0`
    is a place to leave one off. */
private infix fun Number.at(y: Number): Pair<Double, Double> = toDouble() to y.toDouble()

data class Metric(
    val id: String,
    val pillar: String,
    val w: Double,
    val fmt: String,
    val hi: Boolean,
    val anchors: List<Pair<Double, Double>>,
    val get: (Facts) -> Double,
    /** The number to PRINT, where it differs from the number
        scored. `peRel` is scored against the sector and printed
        as a plain P/E, because "1.06× its sector" is the
        judgement and "17.0" is the fact. */
    val raw: ((Facts) -> Double)? = null,
    val na: (Facts) -> Boolean,
)

val METRICS: List<Metric> = listOf(
    /* ---------------- VALUATION ---------------- */
    Metric(
        "peRel", "value", 3.0, "x", false,
        listOf(0.4 at 100, 0.7 at 85, 1.0 at 60, 1.4 at 35, 2.0 at 15, 3.0 at 0),
        get = { it["peRel"] }, raw = { it["pe"] },
        na = { !it["peRel"].isFinite() },
    ),
    Metric(
        "pbRel", "value", 2.0, "x", false,
        listOf(0.4 at 100, 0.7 at 85, 1.0 at 60, 1.5 at 35, 2.5 at 12, 4.0 at 0),
        get = { it["pbRel"] }, raw = { it["pb"] },
        na = { !it["pbRel"].isFinite() },
    ),
    Metric(
        "evEbitda", "value", 2.0, "x", false,
        listOf(3 at 100, 5 at 85, 8 at 60, 12 at 32, 18 at 10, 25 at 0),
        get = { it["evEbitda"] },
        na = { it.isFinancial || !it["evEbitda"].isFinite() || it["evEbitda"] < 0 },
    ),
    Metric(
        "earningsYieldSpread", "value", 3.0, "pp", true,
        listOf(-8 at 0, -3 at 20, 0 at 45, 3 at 70, 8 at 92, 14 at 100),
        get = { it["earningsYieldSpread"] },
        na = { !it["earningsYieldSpread"].isFinite() },
    ),
    Metric(
        "fcfYield", "value", 2.0, "%", true,
        listOf(-5 at 0, 0 at 25, 3 at 50, 6 at 75, 10 at 92, 15 at 100),
        get = { it["fcfYield"] },
        na = { !it["fcfYield"].isFinite() },
    ),
    Metric(
        "peg", "value", 2.0, "x", false,
        listOf(0.4 at 100, 0.8 at 85, 1.2 at 65, 2.0 at 35, 3.0 at 12, 5.0 at 0),
        get = { it["peg"] },
        na = { !it["peg"].isFinite() },
    ),
    /* Cheap against its own sector and cheap against the market
       are different questions, and on the DSE they come apart
       often: whole sectors get bid up together, so a company can
       look fair next to its peers and expensive next to
       everything else. This is what the index picker feeds. */
    Metric(
        "peVsMarket", "value", 1.0, "x", false,
        listOf(0.5 at 100, 0.8 at 82, 1.0 at 62, 1.5 at 35, 2.2 at 12, 3.5 at 0),
        get = { it["peVsMarket"] }, raw = { it["pe"] },
        na = { !it["peVsMarket"].isFinite() },
    ),
    Metric(
        "ps", "value", 1.0, "x", false,
        listOf(0.3 at 100, 0.8 at 78, 1.5 at 58, 3 at 32, 6 at 10, 10 at 0),
        get = { it["ps"] },
        na = { it.isFinancial || !it["ps"].isFinite() },
    ),

    /* ---------------- QUALITY ---------------- */
    Metric(
        "roe", "quality", 3.0, "%", true,
        listOf(-10 at 0, 0 at 8, 8 at 25, 12 at 45, 18 at 70, 25 at 90, 35 at 100),
        get = { it["roe"] },
        na = { !it["roe"].isFinite() },
    ),
    Metric(
        "roce", "quality", 2.0, "%", true,
        listOf(-5 at 0, 0 at 8, 6 at 20, 10 at 45, 15 at 70, 22 at 90, 30 at 100),
        get = { it["roce"] },
        na = { !it["roce"].isFinite() },
    ),
    Metric(
        "marginRel", "quality", 2.0, "x", true,
        listOf(0.3 at 10, 0.6 at 30, 1.0 at 60, 1.4 at 80, 2.0 at 100),
        get = { it["marginRel"] }, raw = { it["netMargin"] },
        na = { !it["marginRel"].isFinite() || it["netMargin"] < 0 },
    ),
    Metric(
        "roeRel", "quality", 2.0, "x", true,
        listOf(0.4 at 10, 0.7 at 32, 1.0 at 60, 1.4 at 82, 2.0 at 100),
        get = { it["roeRel"] }, raw = { it["roe"] },
        na = { !it["roeRel"].isFinite() },
    ),
    Metric(
        "grossMargin", "quality", 1.0, "%", true,
        listOf(0 at 0, 5 at 12, 15 at 30, 25 at 55, 40 at 80, 55 at 100),
        get = { it["grossMargin"] },
        na = { it.isFinancial || !it["grossMargin"].isFinite() },
    ),
    Metric(
        "cashConversion", "quality", 3.0, "x", true,
        listOf(-0.5 at 0, 0 at 8, 0.4 at 20, 0.7 at 45, 1.0 at 70, 1.3 at 90, 2.0 at 100),
        get = { it["cashConversion"] },
        na = { !it["cashConversion"].isFinite() },
    ),
    Metric(
        "accruals", "quality", 2.0, "x", false,
        listOf(-0.05 at 100, 0 at 85, 0.03 at 60, 0.07 at 35, 0.12 at 10, 0.2 at 0),
        get = { it["accruals"] },
        na = { !it["accruals"].isFinite() },
    ),
    Metric(
        "assetTurnover", "quality", 1.0, "x", true,
        listOf(0.1 at 10, 0.4 at 35, 0.7 at 55, 1.0 at 75, 1.5 at 95, 2.5 at 100),
        get = { it["assetTurnover"] },
        na = { !it["assetTurnover"].isFinite() },
    ),

    /* ---------------- GROWTH ---------------- */
    Metric(
        "revGrowth", "growth", 3.0, "%", true,
        listOf(-15 at 0, -5 at 18, 0 at 32, 8 at 55, 15 at 75, 25 at 92, 40 at 100),
        get = { it["revGrowth"] },
        na = { !it["revGrowth"].isFinite() },
    ),
    Metric(
        "niGrowth", "growth", 3.0, "%", true,
        listOf(-30 at 0, -10 at 18, 0 at 35, 10 at 60, 20 at 80, 35 at 95, 60 at 100),
        get = { it["niGrowth"] },
        na = { !it["niGrowth"].isFinite() },
    ),
    Metric(
        "epsCagr3y", "growth", 3.0, "%", true,
        listOf(-15 at 0, -5 at 15, 0 at 30, 10 at 60, 18 at 82, 30 at 100),
        get = { it["epsCagr3y"] },
        na = { !it["epsCagr3y"].isFinite() },
    ),
    Metric(
        "cfoGrowth", "growth", 2.0, "%", true,
        listOf(-40 at 0, -15 at 20, 0 at 38, 10 at 62, 25 at 85, 50 at 100),
        get = { it["cfoGrowth"] },
        na = { !it["cfoGrowth"].isFinite() },
    ),
    Metric(
        "marginTrend", "growth", 2.0, "pp", true,
        listOf(-6 at 0, -4 at 12, -1 at 32, 0 at 50, 1 at 70, 3 at 90, 6 at 100),
        get = { it["marginTrend"] },
        na = { !it["marginTrend"].isFinite() },
    ),

    /* ---------------- FINANCIAL HEALTH ----------------
       Everything down to `fScore` is non-financial only, and the
       financial block after it takes over for banks, NBFIs and
       insurers. */
    Metric(
        "debtEquity", "health", 3.0, "x", false,
        listOf(0 at 100, 0.3 at 85, 0.6 at 65, 1.0 at 45, 1.8 at 20, 3.0 at 0),
        get = { it["debtEquity"] },
        na = { it.isFinancial || !it["debtEquity"].isFinite() },
    ),
    Metric(
        "netDebtEbitda", "health", 3.0, "x", false,
        listOf(-1 at 100, 0 at 92, 1 at 78, 2 at 60, 3.5 at 35, 5 at 12, 7 at 0),
        get = { it["netDebtEbitda"] },
        na = { it.isFinancial || !it["netDebtEbitda"].isFinite() },
    ),
    Metric(
        "interestCover", "health", 3.0, "x", true,
        listOf(0 at 0, 1 at 10, 2 at 30, 4 at 60, 8 at 85, 15 at 100),
        get = { it["interestCover"] },
        na = { it.isFinancial || !it["interestCover"].isFinite() },
    ),
    /* Non-monotone on purpose: a current ratio of 5 is not five
       times as healthy as 1, it is cash sitting idle or stock
       that will not move. The anchors rise, then fall. */
    Metric(
        "currentRatio", "health", 2.0, "x", true,
        listOf(0.5 at 10, 1.0 at 35, 1.5 at 70, 2.0 at 90, 3.0 at 85, 5.0 at 60),
        get = { it["currentRatio"] },
        na = { !it["currentRatio"].isFinite() },
    ),
    Metric(
        "quickRatio", "health", 2.0, "x", true,
        listOf(0.3 at 5, 0.7 at 35, 1.0 at 65, 1.5 at 88, 2.5 at 100),
        get = { it["quickRatio"] },
        na = { !it["quickRatio"].isFinite() },
    ),
    Metric(
        "altmanZ", "health", 3.0, "n", true,
        listOf(0 at 0, 2 at 8, 4.35 at 25, 5.85 at 60, 7.5 at 85, 10 at 100),
        get = { it["altmanZ"] },
        na = { !it["altmanZ"].isFinite() },
    ),
    /* Scored as a PROPORTION of the tests that could be asked,
       scaled back to nine, so a company with six testable checks
       and five passes is not scored as five out of nine. Below
       five testable it is dropped: a proportion of three is
       noise, not a score. */
    Metric(
        "fScore", "health", 2.0, "n", true,
        listOf(0 at 0, 3 at 18, 5 at 45, 7 at 78, 8 at 92, 9 at 100),
        get = { if (it.f.tested >= 5) (it.f.score.toDouble() / it.f.tested) * 9 else Double.NaN },
        raw = { it.f.score.toDouble() },
        na = { it.f.tested < 5 },
    ),

    /* ---------------- FINANCIAL MODE ----------------
       Banks and NBFIs are the largest block on the DSE by market
       value, and a generic screener gets every one of them
       wrong: deposits look like debt, so D/E reads 8×; there is
       no EBITDA; the current ratio is meaningless; Altman marks
       a perfectly sound bank as distressed. These five replace
       them, and are what a bank is actually supervised on. */
    Metric(
        "car", "health", 3.0, "%", true,
        listOf(6 at 0, 8 at 12, 10 at 30, 12.5 at 60, 15 at 85, 18 at 100),
        get = { if (it.d.car > 0) it.d.car else Double.NaN },
        na = { !it.isFinancial || !(it.d.car > 0) },
    ),
    Metric(
        "npl", "health", 3.0, "%", false,
        listOf(1 at 100, 3 at 80, 5 at 58, 8 at 32, 12 at 10, 20 at 0),
        get = { if (it.d.npl > 0) it.d.npl else Double.NaN },
        na = { !it.isFinancial || !(it.d.npl > 0) },
    ),
    Metric(
        "provisionCover", "health", 2.0, "%", true,
        listOf(30 at 0, 60 at 30, 80 at 55, 100 at 80, 130 at 100),
        get = { if (it.d.provisionCover > 0) it.d.provisionCover else Double.NaN },
        na = { !it.isFinancial || !(it.d.provisionCover > 0) },
    ),
    Metric(
        "costIncome", "health", 2.0, "%", false,
        listOf(30 at 100, 40 at 82, 50 at 60, 60 at 38, 70 at 15, 85 at 0),
        get = { if (it.d.costIncome > 0) it.d.costIncome else Double.NaN },
        na = { !it.isFinancial || !(it.d.costIncome > 0) },
    ),
    Metric(
        "adr", "health", 2.0, "%", false,
        listOf(65 at 95, 75 at 88, 82 at 70, 87 at 50, 92 at 20, 100 at 0),
        get = { if (it.d.adr > 0) it.d.adr else Double.NaN },
        na = { !it.isFinancial || !(it.d.adr > 0) },
    ),

    /* ---------------- INCOME ----------------
       The weights here were rebalanced after the pillar was
       caught rewarding companies for barely paying. Cover ratios
       saturate: a token 1% dividend is trivially covered and
       scored 100 on both `divCover` and `fcfCoverDiv`, which
       between them outvoted the yield itself, so a 0.95% yield
       scored 62 and a 2.14% yield scored 65. That is nonsense on
       a pillar whose entire question is whether this is worth
       holding for the income.

       Yield carries the pillar now and the cover ratios qualify
       it, which produces the right shape: raising a dividend
       helps while it stays affordable, and stops helping at the
       point it outruns free cash flow. */
    Metric(
        "yieldSpread", "income", 6.0, "pp", true,
        listOf(-11 at 0, -6 at 18, -3 at 35, 0 at 60, 2 at 80, 5 at 100),
        get = { it["yieldSpread"] },
        na = { !it["yieldSpread"].isFinite() },
    ),
    Metric(
        "payout", "income", 2.0, "%", true,
        listOf(0 at 20, 20 at 55, 40 at 85, 60 at 90, 80 at 65, 100 at 30, 130 at 0),
        get = { it["payout"] },
        na = { !it["payout"].isFinite() },
    ),
    Metric(
        "divCover", "income", 2.0, "x", true,
        listOf(0.5 at 0, 0.8 at 5, 1.0 at 30, 1.3 at 55, 1.8 at 80, 2.5 at 95, 4 at 100),
        get = { it["divCover"] },
        na = { !it["divCover"].isFinite() },
    ),
    Metric(
        "fcfCoverDiv", "income", 2.0, "x", true,
        listOf(-1 at 0, 0 at 12, 0.5 at 30, 1.0 at 60, 1.5 at 82, 2.5 at 100),
        get = { it["fcfCoverDiv"] },
        na = { !it["fcfCoverDiv"].isFinite() },
    ),
    Metric(
        "divYears", "income", 1.0, "n", true,
        listOf(0 at 0, 1 at 20, 3 at 50, 5 at 72, 8 at 90, 12 at 100),
        get = { it.d.yearsPaid },
        na = { !it.d.yearsPaid.isFinite() },
    ),

    /* ---------------- MOMENTUM & MARKET ---------------- */
    Metric(
        "range52", "momentum", 2.0, "%", true,
        listOf(0 at 25, 20 at 45, 50 at 65, 75 at 85, 95 at 75, 100 at 60),
        get = { it["range52"] },
        na = { !it["range52"].isFinite() },
    ),
    Metric(
        "vsMa200", "momentum", 3.0, "%", true,
        listOf(-40 at 5, -20 at 25, -5 at 50, 5 at 75, 20 at 90, 50 at 70),
        get = { it["vsMa200"] },
        na = { !it["vsMa200"].isFinite() },
    ),
    Metric(
        "maCross", "momentum", 2.0, "x", true,
        listOf(0.85 at 10, 0.95 at 35, 1.0 at 55, 1.05 at 80, 1.2 at 95),
        get = { it["maCross"] },
        na = { !it["maCross"].isFinite() },
    ),
    Metric(
        "relStrength", "momentum", 3.0, "pp", true,
        listOf(-40 at 5, -20 at 25, 0 at 55, 15 at 80, 40 at 100),
        get = { it["relStrength"] },
        na = { !it["relStrength"].isFinite() },
    ),
    Metric(
        "liquidity", "momentum", 3.0, "lakh", true,
        listOf(0 at 0, 1 at 5, 5 at 25, 20 at 55, 60 at 80, 200 at 100),
        get = { it.d.turnover },
        na = { !it.d.turnover.isFinite() },
    ),
    Metric(
        "freeFloat", "momentum", 2.0, "%", true,
        listOf(5 at 0, 10 at 5, 20 at 30, 30 at 55, 45 at 80, 60 at 95, 80 at 100),
        get = { it.d.freeFloat },
        na = { !it.d.freeFloat.isFinite() },
    ),
)

val PILLARS: List<String> =
    listOf("value", "quality", "growth", "health", "income", "momentum")

/* ============================================================
   INVESTOR PRESETS

   The same company is a buy for one person and a pass for
   another, and pretending otherwise is the central dishonesty of
   every "stock rating" ever published. So the weights are the
   reader's. An income investor who does not care about momentum
   sets it to zero and the verdict moves.
   ============================================================ */

typealias Weights = Map<String, Double>

val WEIGHT_PRESETS: Map<String, Weights> = mapOf(
    "balanced" to mapOf(
        "value" to 20.0, "quality" to 20.0, "growth" to 15.0,
        "health" to 20.0, "income" to 15.0, "momentum" to 10.0,
    ),
    "value" to mapOf(
        "value" to 30.0, "quality" to 20.0, "growth" to 5.0,
        "health" to 25.0, "income" to 10.0, "momentum" to 10.0,
    ),
    "growth" to mapOf(
        "value" to 10.0, "quality" to 20.0, "growth" to 35.0,
        "health" to 15.0, "income" to 0.0, "momentum" to 20.0,
    ),
    "income" to mapOf(
        "value" to 15.0, "quality" to 15.0, "growth" to 5.0,
        "health" to 25.0, "income" to 35.0, "momentum" to 5.0,
    ),
)
