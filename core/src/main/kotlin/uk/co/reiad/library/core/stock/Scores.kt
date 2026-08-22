package uk.co.reiad.library.core.stock

/* ============================================================
   Altman Z, and the Piotroski F-score.

   Both are the site's, ported number for number. What they are
   FOR is written on the site at length and worth keeping here in
   one line each, because a reader of this file will otherwise
   assume the constants are arbitrary.
   ============================================================ */

/** Altman Z: the emerging-market variant, Z''.

    The original 1968 Z-score was fitted on US manufacturers and
    leans on a market-value term that behaves badly on a thin
    market. Z'' drops it for book values and adds a 3.25 constant
    so scores on emerging markets sit on the same scale as a US
    rating, which is why the thresholds here are 5.85 and 4.35
    rather than the 2.6 and 1.1 quoted for the original.

    It does not apply to banks at all: a bank's balance sheet is
    SUPPOSED to look like a distressed manufacturer's, so the
    financial path drops it and scores capital adequacy instead. */
fun altmanZ(d: Inputs): Double {
    val ta = d.totalAssets
    if (ta <= 0) return Double.NaN
    val liabilities = ta - d.equity
    if (liabilities <= 0) return Double.NaN
    val x1 = (d.currentAssets - d.currentLiabilities) / ta
    val x2 = d.reserves / ta                        // retained earnings proxy
    val x3 = d.ebit / ta
    val x4 = d.equity / liabilities
    return 3.25 + 6.56 * x1 + 3.26 * x2 + 6.72 * x3 + 1.05 * x4
}

/** One of the nine questions, and whether it could be asked. */
data class Check(val id: String, val pass: Boolean?, val testable: Boolean)

data class FScore(val checks: List<Check>, val tested: Int, val score: Int)

/** Piotroski: nine yes/no tests of whether the business got
    better or worse over the year.

    Its value is that none of the nine is about the PRICE. It is a
    question about the company asked nine times, and a cheap stock
    passing eight of them is a very different proposition from a
    cheap stock passing two.

    **A test needing last year's figures is SKIPPED, not failed.**
    That is the whole reason `testable` exists: a skipped test
    counted as a failure would punish a reader for not having
    typed something in, and the score would say "this company is
    bad" when it means "we cannot tell". */
fun piotroski(d: Inputs, r: Ratios): FScore {
    val checks = mutableListOf<Check>()
    fun has(x: Double) = x.isFinite() && x != 0.0
    val avgAssets = r["avgAssets"] ?: Double.NaN
    val roaNow = div(d.netIncome, avgAssets)
    val roaPrev = if (has(d.totalAssetsPrev)) div(d.netIncomePrev, d.totalAssetsPrev)
    else Double.NaN

    fun add(id: String, pass: Boolean, testable: Boolean = true) {
        checks += Check(id, if (testable) pass else null, testable)
    }

    add("profit", d.netIncome > 0)
    add("cfo", d.cfo > 0)
    add("roaUp", roaNow > roaPrev, roaPrev.isFinite())
    add("accrual", d.cfo > d.netIncome)
    add(
        "leverage",
        div(d.totalDebt, d.totalAssets) < div(d.totalDebtPrev, d.totalAssetsPrev),
        has(d.totalDebtPrev) && has(d.totalAssetsPrev),
    )
    add(
        "liquidity",
        div(d.currentAssets, d.currentLiabilities) >
            div(d.currentAssetsPrev, d.currentLiabilitiesPrev),
        has(d.currentAssetsPrev) && has(d.currentLiabilitiesPrev),
    )
    add("dilution", d.shares <= d.sharesPrev, has(d.sharesPrev))
    add(
        "margin",
        div(d.grossProfit, d.revenue) > div(d.grossProfitPrev, d.revenuePrev),
        has(d.grossProfitPrev) && has(d.revenuePrev),
    )
    add(
        "turnover",
        div(d.revenue, avgAssets) > div(d.revenuePrev, d.totalAssetsPrev),
        has(d.revenuePrev) && has(d.totalAssetsPrev),
    )

    val testable = checks.filter { it.testable }
    return FScore(
        checks = checks,
        tested = testable.size,
        score = testable.count { it.pass == true },
    )
}
