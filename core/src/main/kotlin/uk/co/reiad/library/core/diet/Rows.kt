package uk.co.reiad.library.core.diet

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/* ============================================================
   What a row of the diet tool is.

   Four tables in Supabase, all behind `auth.uid() = user_id`, and
   the app reads them with the reader's own token exactly as the
   routine does: there is no Worker in this path and there should
   not be one, because it would be a second thing holding
   somebody's private log.

   ---- absent is not nought, and it is the whole schema ----

   Every measurement here is nullable and every null MEANS
   something: a day with no weight is a day nobody stepped on the
   scale, which is different from a day they weighed zero, and the
   trend has to be able to tell them apart. `explicitNulls = false`
   on the encoder is what keeps a field somebody did not fill out
   of the write, rather than sending a null that would erase what
   is already there.

   That is the same rule the routine keeps about a zero mark, one
   table along, and it is why these are all `Double?` rather than
   `Double` with a sentinel.
   ============================================================ */

/**
 * One day. `entry_date` is the key, together with the reader.
 *
 * The measurement columns are what the reader typed and the
 * macro columns are the day's ROLLUP of its entries: two kinds of
 * fact in one row, which is right because they are both "what was
 * true on this date" and a reader edits both from the same page.
 */
@Serializable
data class DietDay(
    @SerialName("entry_date") val date: String,
    @SerialName("weight_kg") val weightKg: Double? = null,
    val kcal: Double? = null,
    @SerialName("protein_g") val proteinG: Double? = null,
    @SerialName("carbs_g") val carbsG: Double? = null,
    @SerialName("fat_g") val fatG: Double? = null,
    @SerialName("fibre_g") val fibreG: Double? = null,
    @SerialName("ketones_mmol") val ketonesMmol: Double? = null,
    val steps: Int? = null,
    @SerialName("water_ml") val waterMl: Int? = null,
    /** One to five, and NOT a mood: hunger is the early warning
        that a deficit is too deep, which is a reading rather than
        a feeling. */
    val hunger: Int? = null,
    @SerialName("waist_cm") val waistCm: Double? = null,
    @SerialName("hip_cm") val hipCm: Double? = null,
    @SerialName("neck_cm") val neckCm: Double? = null,
    val marks: List<String>? = null,
    val tags: List<String>? = null,
    val note: String? = null,
)

/** One thing eaten. Many rows per day, unlike everything else
    here, which is why it carries an `id` and the others do not. */
@Serializable
data class DietEntry(
    val id: String? = null,
    @SerialName("entry_date") val date: String,
    val label: String,
    @SerialName("label_bn") val labelBn: String? = null,
    val qty: Double? = null,
    val unit: String? = null,
    val kcal: Double? = null,
    val macros: Map<String, Double>? = null,
    /** The coverage nutrients, which the panel counts by
        PRESENCE. A key here with a nought in it buys the day a
        coverage the log has not got, so nothing may default one
        in: an absent nutrient is one nobody looked up. */
    val micros: Map<String, Double>? = null,
    val meal: String? = null,
    /** The local clock, "HH:MM". The hour a thing was eaten is a
        fact about the reader's own day, so it is stored beside
        the date rather than read back out of `meal`, which is a
        meal name. */
    @SerialName("at_time") val atTime: String? = null,
    /** A restaurant plate is not knowable. The midpoint goes into
        the total and the WIDTH goes into the day's confidence, so
        both ends are carried or neither is. */
    @SerialName("est_low") val estLow: Double? = null,
    @SerialName("est_high") val estHigh: Double? = null,
    val planned: Boolean? = null,
    /** Where the figure came from: a portion out of the library,
        the reader's own item, a barcode, or a free estimate. Kept
        so a stale figure can be found and refreshed rather than
        trusted for ever. One of the eight `diet_entries`'s own
        check constraint allows. */
    val source: String? = null,
    @SerialName("source_id") val sourceId: String? = null,
)

/** The reader's own answers to the setup questions. */
@Serializable
data class DietProfile(
    @SerialName("height_cm") val heightCm: Double? = null,
    @SerialName("birth_year") val birthYear: Int? = null,
    val sex: String? = null,
    val ancestry: String? = null,
    val place: String? = null,
    val activity: String? = null,
    val goal: String? = null,
    @SerialName("rate_pct") val ratePct: Double? = null,
    val lang: String? = null,
    @SerialName("setup_at") val setupAt: String? = null,
)

/** The body a profile and a day make together.

    Null when either half is missing, and that is the point: every
    figure in `Body.kt` needs a height, a weight, an age and a sex,
    and a tool that filled in an average for any of them would be
    describing a body that is not the reader's. */
fun bodyOf(profile: DietProfile?, day: DietDay?, thisYear: Int): Body? {
    val height = profile?.heightCm ?: return null
    val weight = day?.weightKg ?: return null
    val born = profile.birthYear ?: return null
    if (height <= 0 || weight <= 0) return null
    return Body(
        heightCm = height,
        weightKg = weight,
        ageYears = (thisYear - born).toDouble(),
        sex = if (profile.sex == "male") Sex.MALE else Sex.FEMALE,
        ancestry = if (profile.ancestry == "general") Ancestry.GENERAL else Ancestry.ASIAN,
        waistCm = day.waistCm,
        hipCm = day.hipCm,
        neckCm = day.neckCm,
    )
}

/* What a day adds up to is `totalFor` in `Foods.kt`, which is
   `shared/diet.ts`'s own `totalFor` ported whole.

   There were two little sums here, `totalOf` and `macroOf`, and
   they were the same arithmetic done more simply and therefore
   differently: neither filtered a PLANNED row, so a reader who
   planned tomorrow's dinner on the site read today's total as
   both days, and neither tracked coverage, which is the figure
   the nutrition page refuses to print a number without. */
