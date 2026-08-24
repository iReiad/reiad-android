package uk.co.reiad.library.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import uk.co.reiad.library.core.Kind
import uk.co.reiad.library.core.diet.Ate
import uk.co.reiad.library.core.diet.FoodLibrary
import uk.co.reiad.library.core.diet.Portion
import uk.co.reiad.library.core.diet.scaleTo
import uk.co.reiad.library.core.stock.inScript

/* ============================================================
   Adding what you ate, out of the site's own portion library.

   ---- what this closes ----

   The app could show a day's log and take a weight off the scale
   and could not add a single thing to it. A log you cannot write
   to is a screen that describes a tool rather than being one, and
   the card that used to sit here said as much out loud: "the food
   search is on the site".

   ---- and NOT ONE FOOD IS NAMED IN THIS FILE ----

   Or in `Foods.kt`, or anywhere in this app. The rows arrive from
   `/api/foods`, so a dish added on the site is on this phone at
   the next fetch, and a nutrient added to a row is scaled and
   totalled by this build with nothing published. That is
   `CLAUDE.md`'s data-and-code table holding for the largest table
   the site has.

   ---- an amount is a refusal or a figure ----

   `scaleTo` returns null rather than guessing, and this screen
   shows that as an Add that will not press, with the reason under
   it. The refusals are the point: a log wrong in the flattering
   direction is what the whole tool is built against, and grams
   asked of a row that never says what its portion weighs is
   exactly the shape of a number nobody measured.
   ============================================================ */

@Composable
fun FoodPicker(
    library: FoodLibrary?,
    /** Where the reader eats, off their own profile. It decides
        which half of the library leads; the other half is still
        returned, and last. A Bangladeshi reader in Manchester
        eats both lists. */
    place: String,
    lang: String,
    onAdd: (Portion, Ate) -> Unit,
    onClose: () -> Unit,
    onOpenSite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalReiad.current
    var typed by remember { mutableStateOf("") }
    var chosen by remember { mutableStateOf<Portion?>(null) }

    if (library == null) {
        /* Not an empty list, and not a promise either.

           This used to say the list arrives once and then stays
           on the phone, so the only way to be here was a first
           run with no signal. That sentence stopped being true:
           `/api/foods` answers 404 on the site today, so the
           list does not arrive at all, and a reader who waited
           for it was waiting for nothing. The site's own food
           pages DO work, which is what this now says and what
           the button under it opens. */
        InfoCard(
            title = if (lang == "bn") "খাবারের তালিকা এই ফোনে নেই" else "The food list is not on this phone",
            dek = if (lang == "bn") {
                "সাইটের খাবারের পাতাগুলো কাজ করছে: খোঁজা, বারকোড, আর দুটো বড় ডাটাবেস। " +
                    "যোগ করলে এখানেও দেখা যাবে।"
            } else {
                "The site's food pages work: search, the barcode scanner and the two " +
                    "public databases. Anything added there shows up here."
            },
            modifier = modifier,
        ) {
            Spacer(Modifier.height(Gap.s6))
            PillButton(
                if (lang == "bn") "সাইটে খুঁজুন" else "Search on the site",
                onOpenSite,
                icon = "arrow",
            )
        }
        return
    }

    val hits = remember(typed, place, library) { library.search(typed, place) }

    /* The head does NOT scroll away and the list does. A picker
       whose search box leaves the screen after four rows is one
       where correcting a typo means scrolling back up, and this
       list is eighty-three rows long. */
    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(Gap.s5)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (lang == "bn") "কী খেলেন" else "What did you eat",
                style = MaterialTheme.typography.titleSmall,
                color = c.ink,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            PillButton(
                label = if (lang == "bn") "বন্ধ" else "Close",
                onClick = onClose,
                icon = "close",
            )
        }

        Field(
            value = typed,
            onValue = { typed = it; chosen = null },
            description = if (lang == "bn") "খাবার খুঁজুন" else "Search the food list",
            hint = if (lang == "bn") "ভাত, ডাল, ডিম…" else "rice, dal, egg…",
            icon = "search",
            keyboard = KeyboardOptions(imeAction = ImeAction.Search),
        )

        val picked = chosen
        if (picked != null) {
            Amount(
                row = picked,
                library = library,
                lang = lang,
                onAdd = { ate -> onAdd(picked, ate); chosen = null; typed = "" },
                onBack = { chosen = null },
            )
            return@Column
        }

        if (hits.isNotEmpty()) {
            /* Lazy, because it is the whole library until
               something is typed. Composing eighty-three rows to
               show six is the shape of a list that scrolls
               badly on the phone somebody actually has. */
            LazyColumn(
                Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(Gap.s3),
            ) {
                items(hits, key = { it.id }) { row ->
                    Hit(
                        row = row,
                        library = library,
                        lang = lang,
                        away = place !in row.place,
                        onChoose = { chosen = row },
                    )
                }
            }
            return@Column
        }

        /* The library is short on purpose: §22 is a list of
               what people actually eat rather than a food
               database. So a miss is expected and it says where
               the barcode scanner and the two public databases
               are, rather than reading as a failure. */
        InfoCard(
            title = if (lang == "bn") "এই তালিকায় নেই" else "Not in this list",
            dek = if (lang == "bn") {
                "এই তালিকাটা ছোট রাখা হয়েছে: যা মানুষ আসলে খায় তার একটা তালিকা, " +
                    "খাবারের ডাটাবেস নয়। বারকোড আর বড় দুটো ডাটাবেস সাইটে আছে।"
            } else {
                "This list is deliberately short: what people actually eat, not a " +
                    "food database. Barcodes and the two public databases are on the site."
            },
        ) {
            Spacer(Modifier.height(Gap.s6))
            PillButton(
                if (lang == "bn") "সাইটে খুঁজুন" else "Search on the site",
                onOpenSite,
                icon = "arrow",
            )
        }
    }
}

