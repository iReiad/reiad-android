package uk.co.reiad.library.core.stock

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/* ============================================================
   A filled-in check, as a query string.

   The stock check has shared analyses as a query string since it
   was written, which is why an account's saved scenario stores
   one rather than a blob: there is ONE encoder for this and it
   predates the accounts.

   So this is not a new format. It is the same format read and
   written from the other side, and the whole point is that a link
   opens the same numbers wherever it is opened.

   ---- what is in it, and what is not ----

   Only what DIFFERS from a default. That keeps a link short
   enough to paste into a message, and it is also the thing that
   makes `DEFAULTS` load-bearing: a field whose default differs
   between the two sides opens a different company on the other
   one, with no sign on either that anything happened.
   `ShareTest` asserts every one of the fifty-six against the
   site's own, out of the same fixture the model is locked to.
   ============================================================ */

/** What a link carries: numbers, weights, and the two things
    that are about the reader rather than the company. */
data class Shared(
    val inputs: Inputs,
    val weights: Weights,
    /** One of the four preset names, or "custom" for a link
        carrying weights of its own. A link with hand-set weights
        is not any of the presets, and showing one as chosen would
        be the chip claiming weights the page is not using. */
    val style: String,
    val lang: String?,
)

/** The site's own key order, which is `DEFAULTS`'s, so a link
    built here is byte-identical to one built there for the same
    analysis. Not required for a link to WORK, and worth having
    anyway: two encoders producing the same bytes is a property a
    test can assert, and "close enough" is not. */
private val FIELD_ORDER: List<String> = listOf(
    "price", "shares", "high52", "low52", "ma50", "ma200", "turnover", "freeFloat",
    "category", "sector", "benchmark", "stockReturn12m", "indexReturn12m",
    "revenue", "grossProfit", "ebit", "depreciation", "interestExpense", "netIncome",
    "totalAssets", "currentAssets", "inventory", "cash", "currentLiabilities",
    "totalDebt", "equity", "reserves",
    "cfo", "capex",
    "dps", "divTax", "yearsPaid",
    "revenuePrev", "grossProfitPrev", "netIncomePrev", "totalAssetsPrev",
    "currentAssetsPrev", "currentLiabilitiesPrev", "totalDebtPrev", "cfoPrev",
    "sharesPrev", "netIncome3y",
    "car", "npl", "provisionCover", "costIncome", "adr",
    "sectorPE", "sectorPB", "sectorROE", "sectorMargin", "marketPE",
    "riskFree", "fdr", "inflation", "nonCompliantIncome",
)

/** The two fields that are text rather than a number. Everything
    else parses as a double, and a field added on the site that is
    neither fails `ShareTest`'s default comparison first. */
private val TEXT_FIELDS = setOf("category", "sector", "benchmark")

private val JSON = Json { encodeDefaults = true }

/** Every field of an `Inputs`, by the site's own name.

    Through the serializer rather than by hand, because a
    hand-written list of fifty-six getters is fifty-six places to
    forget the fifty-seventh, and forgetting one means a link that
    silently drops a number the reader typed. */
internal fun fieldsOf(d: Inputs): Map<String, String> {
    val obj: JsonObject = JSON.encodeToJsonElement(d).jsonObject
    return obj.mapValues { (_, v) -> v.jsonPrimitive.content }
}

/** A number the way JavaScript prints it, because the site's
    `URLSearchParams.set` stringifies through `String(n)`.

    `210` and not `210.0`: the site would write the first, and a
    link is compared as text by anybody reading two of them. */
internal fun jsNumber(text: String): String =
    if (text.endsWith(".0")) text.dropLast(2) else text

/** The query string for one analysis, without the leading `?`.

    `lang` is written when it is not English, or when the reader
    has chosen English explicitly: a link that says `lang=en` is
    saying "show this in English even though you usually read
    Bangla", which is a different statement from saying nothing. */
fun shareQuery(
    d: Inputs,
    weights: Weights = WEIGHT_PRESETS.getValue("balanced"),
    style: String? = null,
    lang: String? = null,
): String {
    val defaults = fieldsOf(Inputs())
    val mine = fieldsOf(d)
    val parts = mutableListOf<Pair<String, String>>()

    for (key in FIELD_ORDER) {
        val value = mine[key] ?: continue
        if (value == defaults[key]) continue
        parts += key to if (key in TEXT_FIELDS) value else jsNumber(value)
    }

    val balanced = WEIGHT_PRESETS.getValue("balanced")
    for (p in PILLARS) {
        val w = weights[p] ?: continue
        if (w != balanced[p]) parts += "w.$p" to jsNumber(w.toString())
    }

    if (style != null && style in WEIGHT_PRESETS) parts += "style" to style
    if (lang != null) parts += "lang" to lang

    return parts.joinToString("&") { (k, v) -> "${encodeQuery(k)}=${encodeQuery(v)}" }
}

