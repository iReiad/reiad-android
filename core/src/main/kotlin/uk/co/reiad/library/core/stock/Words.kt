package uk.co.reiad.library.core.stock

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs

/* ============================================================
   Every word the stock check says, and how to print a number
   beside it.

   ---- the split, which is the whole of the contract ----

   The WORDS come down from `/api/tools`. They are data: 366
   phrases in two languages out of `shared/tool-strings.ts`, and
   an edited Bangla sentence reaches this app on the next fetch
   with nothing to publish.

   The FORMATTERS are here. They are arithmetic, so they are code
   on both sides and a change to one needs a release. That is the
   line CLAUDE.md draws, and this file is the two halves of it in
   one place so a reader can see which is which.

   ---- why the digits are a table and not `NumberFormat` ----

   Android's ICU has `bn-BD` and would produce Bengali digits, and
   it would be the wrong bet: what the site produces is what a
   READER has already seen, and the two ICUs are different
   versions. A grouping that changed between them would print the
   same number two ways on two devices for no reason anybody could
   find. So the grouping is written out and the digits are a
   sixteen-character lookup.
   ============================================================ */

/** One phrase, in both languages. Both are required on the site,
    where the type says so; here both are optional, because this
    arrives over a wire from a deploy that may be newer than this
    build and a missing half must fall back rather than crash. */
@Serializable
data class Phrase(val en: String? = null, val bn: String? = null)

@Serializable
data class ToolWords(
    val langs: List<String> = listOf("en", "bn"),
    @SerialName("strings") val strings: Map<String, Phrase> = emptyMap(),
) {
    /** The phrase for a key, in a language, with `{name}` filled
        in.

        The key itself when there is no such phrase, which is what
        the site does and is the right answer: a reader seeing
        `verdict.buy` knows something is missing, and a reader
        seeing an empty box does not. */
    fun t(key: String, lang: String = "en", vars: Map<String, String> = emptyMap()): String {
        val entry = strings[key] ?: return key
        var s = (if (lang == "bn") entry.bn else entry.en) ?: entry.en ?: return key
        for ((k, v) in vars) s = s.replace("{$k}", v)
        return s
    }

    fun has(key: String): Boolean = key in strings
}

/* ---------- the digits ---------- */

private const val BENGALI_DIGITS = "০১২৩৪৫৬৭৮৯"

/** A Latin numeral string in the reader's own digits. */
fun inScript(text: String, lang: String): String =
    if (lang != "bn") text
    else buildString {
        for (c in text) append(if (c in '0'..'9') BENGALI_DIGITS[c - '0'] else c)
    }

/** Thousands separated the way both languages here group them.

    English groups in threes. Bangla groups in the South Asian
    way: the last three, then twos, so ten lakh is `১০,০০,০০০`
    and not `১,০০০,০০০`. Getting that wrong is the sort of thing
    a reader notices immediately and cannot explain, and it is
    the reason this is not a `%,d`. */
internal fun group(digits: String, lang: String): String {
    if (digits.length <= 3) return digits
    val head = digits.dropLast(3)
    val tail = digits.takeLast(3)
    if (lang != "bn") {
        val parts = mutableListOf(tail)
        var rest = head
        while (rest.length > 3) {
            parts += rest.takeLast(3)
            rest = rest.dropLast(3)
        }
        parts += rest
        return parts.reversed().joinToString(",")
    }
    val parts = mutableListOf(tail)
    var rest = head
    while (rest.length > 2) {
        parts += rest.takeLast(2)
        rest = rest.dropLast(2)
    }
    if (rest.isNotEmpty()) parts += rest
    return parts.reversed().joinToString(",")
}

/** The dash a number that is not there prints as.

    An en dash, U+2013, which is what the site prints and what
    this repository's house rules leave alone. Never an em dash,
    and never an empty string: a blank cell reads as a number
    somebody forgot to fill in. */
const val NOTHING = "–"

/* ---- rounding, which is the one thing here that is subtle ----

   `Intl.NumberFormat` does NOT round the double. It rounds the
   double's SHORTEST DECIMAL form, half away from zero. Those are
   different answers, and 9.995 is where they part: the stored
   double is 9.99499999999999957, so rounding the value gives 9.99
   and rounding the string "9.995" gives 10.00. The site prints
   10.00.

   `BigDecimal.valueOf` is specified as the decimal of
   `Double.toString`, which is that shortest form, so this is the
   same two steps and not an approximation of them. Multiplying by
   a power of ten and rounding, which is the obvious way to write
   this, gets 9.99 and is wrong on every tie in the model. */
private fun fixed(v: Double, digits: Int, lang: String): String {
    if (!v.isFinite()) return NOTHING
    val rounded = BigDecimal.valueOf(v).setScale(digits, RoundingMode.HALF_UP)
    val plain = rounded.abs().toPlainString()
    val whole = plain.substringBefore('.')
    val frac = plain.substringAfter('.', "")
    val body = if (frac.isEmpty()) group(whole, lang) else "${group(whole, lang)}.$frac"
    /* A negative that rounds to nothing keeps its sign, because
       `Intl` does: -0.4 to no decimals is "-0", and a reader
       looking at a column of returns needs to see that it was a
       loss rather than a nought. */
    return inScript(if (v < 0) "-$body" else body, lang)
}

fun fmtNum(v: Double, lang: String = "en", digits: Int = 2): String = fixed(v, digits, lang)

fun fmtInt(v: Double, lang: String = "en"): String = fixed(v, 0, lang)

/** A metric's raw value, printed the way its `fmt` hint asks.

    The hints are the site's: `x` for a multiple, `%` for a
    percentage, `pp` for a difference BETWEEN two percentages
    (which carries its sign, because "+3 pp" and "3%" are
    different claims), `lakh` for money, `n` for a plain number. */
fun fmtValue(v: Double, kind: String, lang: String = "en"): String {
    if (!v.isFinite()) return NOTHING
    return when (kind) {
        "x" -> "${fmtNum(v, lang, 2)}×"
        "%" -> "${fmtNum(v, lang, 1)}%"
        "pp" -> "${if (v > 0) "+" else ""}${fmtNum(v, lang, 1)} pp"
        "lakh" -> fmtInt(v, lang)
        else -> fmtNum(v, lang, 2)
    }
}

/** Taka figures held in lakh, printed at whatever scale reads
    best. A hundred lakh is a crore, and past that nobody reads
    lakh any more. */
fun fmtLakh(v: Double, words: ToolWords, lang: String = "en"): String {
    if (!v.isFinite()) return NOTHING
    val magnitude = abs(v)
    if (magnitude >= 100) {
        val crore = fmtNum(v / 100, lang, if (magnitude >= 10000) 0 else 1)
        return "৳$crore ${words.t("t.crore", lang)}"
    }
    return "৳${fmtInt(v, lang)} ${if (lang == "bn") "লাখ" else "lakh"}"
}

fun fmtTk(v: Double, lang: String = "en", digits: Int = 2): String =
    if (v.isFinite()) "৳${fmtNum(v, lang, digits)}" else NOTHING
