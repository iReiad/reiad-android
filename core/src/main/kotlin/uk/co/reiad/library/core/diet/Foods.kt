package uk.co.reiad.library.core.diet

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.roundToLong

/* ============================================================
   The portion library, which this app never holds a copy of.

   ---- which half of the contract this is ----

   `CLAUDE.md`'s table: a food row is DATA, so it arrives from
   `/api/foods` and a dish added on the site is on this phone at
   the next fetch. `Body.kt` next door is the other half: that is
   arithmetic, ported, and a changed formula genuinely does need a
   release.

   The line matters most where it is easiest to cross. Eighty-three
   bilingual rows, each carrying a source citation, is exactly the
   list a second copy goes stale in: it would have looked right on
   the day it was written and been wrong the first week.

   ---- so NOTHING here names a nutrient ----

   Not one key. `per` is whatever the row states, `coverage` and
   `macros` are the two lists the endpoint sends, and `scaleTo`
   walks them. A nutrient added to `shared/foods.ts` next year is
   scaled, totalled and drawn by this build without it being
   rebuilt, and a Kotlin `data class` with nineteen fields on it
   could not do that: it would decode the new row perfectly and
   drop the new figure, silently, which is the failure at the top
   of the site's own house rules wearing a serialiser.

   That is why a `Portion` is built out of a `JsonObject` by hand
   rather than declared with `@Serializable`.

   ---- and an absent figure stays absent ----

   A nutrient nobody looked up for a dish is NOT in the map. It is
   never a nought, and no default may put one there: `DIET.md`
   §15's coverage arithmetic counts presence, so a zero standing in
   for a gap buys the day a coverage the log has not got. A
   measured nought (an oil states no sugar) is a figure and is
   kept, which is the same rule from the other side.
   ============================================================ */

/** How a unit is said, in each language. */
data class UnitWord(
    val en: String,
    /** Written out rather than derived, because deriving it gives
        "2 gs". */
    val ens: String,
    val bn: String,
    /** Bangla counts with the numeral attached: `২টা`, never
        `২ টা`. */
    val tight: Boolean = false,
)

/** A heading over a group of nutrients. */
data class NutrientGroup(val id: String, val en: String, val bn: String)

/**
 * One nutrient the panel draws, with the range to aim for and
 * whose range it is.
 *
 * BOTH ENDS ARE OPTIONAL ON PURPOSE. `low` alone is a floor,
 * `high` alone is a ceiling, and neither is a nutrient with no
 * single right amount: carbohydrate and fat are chosen rather
 * than prescribed, and drawing a range for them would tell a
 * reader on keto they were failing at something they decided to
 * do.
 */
data class Nutrient(
    val key: String,
    val group: String,
    /** `total`, `micros` or `both`: where the figure is in a
        day's total. Read rather than assumed, because `water` is
        both, the glasses being logged and the water in the food
        estimated. */
    val reads: String,
    val unit: String,
    val unitBn: String,
    val low: Double? = null,
    val high: Double? = null,
    val en: String,
    val bn: String,
    /** Why this reader is being shown it, in one sentence. */
    val whyEn: String,
    val whyBn: String,
    /** Whose reference intake the range is. A range with no
        population on it is a range a reader cannot argue with,
        and several of these differ between the UK, the WHO and
        the US by more than a rounding. */
    val refEn: String,
    val refBn: String,
) {
    fun say(lang: String): String = if (lang == "bn") bn else en
    fun unitSay(lang: String): String = if (lang == "bn") unitBn else unit
    fun why(lang: String): String = if (lang == "bn") whyBn else whyEn
    fun reference(lang: String): String = if (lang == "bn") refBn else refEn
}

/**
 * One row of the library.
 *
 * `per` is every nutrient the row states, under the site's own
 * keys, and `kcal` is out of it because every row has one and the
 * arithmetic reads it by name.
 */