/** Read one back.

    Anything unrecognised is IGNORED rather than refused, which is
    the site's behaviour and the right one: a link from a newer
    version of the tool should still open, showing the fields this
    build understands, rather than showing an error.

    `base` is what the fields not named start at. Defaults for a
    link, and the check on screen for a single box being typed
    into, which is why `withField` goes through here: one place
    reads a number out of text, so a box and a link cannot come to
    different conclusions about "1e1". */
fun readShare(query: String, base: Inputs = Inputs()): Shared {
    var d = base
    var weights = WEIGHT_PRESETS.getValue("balanced")
    var style = "balanced"
    var handSet = false
    var lang: String? = null

    for (pair in query.removePrefix("?").split("&")) {
        if (pair.isEmpty()) continue
        val i = pair.indexOf('=')
        val key = decodeQuery(if (i < 0) pair else pair.substring(0, i))
        val value = decodeQuery(if (i < 0) "" else pair.substring(i + 1))

        when {
            key == "lang" -> if (value == "bn" || value == "en") lang = value
            key == "style" -> if (value in WEIGHT_PRESETS) {
                style = value
                weights = WEIGHT_PRESETS.getValue(value)
            }
            key.startsWith("w.") && key.drop(2) in PILLARS -> {
                val n = readNumber(value) ?: continue
                weights = weights + (key.drop(2) to n)
                handSet = true
            }
            else -> d = applyField(d, key, value) ?: d
        }
    }

    /* `style` last, whichever order the link put them in: a link
       carrying its own weights is not any of the four presets. */
    if (handSet && !query.contains("style=")) style = "custom"

    return Shared(d, weights, style, lang)
}

/** One field on to an `Inputs`, or null for a name it has none
    for. Written out rather than reflected because a `when` over
    fifty-six names is checked by the compiler and a string lookup
    is not. */
private fun applyField(d: Inputs, key: String, text: String): Inputs? {
    if (key in TEXT_FIELDS) {
        return when (key) {
            "category" -> d.copy(category = text)
            "sector" -> d.copy(sector = text)
            "benchmark" -> d.copy(benchmark = text)
            else -> null
        }
    }
    val n = readNumber(text) ?: return null
    return when (key) {
        "price" -> d.copy(price = n)
        "shares" -> d.copy(shares = n)
        "high52" -> d.copy(high52 = n)
        "low52" -> d.copy(low52 = n)
        "ma50" -> d.copy(ma50 = n)
        "ma200" -> d.copy(ma200 = n)
        "turnover" -> d.copy(turnover = n)
        "freeFloat" -> d.copy(freeFloat = n)
        "stockReturn12m" -> d.copy(stockReturn12m = n)
        "indexReturn12m" -> d.copy(indexReturn12m = n)
        "revenue" -> d.copy(revenue = n)
        "grossProfit" -> d.copy(grossProfit = n)
        "ebit" -> d.copy(ebit = n)
        "depreciation" -> d.copy(depreciation = n)
        "interestExpense" -> d.copy(interestExpense = n)
        "netIncome" -> d.copy(netIncome = n)
        "totalAssets" -> d.copy(totalAssets = n)
        "currentAssets" -> d.copy(currentAssets = n)
        "inventory" -> d.copy(inventory = n)
        "cash" -> d.copy(cash = n)
        "currentLiabilities" -> d.copy(currentLiabilities = n)
        "totalDebt" -> d.copy(totalDebt = n)
        "equity" -> d.copy(equity = n)
        "reserves" -> d.copy(reserves = n)
        "cfo" -> d.copy(cfo = n)
        "capex" -> d.copy(capex = n)
        "dps" -> d.copy(dps = n)
        "divTax" -> d.copy(divTax = n)
        "yearsPaid" -> d.copy(yearsPaid = n)
        "revenuePrev" -> d.copy(revenuePrev = n)
        "grossProfitPrev" -> d.copy(grossProfitPrev = n)
        "netIncomePrev" -> d.copy(netIncomePrev = n)
        "totalAssetsPrev" -> d.copy(totalAssetsPrev = n)
        "currentAssetsPrev" -> d.copy(currentAssetsPrev = n)
        "currentLiabilitiesPrev" -> d.copy(currentLiabilitiesPrev = n)
        "totalDebtPrev" -> d.copy(totalDebtPrev = n)
        "cfoPrev" -> d.copy(cfoPrev = n)
        "sharesPrev" -> d.copy(sharesPrev = n)
        "netIncome3y" -> d.copy(netIncome3y = n)
        "car" -> d.copy(car = n)
        "npl" -> d.copy(npl = n)
        "provisionCover" -> d.copy(provisionCover = n)
        "costIncome" -> d.copy(costIncome = n)
        "adr" -> d.copy(adr = n)
        "sectorPE" -> d.copy(sectorPE = n)
        "sectorPB" -> d.copy(sectorPB = n)
        "sectorROE" -> d.copy(sectorROE = n)
        "sectorMargin" -> d.copy(sectorMargin = n)
        "marketPE" -> d.copy(marketPE = n)
        "riskFree" -> d.copy(riskFree = n)
        "fdr" -> d.copy(fdr = n)
        "inflation" -> d.copy(inflation = n)
        "nonCompliantIncome" -> d.copy(nonCompliantIncome = n)
        else -> null
    }
}

