package uk.co.reiad.library.core.broker

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.math.max

/* ============================================================
   What a broker's JSON means.

   `/api/broker/live` hands back Trading 212's own answer,
   unchanged: a summary object and an array of positions. Turning
   that into a dashboard is a dozen small derivations, and
   `shared/portfolio.ts` on the website is the original. This is a
   second implementation of it, locked to
   `content/portfolio.fixtures.json`.

   ---- why every read here is defensive ----

   Every field belongs to somebody else. A broker that renames one
   or starts sending a string where a number was must not take the
   page down, so a field that cannot be read is NOUGHT rather than
   an exception, and the page shows a nought where it cannot show
   a figure.

   That is why this parses `JsonElement` by hand rather than
   deserialising into a data class. `@Serializable` with a
   non-null Double throws on a string; with nullable fields
   everywhere it is the same hand-written null handling one layer
   further from the reader. The fixture holds a summary with a
   string in it for exactly this.

   `unrealizedProfitLoss` is spelt the American way at the source
   and is not ours to correct. A Kotlin-shaped rename here would
   read a field that does not exist, which under the rule above is
   nought, on every holding, silently.
   ============================================================ */

/* ---------- reading somebody else's JSON ---------- */

private fun obj(v: JsonElement?): JsonObject =
    (v as? JsonObject) ?: JsonObject(emptyMap())

private fun num(v: JsonElement?): Double {
    val p = v as? JsonPrimitive ?: return 0.0
    /* `isString` matters: JSON `"18420.55"` parses as a double
       and the site reads it as nought, because `typeof v ===
       "number"` is false there. Two implementations disagreeing
       about that would show two different account values. */
    if (p.isString) return 0.0
    return p.doubleOrNull?.takeIf { it.isFinite() } ?: 0.0
}

private fun str(v: JsonElement?): String {
    val p = v as? JsonPrimitive ?: return ""
    return if (p.isString) p.content else ""
}

/** A percentage of something, or nought where the something is
    nought. Never infinite: a holding bought for nothing would
    otherwise render as an infinite gain. */
fun pctOf(part: Double, whole: Double): Double =
    if (whole > 0) (part / whole) * 100 else 0.0

/* ---------- the five figures at the top ---------- */

@Serializable
data class Totals(
    val currency: String,
    /** Everything, cash included. */
    val total: Double,
    val invested: Double,
    val cost: Double,
    val unrealised: Double,
    /** Gain against what was PAID, not against the total: a
        portfolio half in cash has not made half the return. */
    val unrealisedPct: Double,
    val realised: Double,
    val freeCash: Double,
    val inPies: Double,
)

fun totalsOf(summary: JsonElement?): Totals {
    val s = obj(summary)
    val inv = obj(s["investments"])
    val cash = obj(s["cash"])
    val cost = num(inv["totalCost"])
    val unrealised = num(inv["unrealizedProfitLoss"])
    return Totals(
        currency = str(s["currency"]).ifEmpty { "GBP" },
        total = num(s["totalValue"]),
        invested = num(inv["currentValue"]),
        cost = cost,
        unrealised = unrealised,
        unrealisedPct = pctOf(unrealised, cost),
        realised = num(inv["realizedProfitLoss"]),
        freeCash = num(cash["availableToTrade"]),
        inPies = num(cash["inPies"]),
    )
}

/* ---------- one holding ---------- */

@Serializable
data class Holding(
    val name: String,
    /** `AAPL_US_EQ` is an internal id and `AAPL` is what a reader
        recognises. */
    val ticker: String,
    /** The instrument's own currency, which is often not the
        account's: a price paid in dollars inside a sterling
        account is still a dollar figure. */
    val currency: String,
    val quantity: Double,
    val averagePaid: Double,
    val price: Double,
    val value: Double,
    val cost: Double,
    val gain: Double,
    val gainPct: Double,
    /** Share of what is INVESTED, so the column adds to a hundred
        whatever the cash balance is. */
    val weightPct: Double,
    /** Against the largest holding rather than against a hundred,
        because a bar that never fills its row is a bar nobody can
        compare. Floored at 2 so the smallest is still visible. */
    val barPct: Double,
)