data class Portion(
    /** A STORED KEY. It goes into `diet_entries.source_id` the
        moment the reader taps the row, so the site renames one at
        the cost of what somebody logged. Nothing here may derive
        it. */
    val id: String,
    val en: String,
    val bn: String,
    val place: List<String>,
    val qty: Double,
    val unit: String,
    /** What the row's own portion weighs, where it says. A row
        with no weight cannot answer a question in grams, which is
        a refusal rather than a gap: see `scaleTo`. */
    val grams: Double? = null,
    val kcal: Double,
    val per: Map<String, Double> = emptyMap(),
    /** The citation: which table the figures are out of. */
    val source: String? = null,
    val tags: List<String> = emptyList(),
    /* `raw` IS DELIBERATELY NOT CARRIED. The site's flag says
       whether the figures are for the food dry or cooked, and
       `foods.ts`'s own rule is that a row in scope states its
       state IN THE NAME, in both languages: "cooked white rice, 1
       cup" and "ভাত (রান্না করা)". A reader here is shown the
       name, and no arithmetic in this app branches on the flag,
       so decoding it would be a field carried and never drawn.
       The day something here needs to know, take it then. */
) {
    fun say(lang: String): String = if (lang == "bn") bn else en
}

/**
 * Everything `/api/foods` sends, and the four ways of reaching it.
 *
 * Held whole rather than sliced: which nutrients are macros and
 * which are counted for coverage are decisions the site has
 * already made, and an app working either out for itself would
 * agree today and disagree the first time a nutrient is added.
 */
