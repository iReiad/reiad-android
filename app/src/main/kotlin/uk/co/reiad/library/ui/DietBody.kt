package uk.co.reiad.library.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import uk.co.reiad.library.core.DietWords
import uk.co.reiad.library.core.stock.inScript
import uk.co.reiad.library.core.diet.Body
import uk.co.reiad.library.core.diet.FatMethod
import uk.co.reiad.library.core.diet.bmi
import uk.co.reiad.library.core.diet.bmiBand
import uk.co.reiad.library.core.diet.fatEstimate
import uk.co.reiad.library.core.diet.ffmiNormalised
import uk.co.reiad.library.core.diet.restingBurn
import uk.co.reiad.library.core.diet.whtr
import uk.co.reiad.library.core.diet.whtrBand
import kotlin.math.roundToInt

/* ============================================================
   What the numbers say about you: the diet tool's `you` page.

   ---- nothing here is a point estimate ----

   `DIET.md`'s own rule and the reason this screen is worth
   having at all. Body fat comes back as a RANGE, because a tape
   measure and a formula cannot support anything narrower, and
   printing one decimal place would be making it up. The site
   prints the range and so does this.

   ---- and the words are the site's ----

   Every explanation comes down in `dietWords`: the phrases by
   id, and the BANDS by the token the arithmetic returns. So a
   band this app computed has a sentence without this app holding
   a single Bangla sentence about bodies.

   A phrase that has not arrived draws NOTHING rather than its
   own key. A figure with no explanation under it is a figure; a
   figure with `dt.bmi.why` under it is a bug somebody has to
   report, and this screen is one a reader may take to a
   clinician.

   ---- what it does not do ----

   It does not judge. Not one line here says a number is good or
   bad: every reading is the figure, the band it falls in as the
   READER'S OWN cut-offs define it, and what the method can
   support. `check-diet.ts` holds the site to that and the same
   sentences are what arrive here.
   ============================================================ */

@Composable
fun DietBodyPanel(
    body: Body?,
    words: DietWords,
    lang: String,
    onOpenSite: () -> Unit,
    modifier: Modifier = Modifier,
    /** The smoothed weight and the week's slope, where a
        fortnight of mornings can support them. */
    trendKg: Double? = null,
    perWeek: uk.co.reiad.library.core.diet.Range? = null,
) {
    val c = LocalReiad.current

    if (body == null) {
        /* Not a blank and not a row of dashes. The whole screen
           needs a height, a weight, an age and a sex, and a tool
           that filled in an average for any of them would be
           describing a body that is not the reader's. */
        InfoCard(
            title = if (lang == "bn") "মাপগুলো দিলে এখানে হিসাব হবে" else "Your readings appear here",
            dek = if (lang == "bn") {
                "উচ্চতা, ওজন আর জন্মসাল লাগবে। গড় দিয়ে ভরাট করা হয় না: তাহলে হিসাবটা আপনার হতো না।"
            } else {
                "It needs a height, a weight and a year of birth. Nothing is filled " +
                    "in with an average, because then the figures would not be yours."
            },
            modifier = modifier,
        ) {
            Spacer(Modifier.height(Gap.s6))
            PillButton(
                if (lang == "bn") "সাইটে গিয়ে দিন" else "Set them on the site",
                onOpenSite,
                icon = "arrow",
            )
        }
        return
    }

    val fat = fatEstimate(body)
    val rest = restingBurn(body, if (fat.method == FatMethod.NAVY) fat.leanKg else null)

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Gap.s6)) {
        words.say("dt.body.head", lang)?.let {
            Text(
                it,
                style = MaterialTheme.typography.titleSmall,
                color = c.ink,
                modifier = Modifier.semantics { heading() },
            )
        }

        /* The TREND leads, where there is one, because it is the
           screen's own advice taken: the weight box says "one
           reading is noise, the trend is the signal", and a page
           that said so while drawing no trend was carrying the
           arithmetic and never showing it. The slope is a RANGE,
           because a fit over a fortnight cannot support a point,
           and the words state a DIRECTION and never a verdict. */
        trendKg?.let { kg ->
            Reading(
                head = if (lang == "bn") "ওজনের গতি" else "Weight trend",
                value = if (lang == "bn") {
                    "${inScript(one(kg), "bn")} কেজি"
                } else {
                    "${one(kg)} kg"
                },
                said = perWeek?.let { slopeWords(it, lang) },
                why = if (lang == "bn") {
                    "দিনের ওঠানামা মসৃণ করে হিসাব করা: এক পাক্ষিকের সকালগুলো থেকে।"
                } else {
                    "Smoothed over the last fortnight's mornings, so one salty " +
                        "dinner does not read as a kilogram."
                },
            )
        }

        /* Waist to height FIRST, and that is the site's own
           ordering rather than a preference: it makes no
           assumption about population, which is exactly the
           property BMI lacks. */
        body.waistCm?.let { waist ->
            val ratio = whtr(waist, body.heightCm)
            Reading(
                head = words.say("dt.whtr.head", lang),
                value = two(ratio),
                said = words.whtrBand(whtrBand(ratio).id, lang),
                why = words.say("dt.whtr.why", lang),
            )
        } ?: Reading(
            head = words.say("dt.whtr.head", lang),
            /* The words, not a dash. A figure nobody measured is
               "not known", which is a sentence; a rule laid on
               its side is a reader guessing whether the tool is
               broken. */
            value = if (lang == "bn") "জানা নেই" else "not known",
            said = words.say("dt.whtr.empty", lang),
            why = null,
        )

        val value = bmi(body.weightKg, body.heightCm)
        Reading(
            head = words.say("dt.bmi.head", lang) ?: "BMI",
            value = one(value),
            /* The band off the READER'S cut-offs, never a fixed
               set: `DIET.md` section 2, and the whole reason
               `ancestry` is on a `Body` at all. */
            said = words.bmiBand(bmiBand(value, body.ancestry).id, lang),
            why = words.say("dt.bmi.why", lang),
            note = words.cutSet(body.ancestry.id, lang),
        )

        Reading(
            head = words.say("dt.fat.head", lang),
            /* A RANGE, because that is what the method supports.
               Anything printing a decimal place here is making it
               up, which is `DIET.md`'s own sentence. */
            value = if (lang == "bn") {
                "${inScript(fat.pct.low.roundToInt().toString(), "bn")} – ${inScript(fat.pct.high.roundToInt().toString(), "bn")}%"
            } else {
                "${fat.pct.low.roundToInt()} to ${fat.pct.high.roundToInt()}%"
            },
            said = words.say(
                if (fat.method == FatMethod.NAVY) "dt.fat.navy" else "dt.fat.bmi",
                lang,
            ),
            why = words.say("dt.fat.why", lang),
        )

        Reading(
            head = words.say("dt.lean.head", lang),
            value = if (lang == "bn") "${inScript(one(fat.leanKg), "bn")} কেজি" else "${one(fat.leanKg)} kg",
            said = "FFMI ${one(ffmiNormalised(fat.leanKg, body.heightCm))}",
            why = words.say("dt.lean.why", lang),
        )

        Reading(
            head = if (lang == "bn") "বিশ্রামে খরচ" else "Resting burn",
            value = "${(rest.kcal / 10).roundToInt() * 10} kcal",
            said = words.sexForm(body.sex.id, lang),
            why = null,
        )

        /* Section 31: every page that prints a figure about a
           body prints, beside it, that this is general education
           and not medical advice. In BOTH languages. */
        Spacer(Modifier.height(Gap.s4))
        Text(
            if (lang == "bn") {
                "এটা সাধারণ শিক্ষা, চিকিৎসা পরামর্শ নয়।"
            } else {
                "This is general education and not medical advice."
            },
            style = MaterialTheme.typography.bodySmall,
            color = c.inkSoft,
        )
    }
}