/** The full address, for a share sheet. */
fun shareLink(
    d: Inputs,
    weights: Weights = WEIGHT_PRESETS.getValue("balanced"),
    style: String? = null,
    lang: String? = null,
): String {
    val q = shareQuery(d, weights, style, lang)
    return if (q.isEmpty()) "https://reiad.co.uk/tools/stock.html"
    else "https://reiad.co.uk/tools/stock.html?$q"
}

/** A number out of somebody else's link.

    Written out rather than left to `toDoubleOrNull`, because the
    two sides have to AGREE and neither language's default parser
    is the agreement. What is accepted here is what both read the
    same way: an optional sign, digits with an optional decimal
    point, and an optional exponent. `9.5e4` is a reasonable thing
    to hand-write into a revenue field and JavaScript reads it as
    ninety-five thousand, so refusing it would be the divergence
    rather than the safety.

    Three things JavaScript's `Number()` accepts are deliberately
    refused, and each is a decision rather than an oversight:

    | `""`         | `Number("")` is NOUGHT. An empty parameter is not the number nought, it is a field nobody filled in, and a price of zero is a valuation of infinity. |
    | `Infinity`   | carries into every ratio it touches and comes out as a verdict. |
    | `0x2`, `4d`  | one is JavaScript's, one is Java's, and neither is a number anybody types into a share link. |

    Leading and trailing space IS tolerated, because `Number()`
    trims and a link written by hand can carry one. */
internal fun readNumber(text: String): Double? {
    val trimmed = text.trim()
    if (!NUMBER.matches(trimmed)) return null
    val n = trimmed.toDoubleOrNull() ?: return null
    return if (n.isFinite()) n else null
}

private val NUMBER = Regex("""^[+-]?(\d+\.?\d*|\.\d+)([eE][+-]?\d+)?$""")

/* ---------- percent-encoding, application/x-www-form-urlencoded ----------

   `URLSearchParams.toString()` is form encoding, not the URI
   encoding `encodeURIComponent` does, and they differ in exactly
   the characters a Bangla company name is made of. A space is `+`
   here and `%20` there.

   Written out rather than taken from `java.net.URLEncoder`
   because that is a JVM class and `core` is shared: nothing in
   this module may reach for android.* or assume a JDK. */

private const val UNRESERVED = "*-._"

internal fun encodeQuery(text: String): String {
    val out = StringBuilder()
    for (byte in text.encodeToByteArray()) {
        val b = byte.toInt() and 0xFF
        when {
            b in 0x30..0x39 || b in 0x41..0x5A || b in 0x61..0x7A -> out.append(b.toChar())
            b == 0x20 -> out.append('+')
            b.toChar() in UNRESERVED -> out.append(b.toChar())
            else -> {
                out.append('%')
                out.append("0123456789ABCDEF"[b shr 4])
                out.append("0123456789ABCDEF"[b and 0x0F])
            }
        }
    }
    return out.toString()
}

internal fun decodeQuery(text: String): String {
    val out = ArrayList<Byte>(text.length)
    var i = 0
    while (i < text.length) {
        val c = text[i]
        val hex = if (c == '%' && i + 2 < text.length) {
            text.substring(i + 1, i + 3).toIntOrNull(16)
        } else {
            null
        }
        when {
            c == '+' -> { out.add(0x20); i += 1 }
            hex != null -> { out.add(hex.toByte()); i += 3 }
            /* Anything above ASCII arrived unencoded, which a
               careless sender does and a browser tolerates. Its
               own UTF-8 bytes, rather than dropped or mangled
               into one byte per char. */
            else -> { out.addAll(c.toString().encodeToByteArray().toList()); i += 1 }
        }
    }
    return out.toByteArray().decodeToString()
}

/**
 * One line of the answer, for a saved check's list row.
 *
 * `"68.0 · Worth accumulating"`, and it is stored so the account
 * can list a check without loading the model that produced it.
 *
 * ALWAYS IN ENGLISH, which is the site's choice and is the right
 * one: this string is written once and read for ever, and a
 * summary that remembers which language somebody happened to be
 * reading in on the day is a list that is half in each.
 */
fun summarise(a: Analysis, words: ToolWords?): String {
    val band = if (a.vetoed) "verdict.vetoed" else "verdict.${a.verdict.id}"
    val said = words?.t(band, "en").orEmpty().ifBlank { a.verdict.id }
    val score = a.score?.let { oneDecimal(it) } ?: "no score"
    return "$score · $said"
}

/** `toFixed(1)`, which is not `"%.1f"`: the platform default
    rounds half to even and JavaScript rounds half away from zero
    on a positive number. A score of 68.25 is `68.3` on the site
    and would be `68.2` here. */
private fun oneDecimal(v: Double): String {
    val scaled = kotlin.math.floor(kotlin.math.abs(v) * 10 + 0.5) / 10
    val sign = if (v < 0) "-" else ""
    val whole = scaled.toLong()
    val tenth = kotlin.math.round((scaled - whole) * 10).toLong()
    return "$sign$whole.$tenth"
}