class FoodLibrary(
    val place: String,
    val foods: List<Portion>,
    val nutrients: List<Nutrient>,
    val groups: List<NutrientGroup>,
    val units: Map<String, UnitWord>,
    /** The §15 coverage list: what a day reports the coverage of.
        The denominator is every logged item and the numerator is
        the ones whose row carries the key. */
    val coverage: List<String>,
    /** The four a day holds at the top level. */
    val macros: List<String>,
) {
    private val byId: Map<String, Portion> = foods.associateBy { it.id }
    private val byKey: Map<String, Nutrient> = nutrients.associateBy { it.key }

    fun byId(id: String): Portion? = byId[id]
    fun nutrient(key: String): Nutrient? = byKey[key]

    /** The rows for one place, in the order they were written. */
    fun forPlace(where: String): List<Portion> =
        foods.filter { it.place.contains(where) }

    /**
     * Rows matching `q` in English, in Bangla or in a tag, with
     * the reader's own place first.
     *
     * The other place's rows are still returned, and last. A
     * Bangladeshi reader in Manchester eats both lists, and a
     * search that hid the half they had not ticked would look
     * like a library that has never heard of dal.
     *
     * Ranked, not scored, and the ranking is `shared/foods.ts`'s
     * line for line: available beats not, a name that STARTS with
     * what was typed beats one that merely contains it, and ties
     * keep file order, which is stable.
     */
    fun search(q: String, where: String): List<Portion> {
        val needle = q.trim().lowercase()
        if (needle.isEmpty()) return forPlace(where)

        return foods.withIndex()
            .mapNotNull { (at, item) ->
                var best = -1
                for (field in listOf(item.en, item.bn) + item.tags) {
                    val found = field.lowercase().indexOf(needle)
                    if (found < 0) continue
                    best = if (best < 0) found else minOf(best, found)
                }
                if (best < 0) return@mapNotNull null
                val here = if (item.place.contains(where)) 0 else 2
                Triple(item, here + if (best == 0) 0 else 1, at)
            }
            .sortedWith(compareBy({ it.second }, { it.third }))
            .map { it.first }
    }

    /** The nutrients of one group, in the order they are drawn. */
    fun inGroup(group: String): List<Nutrient> = nutrients.filter { it.group == group }

    /**
     * The portion a figure is for: "2 biscuits", "২টা".
     *
     * An unknown unit falls back to the token itself, because a
     * portion reading "1 sachet" is readable and one reading "1"
     * is a number with nothing under it. The fallback is for a
     * row out of a public database rather than for this library.
     */
    fun portionWords(qty: String, unit: String, lang: String): String {
        val word = units[unit] ?: return "$qty $unit"
        if (lang == "bn") return qty + (if (word.tight) "" else " ") + word.bn
        return "$qty ${if (qty.toDoubleOrNull() == 1.0) word.en else word.ens}"
    }

    companion object {
        /**
         * Built from `/api/foods`, or null where the answer is not
         * one.
         *
         * Every field is read defensively and a row that cannot be
         * read is DROPPED rather than failing the whole library: a
         * phone installed in March reads a deploy from August, and
         * a library one row short is recoverable where one that
         * will not parse is not. The same rule `Board.kt` states
         * for the crossing in the other direction.
         */
        fun from(root: JsonObject): FoodLibrary? {
            val foods = (root["foods"] as? JsonArray)
                ?.mapNotNull { portionOf(it as? JsonObject ?: return@mapNotNull null) }
                ?: return null
            if (foods.isEmpty()) return null

            return FoodLibrary(
                place = root.text("place") ?: "bd",
                foods = foods,
                nutrients = (root["nutrients"] as? JsonArray)
                    ?.mapNotNull { nutrientOf(it as? JsonObject ?: return@mapNotNull null) }
                    .orEmpty(),
                groups = (root["groups"] as? JsonArray)
                    ?.mapNotNull { groupOf(it as? JsonObject ?: return@mapNotNull null) }
                    .orEmpty(),
                units = (root["units"] as? JsonObject)
                    ?.mapNotNull { (key, value) ->
                        val o = value as? JsonObject ?: return@mapNotNull null
                        val en = o.text("en") ?: return@mapNotNull null
                        key to UnitWord(
                            en = en,
                            ens = o.text("ens") ?: en,
                            bn = o.text("bn") ?: en,
                            tight = o["tight"]?.jsonPrimitive?.booleanOrNull ?: false,
                        )
                    }?.toMap().orEmpty(),
                coverage = root.strings("coverage"),
                macros = root.strings("macros"),
            )
        }

        /* The structural fields by name, and EVERYTHING ELSE
           numeric into `per`. That is what makes a nutrient added
           on the site arrive here without a release, and it is
           the whole reason this is not a `@Serializable` class. */
        /* Every field on a row that is NOT a figure. `also` is
           not here: it is a function in `foods.ts` that names a
           second table while the library is being written, and it
           never reaches the wire. A name in this list that the
           endpoint does not send is what `FoodSurfaceTest` calls
           a stale exemption. */
        private val NOT_A_NUTRIENT =
            setOf("id", "en", "bn", "place", "qty", "unit", "grams", "kcal",
                "source", "tags", "raw", "price", "currency", "pricedOn")

        private fun portionOf(o: JsonObject): Portion? {
            val id = o.text("id") ?: return null
            val en = o.text("en") ?: return null
            val qty = o.number("qty") ?: return null
            val unit = o.text("unit") ?: return null
            val kcal = o.number("kcal") ?: return null
            if (qty <= 0) return null

            val per = buildMap {
                for ((key, value) in o) {
                    if (key in NOT_A_NUTRIENT) continue
                    val n = (value as? JsonPrimitive)?.doubleOrNull ?: continue
                    put(key, n)
                }
            }

            return Portion(
                id = id,
                en = en,
                bn = o.text("bn") ?: en,
                place = o.strings("place"),
                qty = qty,
                unit = unit,
                grams = o.number("grams"),
                kcal = kcal,
                per = per,
                source = o.text("source"),
                tags = o.strings("tags"),
            )
        }

        private fun nutrientOf(o: JsonObject): Nutrient? {
            val key = o.text("key") ?: return null
            val en = o.text("en") ?: return null
            return Nutrient(
                key = key,
                group = o.text("group") ?: "other",
                reads = o.text("reads") ?: "micros",
                unit = o.text("unit").orEmpty(),
                unitBn = o.text("unitBn") ?: o.text("unit").orEmpty(),
                low = o.number("low"),
                high = o.number("high"),
                en = en,
                bn = o.text("bn") ?: en,
                whyEn = o.text("whyEn").orEmpty(),
                whyBn = o.text("whyBn") ?: o.text("whyEn").orEmpty(),
                refEn = o.text("refEn").orEmpty(),
                refBn = o.text("refBn") ?: o.text("refEn").orEmpty(),
            )
        }

        private fun groupOf(o: JsonObject): NutrientGroup? {
            val id = o.text("id") ?: return null
            val en = o.text("en") ?: return null
            return NutrientGroup(id, en, o.text("bn") ?: en)
        }

        private fun JsonObject.text(key: String): String? =
            (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

        private fun JsonObject.number(key: String): Double? =
            (this[key] as? JsonPrimitive)?.doubleOrNull

        private fun JsonObject.strings(key: String): List<String> =
            (this[key] as? JsonArray)
                ?.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }
                .orEmpty()
    }
}