fun holdingsOf(positions: JsonElement?, invested: Double): List<Holding> {
    val list = (positions as? kotlinx.serialization.json.JsonArray)?.jsonArray ?: return emptyList()
    val rows = list.map { raw ->
        val p = obj(raw)
        val w = obj(p["walletImpact"])
        val instrument = obj(p["instrument"])
        val cost = num(w["totalCost"])
        val gain = num(w["unrealizedProfitLoss"])
        val value = num(w["currentValue"])
        Holding(
            name = str(instrument["name"]).take(60),
            ticker = str(instrument["ticker"]).substringBefore("_"),
            currency = str(instrument["currency"]),
            quantity = num(p["quantity"]),
            averagePaid = num(p["averagePricePaid"]),
            price = num(p["currentPrice"]),
            value = value,
            cost = cost,
            gain = gain,
            gainPct = pctOf(gain, cost),
            weightPct = pctOf(value, invested),
            barPct = 0.0,
        )
    /* Biggest first. The broker answers in its own order, and a
       list of holdings that reshuffles between refreshes looks
       like trades that never happened.

       `sortedByDescending` is stable, and so is the site's
       `Array.prototype.sort` since ES2019, so two holdings of
       exactly equal value come back in the same order on both. */
    }.sortedByDescending { it.value }

    val largest = rows.firstOrNull()?.value ?: 0.0
    return rows.map { row ->
        row.copy(barPct = if (largest > 0) max(2.0, (row.value / largest) * 100) else 0.0)
    }
}

/* ---------- dividends, by month ---------- */

@Serializable
data class Month(
    /** `2026-08`, which sorts and needs no locale. */
    val key: String,
    val amount: Double,
)

/**
 * The last twelve months of dividends, oldest first, including
 * the months with none in them.
 *
 * The empty ones are the point. A chart of only the months that
 * paid has no gaps in it, which is exactly the wrong impression:
 * a portfolio paying twice a year should LOOK like a portfolio
 * paying twice a year.
 *
 * `year` and `month` are arguments rather than a call to the
 * clock, so this can be tested and so the two implementations
 * bucket against the same month rather than against whatever each
 * device thinks the date is. `month` is 1 to 12, unlike
 * JavaScript's.
 */
fun dividendMonths(items: JsonElement?, year: Int, month: Int, span: Int = 12): List<Month> {
    val paid = mutableMapOf<String, Double>()
    val list = (items as? kotlinx.serialization.json.JsonArray)?.jsonArray
    for (raw in list.orEmpty()) {
        val d = obj(raw)
        val key = str(d["paidOn"]).take(7)
        if (key.length != 7) continue
        paid[key] = (paid[key] ?: 0.0) + num(d["amount"])
    }

    return (span - 1 downTo 0).map { back ->
        /* Counted in months and normalised, rather than by
           subtracting days: the 31st of March minus one month is
           a date that does not exist. */
        val total = year * 12 + (month - 1) - back
        val key = "${total / 12}-${(total % 12 + 1).toString().padStart(2, '0')}"
        Month(key, paid[key] ?: 0.0)
    }
}

/** What those twelve months came to, which is the honest headline
    for a dividend chart: the tallest column means nothing without
    it. */
fun dividendTotal(months: List<Month>): Double = months.sumOf { it.amount }

/* ---------- what a stranger is shown ----------

   The shape `/api/broker/public` returns. Percentages only, and
   `holdings` may be null: an admin decides on the dashboard
   whether a stranger sees the list at all, whether the names are
   shown, and whether the returns are.

   The stripping happens on the SERVER, which is the whole point:
   a client that filters is a client that has already been sent
   the thing it is hiding. */

@Serializable
data class PublicHolding(
    val name: String = "",
    val ticker: String = "",
    val weightPct: Double = 0.0,
    val returnPct: Double? = null,
)

@Serializable
data class PublicPortfolio(
    val at: String = "",
    val count: Int = 0,
    val investedPct: Double = 0.0,
    val cashPct: Double = 0.0,
    val returnPct: Double = 0.0,
    val holdings: List<PublicHolding>? = null,
)

@Serializable
data class PublicAnswer(val portfolio: PublicPortfolio? = null)