/** One figure, with what it means under it.

    A `plate`: it is read and never pressed, which is the site's
    own rule for a statistic and the reason it does not lift or
    take a light under a finger. */
@Composable
private fun Reading(
    head: String?,
    value: String,
    said: String?,
    why: String?,
    note: String? = null,
) {
    val c = LocalReiad.current
    /* NO NAME, NO PLATE. Three heads on this page come down as
       data with the site's other words, and `say()` answers null
       until the table has arrived, so a phone meeting a manifest
       without them drew the figure anyway: "0.51" alone in a
       box, which a reader cannot interpret and a clinician can
       only frown at. A number without its name is decoration,
       and the page is honest with fewer readings. Every reading
       that must always show passes a literal or a fallback. */
    if (head.isNullOrBlank()) return
    Plate(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                head?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = c.accent)
                    Spacer(Modifier.height(Gap.s2))
                }
                Text(value, style = MaterialTheme.typography.headlineSmall, color = c.ink)
            }
            note?.let {
                Spacer(Modifier.width(Gap.s5))
                Text(it, style = MaterialTheme.typography.labelSmall, color = c.inkSoft)
            }
        }
        said?.let {
            Spacer(Modifier.height(Gap.s3))
            Text(it, style = MaterialTheme.typography.bodyMedium, color = c.inkSoft)
        }
        /* Absent rather than a key. A reading with no explanation
           is a reading; one saying `dt.bmi.why` is a defect on a
           page a reader may hand to a clinician. */
        why?.let {
            Spacer(Modifier.height(Gap.s3))
            Text(it, style = MaterialTheme.typography.bodySmall, color = c.inkSoft)
        }
    }
}

/** The week's slope, said as a direction with its width, never a
    verdict. `±` where the range straddles nought, because "steady"
    would be a claim the fit cannot support either way. */
private fun slopeWords(perWeek: uk.co.reiad.library.core.diet.Range, lang: String): String {
    val lo = perWeek.low
    val hi = perWeek.high
    val n = { v: Double -> if (lang == "bn") inScript(one(kotlin.math.abs(v)), "bn") else one(kotlin.math.abs(v)) }
    return when {
        hi < 0 ->
            if (lang == "bn") "সপ্তাহে ${n(hi)}–${n(lo)} কেজি কমছে"
            else "down ${n(hi)} to ${n(lo)} kg a week"
        lo > 0 ->
            if (lang == "bn") "সপ্তাহে ${n(lo)}–${n(hi)} কেজি বাড়ছে"
            else "up ${n(lo)} to ${n(hi)} kg a week"
        else ->
            if (lang == "bn") "সপ্তাহে ±${n(maxOf(-lo, hi))} কেজির মধ্যে"
            else "within ±${n(maxOf(-lo, hi))} kg a week"
    }
}

private fun one(x: Double): String = String.format("%.1f", x)

private fun two(x: Double): String = String.format("%.2f", x)