/* ------------------------------------------------------------
   What was actually eaten, which is never what was found
   ------------------------------------------------------------ */

/** How much of it went in: a number, in the row's own unit or in
    grams. */
data class Ate(val n: Double, val unit: String)

/** A row at the amount that was eaten. */
data class ScaledPortion(
    val kcal: Double,
    /** Only the macros the row states. An absent one is absent
        here too, and a nought somebody measured is kept, because
        presence is the question and a nought is a figure. */
    val macros: Map<String, Double>,
    /** The coverage list, and only the keys the row carries. A
        key invented with a nought in it would buy the day a
        coverage the log has not got. */
    val micros: Map<String, Double>,
    /** What the row's own portion was multiplied by. */
    val factor: Double,
    /** What that weighs, where the row says what its portion
        weighs. */
    val grams: Double? = null,
)

/**
 * The row at the amount that was actually eaten, or `null`.
 *
 * NULL IS A REFUSAL AND IT IS THE POINT. A log wrong in the
 * flattering direction is the failure this tool is built around,
 * and a found food is stated per 100 g or per cup with no idea
 * what was on the plate. So an amount that cannot be turned into
 * a factor honestly (not a positive number, or grams asked of a
 * row that never says what it weighs) logs NOTHING, rather than
 * logging the row's own portion and hoping.
 *
 * EVERY nutrient is scaled by the one factor. Scaling the
 * calories alone is how a log ends up with a day of 2,400 kcal
 * and 40 g of protein in it.
 *
 * `macroKeys` and `microKeys` are the endpoint's own two lists
 * rather than anything named here, which is the sentence at the
 * top of this file: the split has to be the site's or the two
 * will scale different sets the day a nutrient is added.
 */
fun scaleTo(
    row: Portion,
    ate: Ate,
    macroKeys: List<String>,
    microKeys: List<String>,
): ScaledPortion? {
    val n = round(ate.n, 2)
    if (!n.isFinite() || n <= 0) return null

    /* The basis is what the row's figures are FOR: its own `qty`
       where the reader answered in its own unit, and its weight
       where they answered in grams. A row with no weight cannot
       answer a question in grams, and inventing one is the error
       this returns null for. */
    val basis = when {
        ate.unit == row.unit -> row.qty
        ate.unit == "g" -> row.grams
        else -> null
    }
    if (basis == null || basis <= 0) return null

    val factor = n / basis
    fun at(value: Double?): Double? = value?.let { round(it * factor, 2) }

    val macros = buildMap {
        for (key in macroKeys) at(row.per[key])?.let { put(key, it) }
    }
    val micros = buildMap {
        for (key in microKeys) at(row.per[key])?.let { put(key, it) }
    }

    return ScaledPortion(
        /* One decimal, which is what `diet_entries.kcal` holds. A
           figure the column would round is a row saying one thing
           and storing another. */
        kcal = round(row.kcal * factor, 1),
        macros = macros,
        micros = micros,
        factor = factor,
        grams = row.grams?.let { round(it * factor, 1) },
    )
}