/* ---------- one row of the library ---------- */

@Composable
private fun Hit(
    row: Portion,
    library: FoodLibrary,
    lang: String,
    /** From the other place's list. Marked rather than hidden:
        the reader can see why it is here and lower down. */
    away: Boolean,
    onChoose: () -> Unit,
) {
    val c = LocalReiad.current
    Tap(onClick = onChoose, label = row.say(lang)) {
        Rung {
            Column(Modifier.weight(1f)) {
                Text(
                    row.say(lang),
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.ink,
                )
                Spacer(Modifier.height(Gap.s2))
                Text(
                    /* Both figures in the reader's own digits.
                       `portionWords` takes the numeral already
                       converted, which is the site's own contract
                       for it: one place decides the script. */
                    library.portionWords(inScript(trim(row.qty), lang), row.unit, lang) +
                        " · ${inScript(row.kcal.toInt().toString(), lang)} kcal" +
                        (if (away) " · ${if (lang == "bn") "অন্য দেশের" else "other list"}" else ""),
                    style = MaterialTheme.typography.labelSmall,
                    color = c.inkSoft,
                )
            }
            Icon("chevron", size = 14.dp, tint = c.inkSoft)
        }
    }
}

/* ---------- how much of it ---------- */

@Composable
private fun Amount(
    row: Portion,
    library: FoodLibrary,
    lang: String,
    onAdd: (Ate) -> Unit,
    onBack: () -> Unit,
) {
    val c = LocalReiad.current

    /* Its own unit first, and grams only where the row says what
       its portion weighs. A unit that cannot be answered is not
       offered: an option that always refuses is a control that
       looks broken. */
    val units = remember(row) {
        buildList {
            add(row.unit)
            if (row.grams != null && row.unit != "g") add("g")
        }
    }
    var unit by remember(row) { mutableStateOf(units.first()) }
    var typed by remember(row) { mutableStateOf(if (unit == "g") "" else trim(row.qty)) }

    val ate = typed.toDoubleOrNull()?.let { Ate(it, unit) }
    val scaled = ate?.let { scaleTo(row, it, library.macros, library.coverage) }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Gap.s5)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                row.say(lang),
                style = MaterialTheme.typography.titleSmall,
                color = c.ink,
                modifier = Modifier.weight(1f),
            )
            PillButton(
                label = if (lang == "bn") "ফিরে" else "Back",
                onClick = onBack,
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(110.dp)) {
                Field(
                    value = typed,
                    onValue = { typed = it },
                    filter = decimalsOnly,
                    description = if (lang == "bn") "কতটা খেয়েছেন" else "How much of it you ate",
                    hint = if (lang == "bn") "কত" else "how much",
                    keyboard = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done,
                    ),
                )
            }
            Spacer(Modifier.width(Gap.s5))
            /* One control even where there is one unit, because a
               row that offers grams and one that cannot must not
               be two different shapes of screen. */
            Box(Modifier.weight(1f)) {
                Segmented(
                    options = units,
                    chosen = unit,
                    onChoose = { unit = it },
                    height = Gap.tap,
                    label = { library.portionWords("", it, lang).trim() },
                ) { option, on ->
                    Text(
                        library.portionWords("", option, lang).trim().ifEmpty { option },
                        style = MaterialTheme.typography.labelLarge,
                        color = if (on) c.paper else c.inkSoft,
                    )
                }
            }
        }

        if (scaled != null) {
            /* What it comes to, before it is logged. A figure a
               reader can check against the packet is a figure they
               can correct; one that appears in the list a second
               later is one they take on trust. */
            Plate(Modifier.fillMaxWidth()) {
                Text(
                    "${inScript(scaled.kcal.toInt().toString(), lang)} kcal",
                    style = MaterialTheme.typography.headlineSmall,
                    color = c.ink,
                )
                val said = library.macros.mapNotNull { key ->
                    val value = scaled.macros[key] ?: return@mapNotNull null
                    val n = library.nutrient(key) ?: return@mapNotNull null
                    "${n.say(lang)} ${inScript(trim(value), lang)}${n.unitSay(lang)}"
                }
                if (said.isNotEmpty()) {
                    Spacer(Modifier.height(Gap.s3))
                    Text(
                        said.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = c.inkSoft,
                    )
                }
                row.source?.let {
                    Spacer(Modifier.height(Gap.s3))
                    /* Where the number came from, on the row that
                       is about to be logged. Almost no app shows
                       this, and it is the difference between a
                       figure out of a government laboratory and
                       one a stranger typed in. */
                    Text(it, style = MaterialTheme.typography.labelSmall, color = c.inkSoft)
                }
            }
        } else {
            Text(
                /* The refusal, written out. A control that will
                   not press and says nothing is a bug as far as
                   the reader is concerned. */
                if (lang == "bn") {
                    "একটা সংখ্যা দিন। এই সারিটার ওজন জানা না থাকলে গ্রামে হিসাব হয় না।"
                } else {
                    "Type an amount. Grams need a row that says what its portion weighs."
                },
                style = MaterialTheme.typography.bodySmall,
                color = c.inkSoft,
            )
        }

        PillButton(
            label = if (lang == "bn") "যোগ করুন" else "Add it",
            kind = if (scaled != null) ButtonKind.SOLID else ButtonKind.GHOST,
            onClick = { ate?.let(onAdd) },
            icon = "check",
        )
    }
}

/** A number with no trailing nought on it: "1" rather than "1.0",
    and "1.5" kept. A portion reading "1.0 cup" is a tool that has
    not been looked at. */
private fun trim(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
