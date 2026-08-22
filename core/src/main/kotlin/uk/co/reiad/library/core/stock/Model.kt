package uk.co.reiad.library.core.stock

import kotlinx.serialization.Serializable
import kotlin.math.pow

/* ============================================================
   The stock check's arithmetic, ported.

   `aab/tools/stock.model.js` is the original and it stays the
   original: this is a second implementation of the same model,
   and two implementations of twelve hundred lines of judgement do
   not stay in step by being written carefully.

   `content/stock.fixtures.json` on the website is what keeps
   them in step. It holds every number the JavaScript produced for
   seven deliberately different companies, and `ModelTest` here
   asserts this port produces the same ones. A changed threshold
   there fails a test here rather than quietly giving two readers
   two different verdicts on the same company.

   ---- NaN is a value, not an error ----

   The one thing to get right. Half of this model's correctness is
   in what it REFUSES to say: a P/E on a loss-maker, a growth rate
   from a negative base, a current ratio for a bank. JavaScript
   says `NaN` and the page prints a dash.

   Kotlin has `Double.NaN` and it behaves the same way, but the
   temptation is to reach for `null` because it is more idiomatic.
   That would be wrong here: `NaN` propagates through arithmetic
   and `null` does not, so a chain of five derived numbers stays
   "not testable" all the way through instead of throwing at the
   first step. The fixtures encode JSON `null` where JavaScript
   had `NaN`, and the test converts rather than the model.
   ============================================================ */

/** Everything a reader types in. Names are the site's own, so a
    shared link opens to the same numbers on either. */
@Serializable
data class Inputs(
    /* --- market --- */
    val price: Double = 210.0,
    val shares: Double = 1200.0,
    val high52: Double = 246.0,
    val low52: Double = 168.0,
    val ma50: Double = 205.0,
    val ma200: Double = 198.0,
    val turnover: Double = 85.0,
    val freeFloat: Double = 38.0,
    val category: String = "A",
    val sector: String = "pharma",
    val benchmark: String = "dsex",
    val stockReturn12m: Double = 12.0,
    val indexReturn12m: Double = 7.0,

    /* --- income statement, last 12 months, lakh BDT --- */
    val revenue: Double = 95000.0,
    val grossProfit: Double = 45600.0,
    val ebit: Double = 20900.0,
    val depreciation: Double = 5200.0,
    val interestExpense: Double = 1300.0,
    val netIncome: Double = 14800.0,

    /* --- balance sheet, lakh BDT --- */
    val totalAssets: Double = 132000.0,
    val currentAssets: Double = 52000.0,
    val inventory: Double = 21000.0,
    val cash: Double = 9500.0,
    val currentLiabilities: Double = 26000.0,
    val totalDebt: Double = 19000.0,
    val equity: Double = 92000.0,
    val reserves: Double = 80000.0,

    /* --- cash flow, lakh BDT --- */
    val cfo: Double = 19500.0,
    val capex: Double = 11000.0,

    /* --- dividend --- */
    val dps: Double = 4.5,
    val divTax: Double = 10.0,
    val yearsPaid: Double = 12.0,

    /* --- prior year, optional ---

       Left at zero, the trend metrics report NOT TESTABLE rather
       than scoring falsely, which is the difference between "we
       cannot tell" and "it is bad". */
    val revenuePrev: Double = 84000.0,
    val grossProfitPrev: Double = 39500.0,
    val netIncomePrev: Double = 12900.0,
    val totalAssetsPrev: Double = 121000.0,
    val currentAssetsPrev: Double = 47500.0,
    val currentLiabilitiesPrev: Double = 25000.0,
    val totalDebtPrev: Double = 21000.0,
    val cfoPrev: Double = 17200.0,
    val sharesPrev: Double = 1200.0,
    val netIncome3y: Double = 9600.0,

    /* --- financial-sector extras, ignored elsewhere --- */
    val car: Double = 0.0,
    val npl: Double = 0.0,
    val provisionCover: Double = 0.0,
    val costIncome: Double = 0.0,
    val adr: Double = 0.0,

    /* --- benchmarks, all editable --- */
    val sectorPE: Double = 16.0,
    val sectorPB: Double = 2.6,
    val sectorROE: Double = 15.0,
    val sectorMargin: Double = 13.0,
    val marketPE: Double = 12.5,
    val riskFree: Double = 11.04,
    val fdr: Double = 8.5,
    val inflation: Double = 9.7,

    /* --- Shariah screen --- */
    val nonCompliantIncome: Double = 3.0,
)