/**
 * What the log stores, or `null` where `scaleTo` refused.
 *
 * `qty` is rounded to two places before anything is scaled by it,
 * because `diet_entries.qty` is `numeric(9,2)`: a row that scaled
 * by 1.333 and stored 1.33 is a row whose own numbers do not
 * follow from each other.
 *
 * BOTH NAMES ARE WRITTEN. `label` is the English and `labelBn`
 * the Bangla, always in that order whichever language the reader
 * has on: a Bangla name in the English column is a log that turns
 * to Bangla when the tool is switched to English and cannot be
 * switched back.
 */
fun loggedFrom(
    row: Portion,
    ate: Ate,
    date: String,
    library: FoodLibrary,
    /** The local clock, "HH:MM", or null. The hour a thing was
        eaten is a fact about the reader's own day, so it is
        stored beside the date rather than read back out of a meal
        name. */
    atTime: String? = null,
): DietEntry? {
    val scaled = scaleTo(row, ate, library.macros, library.coverage) ?: return null
    return DietEntry(
        date = date,
        label = row.en,
        labelBn = row.bn,
        qty = round(ate.n, 2),
        unit = ate.unit,
        kcal = scaled.kcal,
        macros = scaled.macros.ifEmpty { null },
        micros = scaled.micros.ifEmpty { null },
        atTime = atTime,
        /* `library`, which is one of the eight the table's own
           check constraint allows. A reader has to be able to
           tell a figure this site checked from one a stranger
           typed into a public database. */
        source = "library",
        sourceId = row.id,
    )
}

/* ------------------------------------------------------------
   A day, added up
   ------------------------------------------------------------ */

/** Under this share of the day, nothing is drawn.

    `shared/diet.ts`'s own constant and the most important
    sentence on the nutrition page: a confident number that is
    missing a third of the day is more dangerous than no number.
    Ported rather than fetched, because it is arithmetic. */
const val COVERAGE_FLOOR = 0.5

/**
 * What one day's entries come to, and how much of the day each
 * figure was worked out from.
 *
 * **COVERAGE IS WEIGHTED BY ENERGY, NEVER BY THE NUMBER OF
 * ENTRIES.** A logged 700 kcal restaurant plate with nothing
 * attached leaves a much bigger hole than a logged apple does,
 * and counting rows would call that day half known when a tenth
 * of it is. That is a mistake in the flattering direction, which
 * is the one this whole tool is built against, and it was in
 * this file for one draft.
 *
 * `microCoverage` is PER NUTRIENT and the day's own `coverage` is
 * not a substitute for it: a crowdsourced row may carry sodium
 * and nothing else, so the day reads well while four figures out
 * of five are drawn from a third of it.
 *
 * `shared/diet.ts`'s `totalFor`, line for line.
 */
data class DayTotal(
    val kcal: Double,
    /** The four a day holds at the top level, summed. */
    val macros: Map<String, Double>,
    val micros: Map<String, Double>,
    /** Per nutrient, by energy: the share of the day whose rows
        carry that key at all. */
    val microCoverage: Map<String, Double>,
    /** The share of the day with any composition attached. */
    val coverage: Double,
    /** How wide the day's estimates are, added up. A restaurant
        plate is not knowable: the midpoint goes into the total
        and this is what the page has to say beside it. */
    val spread: Double,
    val count: Int,
)

