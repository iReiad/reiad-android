package uk.co.reiad.library.core.stock

/* ============================================================
   One pass over the inputs, producing every derived number the
   metrics need.

   Kept separate from scoring, as it is on the site, so a page can
   show a ratio without a verdict attached to it and so a test can
   check arithmetic and judgement independently.

   Sixty-nine numbers, and `RatiosTest` asserts every one of them
   against what the JavaScript produced for seven companies.
   ============================================================ */

/** Every derived number, by the site's own name.

    A map rather than a data class of sixty-nine fields, and that
    is a deliberate trade. A class would be typed and would also
    be sixty-nine places to make a typo that compiles; the map is
    keyed by the SAME strings the fixtures use, so a renamed
    number fails the comparison by name instead of shifting a
    column silently. The site's own registry makes the same
    choice one level up. */
typealias Ratios = Map<String, Double>

fun ratios(d: Inputs): Ratios {
    val r = LinkedHashMap<String, Double>()
    val fin = isFinancialSector(d.sector)

    /* --- size --- */
    val mcap = d.price * d.shares                       // lakh BDT
    val eps = div(d.netIncome, d.shares)
    val epsPrev = div(d.netIncomePrev, if (d.sharesPrev != 0.0) d.sharesPrev else d.shares)
    val bvps = div(d.equity, d.shares)
    val ebitda = d.ebit + d.depreciation
    val netDebt = d.totalDebt - d.cash
    val ev = mcap + d.totalDebt - d.cash
    val fcf = d.cfo - d.capex

    r["mcap"] = mcap
    r["eps"] = eps
    r["epsPrev"] = epsPrev
    r["bvps"] = bvps
    r["ebitda"] = ebitda
    r["netDebt"] = netDebt
    r["ev"] = ev
    r["fcf"] = fcf
    r["paidUp"] = d.shares * 10                         // 10 BDT face value

    /* --- valuation --- */
    val pe = if (eps > 0) div(d.price, eps) else Double.NaN
    val pb = if (bvps > 0) div(d.price, bvps) else Double.NaN
    val earningsYield = if (pe > 0) 100 / pe else Double.NaN
    val divYield = div(d.dps, d.price) * 100

    r["pe"] = pe
    r["pb"] = pb
    r["ps"] = div(mcap, d.revenue)
    r["evEbitda"] = if (ebitda > 0) div(ev, ebitda) else Double.NaN
    r["earningsYield"] = earningsYield
    r["fcfYield"] = div(fcf, mcap) * 100
    r["divYield"] = divYield
    r["divYieldNet"] = divYield * (1 - d.divTax / 100)

    /* Relative to the sector, which is the only way "expensive"
       means anything. A pharma company on 16 times and a textile
       mill on 16 times are not the same news. */
    r["peRel"] = div(pe, d.sectorPE)
    r["pbRel"] = div(pb, d.sectorPB)
    r["peVsMarket"] = div(pe, d.marketPE)

    /* --- growth --- */
    val epsCagr3y = cagr(d.netIncome, d.netIncome3y, 3.0)
    r["revGrowth"] = pctChange(d.revenue, d.revenuePrev)
    r["epsGrowth"] = pctChange(eps, epsPrev)
    r["niGrowth"] = pctChange(d.netIncome, d.netIncomePrev)
    r["epsCagr3y"] = epsCagr3y
    /* Operating cash growth: a harder number to manage than
       profit growth and so a better read on whether the business
       is actually getting bigger. Undefined from a negative base,
       because a recovery from cash burn is not a growth rate. */
    r["cfoGrowth"] = if (d.cfoPrev > 0) pctChange(d.cfo, d.cfoPrev) else Double.NaN
    r["peg"] = if (pe > 0 && epsCagr3y > 0) div(pe, epsCagr3y) else Double.NaN

    /* --- profitability --- */
    val avgAssets = if (d.totalAssetsPrev > 0) {
        (d.totalAssets + d.totalAssetsPrev) / 2
    } else {
        d.totalAssets
    }
    val roe = div(d.netIncome, d.equity) * 100
    val capitalEmployed = d.totalAssets - d.currentLiabilities
    val netMargin = div(d.netIncome, d.revenue) * 100
    val netMarginPrev = div(d.netIncomePrev, d.revenuePrev) * 100

    r["avgAssets"] = avgAssets
    r["roe"] = roe
    r["roa"] = div(d.netIncome, avgAssets) * 100
    r["capitalEmployed"] = capitalEmployed
    r["roce"] = if (fin) Double.NaN else div(d.ebit, capitalEmployed) * 100
    r["grossMargin"] = div(d.grossProfit, d.revenue) * 100
    r["opMargin"] = div(d.ebit, d.revenue) * 100
    r["netMargin"] = netMargin
    r["netMarginPrev"] = netMarginPrev
    r["marginTrend"] = netMargin - netMarginPrev
    r["marginRel"] = div(netMargin, d.sectorMargin)
    r["roeRel"] = if (roe > 0 && d.sectorROE > 0) div(roe, d.sectorROE) else Double.NaN
    r["assetTurnover"] = if (fin) Double.NaN else div(d.revenue, avgAssets)

    /* DuPont: the same ROE, told as three separate stories. A 20%
       ROE built on a 3% margin and five turns of leverage is a
       different company from one built on a 20% margin and no
       debt, and the single number hides which you are holding. */
    val dupontTurnover = div(d.revenue, avgAssets)
    val dupontLeverage = div(avgAssets, d.equity)
    r["dupontMargin"] = netMargin
    r["dupontTurnover"] = dupontTurnover
    r["dupontLeverage"] = dupontLeverage
    r["dupontRoe"] = (netMargin / 100) * dupontTurnover * dupontLeverage * 100

    /* --- earnings quality --- */
    r["cashConversion"] = if (d.netIncome > 0) div(d.cfo, d.netIncome) else Double.NaN
    r["accruals"] = div(d.netIncome - d.cfo, avgAssets)

    /* --- balance sheet --- */
    r["debtEquity"] = if (d.equity > 0) div(d.totalDebt, d.equity) else Double.NaN
    r["netDebtEbitda"] = if (ebitda > 0) div(netDebt, ebitda) else Double.NaN
    r["interestCover"] =
        if (d.interestExpense > 0) div(d.ebit, d.interestExpense) else Double.NaN
    r["currentRatio"] = if (fin) Double.NaN else div(d.currentAssets, d.currentLiabilities)
    r["quickRatio"] =
        if (fin) Double.NaN else div(d.currentAssets - d.inventory, d.currentLiabilities)

    /* --- dividend --- */
    val divTotal = d.dps * d.shares                     // lakh BDT
    r["payout"] = if (eps > 0) div(d.dps, eps) * 100 else Double.NaN
    r["divCover"] = if (d.dps > 0) div(eps, d.dps) else Double.NaN
    r["divTotal"] = divTotal
    r["fcfCoverDiv"] = if (divTotal > 0) div(fcf, divTotal) else Double.NaN
    r["yieldSpread"] = divYield - d.riskFree
    r["yieldVsFdr"] = divYield - d.fdr
    r["realYield"] = divYield - d.inflation
    r["earningsYieldSpread"] = earningsYield - d.riskFree

    /* --- market --- */
    val range = d.high52 - d.low52
    r["range52"] = if (range > 0) ((d.price - d.low52) / range) * 100 else Double.NaN
    r["vsHigh"] = pctChange(d.price, d.high52)
    r["vsMa50"] = pctChange(d.price, d.ma50)
    r["vsMa200"] = pctChange(d.price, d.ma200)
    r["maCross"] = div(d.ma50, d.ma200)
    r["relStrength"] = d.stockReturn12m - d.indexReturn12m

    r["altmanZ"] = if (fin) Double.NaN else altmanZ(d)

    return r
}