/* ---------- the primitives, and why each refuses ---------- */

/** Division that refuses rather than throwing.

    `Number.isFinite(a) && Number.isFinite(b) && b !== 0`, said in
    Kotlin. A zero denominator gives NaN and not Infinity, which
    matters: Infinity would sail through a comparison and land a
    metric at full marks. */
internal fun div(a: Double, b: Double): Double =
    if (a.isFinite() && b.isFinite() && b != 0.0) a / b else Double.NaN

/** Percentage change, only from a POSITIVE base.

    A change from a negative number has no sign anybody can read:
    a loss halving and a loss doubling both come out positive. */
internal fun pctChange(now: Double, then: Double): Double =
    if (now.isFinite() && then.isFinite() && then > 0) ((now - then) / then) * 100 else Double.NaN

/** Compound annual growth, only where it means something.

    You cannot take the cube root of a sign change, and a CAGR
    from a loss to a profit is a number with no content. */
fun cagr(now: Double, then: Double, years: Double): Double {
    if (!now.isFinite() || !then.isFinite()) return Double.NaN
    if (now <= 0 || then <= 0 || years <= 0) return Double.NaN
    return ((now / then).pow(1.0 / years) - 1) * 100
}

/** A raw value to a 0 to 100 score, by linear interpolation
    between anchor points.

    Anchors rather than thresholds, because a P/E of 14.9 and a
    P/E of 15.1 are the same company and should not fall into
    different buckets. And they read like judgement written down,
    which is arguable out loud in a way a black box is not.

    Null for a value that is not testable, which is not the same
    as a score of zero. */
fun band(v: Double, anchors: List<Pair<Double, Double>>): Double? {
    if (!v.isFinite()) return null
    if (v <= anchors.first().first) return anchors.first().second
    if (v >= anchors.last().first) return anchors.last().second
    for (i in 0 until anchors.size - 1) {
        val (x0, y0) = anchors[i]
        val (x1, y1) = anchors[i + 1]
        if (v in x0..x1) {
            val t = if (x1 == x0) 0.0 else (v - x0) / (x1 - x0)
            return y0 + t * (y1 - y0)
        }
    }
    return anchors.last().second
}

/** The word beside the number. */
fun grade(score: Double?): String = when {
    score == null || !score.isFinite() -> "na"
    score >= 80 -> "strong"
    score >= 62 -> "good"
    score >= 45 -> "fair"
    score >= 28 -> "weak"
    else -> "poor"
}

/* ---------- sectors ---------- */

/** A sector's medians, and whether it is a financial one.

    The medians are indicative and every one of them is an
    editable input on the page: they are here so the tool can say
    "expensive RELATIVE TO WHAT", and the scoring uses whatever
    number the reader puts in.

    `financial` changes the ANALYSIS rather than the benchmarks.
    Half the ordinary ratios are meaningless on a bank's balance
    sheet, and the model drops them rather than scoring nonsense. */
data class Sector(
    val pe: Double,
    val pb: Double,
    val roe: Double,
    val netMargin: Double,
    val financial: Boolean = false,
)

val SECTORS: Map<String, Sector> = mapOf(
    "pharma" to Sector(16.0, 2.6, 15.0, 13.0),
    "bank" to Sector(8.0, 0.9, 13.0, 22.0, financial = true),
    "nbfi" to Sector(12.0, 1.2, 9.0, 15.0, financial = true),
    "insurance" to Sector(13.0, 1.6, 12.0, 10.0, financial = true),
    "textile" to Sector(11.0, 0.9, 9.0, 5.0),
    "cement" to Sector(15.0, 1.8, 10.0, 5.0),
    "food" to Sector(22.0, 4.0, 18.0, 8.0),
    "fuel" to Sector(11.0, 1.5, 14.0, 11.0),
    "engineering" to Sector(17.0, 1.9, 11.0, 7.0),
    "it" to Sector(18.0, 2.8, 16.0, 17.0),
    "telecom" to Sector(14.0, 8.0, 45.0, 15.0),
    "ceramics" to Sector(20.0, 1.7, 8.0, 6.0),
    "other" to Sector(15.0, 1.6, 12.0, 8.0),
)

fun isFinancialSector(sector: String): Boolean = SECTORS[sector]?.financial == true

/** The three headline indices, as a benchmark to price against.

    DSES is the Shariah index, and it is here because "cheap
    against the market" means something different if the only
    market you can buy is the Shariah-compliant part of it. */
val INDICES: Map<String, Double> = mapOf("dsex" to 12.5, "ds30" to 11.5, "dses" to 13.5)