fun totalFor(entries: List<DietEntry>, macroKeys: List<String>): DayTotal {
    /* A planned row is what a reader intends to eat, not what
       they ate. The site's `Side` argument is not ported because
       this app has no plan page yet, and the filter IS: a
       tomorrow's plan silently added to today's total is a day
       that reads twice what it was. */
    val eaten = entries.filter { it.planned != true }

    val macros = mutableMapOf<String, Double>()
    val micros = mutableMapOf<String, Double>()
    val knownPer = mutableMapOf<String, Double>()
    var kcal = 0.0
    var known = 0.0
    var spread = 0.0

    for (entry in eaten) {
        val c = entry.kcal ?: 0.0
        kcal += c
        for (key in macroKeys) {
            val value = entry.macros?.get(key) ?: continue
            macros[key] = (macros[key] ?: 0.0) + value
        }
        val low = entry.estLow
        val high = entry.estHigh
        if (low != null && high != null) spread += high - low

        val carried = entry.micros
        if (!carried.isNullOrEmpty()) {
            known += c
            for ((key, value) in carried) {
                micros[key] = (micros[key] ?: 0.0) + value
                knownPer[key] = (knownPer[key] ?: 0.0) + c
            }
        }
    }

    val microCoverage = mutableMapOf<String, Double>()
    if (kcal > 0) for ((key, value) in knownPer) microCoverage[key] = value / kcal

    return DayTotal(
        kcal = round(kcal, 1),
        /* Summed at full precision and rounded once at the end.
           Rounding each row and adding the roundings is how a
           total stops matching the list under it. */
        macros = macros.mapValues { round(it.value, 1) },
        micros = micros.mapValues { round(it.value, 2) },
        microCoverage = microCoverage,
        coverage = if (kcal > 0) known / kcal else 0.0,
        spread = round(spread, 1),
        count = eaten.size,
    )
}

/** One nutrient's figure, and the share of the day it was drawn
    from.

    `amount` is NULL where the day says nothing about it, which is
    different from nought: "not known" and "none of it" are two
    different sentences and only one of them is about the food. */
data class Reading(val amount: Double?, val seen: Double) {
    /** Drawn from too little of the day to say "about". The page
        says "at least" instead, which is the true statement. */
    val thin: Boolean get() = amount != null && seen < COVERAGE_FLOOR
}

/**
 * Where a figure lives is read off the nutrient rather than
 * guessed: a macro is a top-level total, everything else is in
 * micros, and water is BOTH.
 *
 * WATER IS THE ONE THAT IS BOTH. The glasses are logged and
 * exact; the water in the food is estimated from the library and
 * is a fifth to a third of most days. Adding only the glasses is
 * a figure wrong by that much every day, and adding only the food
 * is a figure about somebody who does not drink.
 */
fun readingFor(of: Nutrient, day: DayTotal, drunkMl: Double = 0.0): Reading = when (of.reads) {
    "total" -> Reading(day.macros[of.key], day.coverage)
    "micros" -> Reading(day.micros[of.key], day.microCoverage[of.key] ?: 0.0)
    else -> {
        val food = day.micros[of.key]
        val seen = day.microCoverage[of.key] ?: 0.0
        if (food == null && drunkMl == 0.0) Reading(null, seen)
        else Reading(drunkMl + (food ?: 0.0), seen)
    }
}

/** Is there enough of the day known to draw anything at all?

    Under half, the site draws NOTHING, and this is the sentence
    it draws instead. Ported because it is the page's own most
    important rule and a phone that drew the figures anyway would
    be the more dangerous of the two. */
fun tooSparse(day: DayTotal): Boolean = day.count > 0 && day.coverage < COVERAGE_FLOOR

/** Rounded on the way out: a float artefact on a page
    ("0.21000000000000002 g") makes a tool look broken. */
private fun round(value: Double, places: Int): Double {
    if (!value.isFinite()) return value
    var scale = 1.0
    repeat(places) { scale *= 10 }
    return (value * scale).roundToLong() / scale
}
