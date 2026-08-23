package uk.co.reiad.library.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import uk.co.reiad.library.core.BLURS
import uk.co.reiad.library.core.Finish
import uk.co.reiad.library.core.GLASSES
import uk.co.reiad.library.data.Held
import uk.co.reiad.library.core.MEASURES
import uk.co.reiad.library.core.Motion
import uk.co.reiad.library.core.SCALES
import uk.co.reiad.library.core.measureOf
import uk.co.reiad.library.core.scaleOf
import uk.co.reiad.library.core.PrefOption
import uk.co.reiad.library.core.Prefs
import uk.co.reiad.library.core.THEMES
import uk.co.reiad.library.core.VEILS

/* ============================================================
   What a reader can change, and what it does.

   Every setting on this sheet has a sentence under it saying what
   it will do. That is not padding: a setting whose effect a
   reader cannot predict is a setting they press once and never
   again, and half of these are about how a surface looks, which
   is exactly the kind of thing nobody experiments with blind.

   The tables are `core/Prefs.kt`, which holds the site's own ids,
   labels and notes. Nothing on this screen names a value.

   ---- three finishes, not three blurs ----

   `plain` is the one to keep working. It is what a reader who has
   asked their system for less transparency gets, and what anybody
   who finds moving text under a bar hard to read chooses. So the
   two sliders under it go quiet rather than pretending: a blur
   control on a finish with no blur is a control that lies.
   ============================================================ */

/** The two answers `tool-lang` can hold. Not in `core/Prefs.kt`
    beside the other tables because the VALUES are the stock
    screen's own vocabulary (`words.langs` is `["en","bn"]` from
    the manifest); what this sheet needs is only the site's two
    labels for them. */
private val TOOL_LANGS = listOf(
    PrefOption("bn", "বাংলা", "the calculators open in Bangla"),
    PrefOption("en", "English", "the calculators open in English"),
)

@Composable
fun SettingsSheet(
    prefs: Prefs,
    onChange: ((Prefs) -> Prefs) -> Unit,
    onClose: () -> Unit,
    /** The reader's calculator language. Separate from `prefs`
        because it lives under its own `tool-lang` key, which the
        calculators have read since before there were accounts. */
    onLang: (String) -> Unit = {},
    /** What the app is holding for offline reading, and the way
        to be rid of it. Optional so that this sheet can still be
        drawn on its own, which is what the render test does. */
    held: Held? = null,
    onForget: () -> Unit = {},
    /** Which build this is. Passed in rather than read from
        `BuildConfig` here, so the render test can pin a fixed
        string and the snapshot does not change on every commit. */
    builtFrom: String = uk.co.reiad.library.BuildConfig.BUILT_FROM,
) {
    val c = LocalReiad.current
    Sheet(onClose = onClose) { _ ->
        Text("Reading", style = MaterialTheme.typography.headlineSmall, color = c.ink)
        Spacer(Modifier.height(Gap.s3))
        Text(
            "These travel with your account, so a change here reaches the site too.",
            style = MaterialTheme.typography.bodySmall,
            color = c.inkSoft,
        )
        Spacer(Modifier.height(Gap.s8))

        /* Type size and line width FIRST, because they are
           the two a reader who cannot comfortably read the
           screen is looking for, and they were the two this
           sheet did not have. Both were stored, both synced,
           and neither did anything. */
        Choice(
            heading = "Type size",
            options = SCALES,
            chosen = scaleOf(prefs.text),
            onChoose = { scale -> onChange { it.copy(text = scale.id) } },
            label = { it.label },
            note = { it.note },
        )
        Spacer(Modifier.height(Gap.s8))

        Choice(
            heading = "Line width",
            options = MEASURES,
            chosen = measureOf(prefs.measure),
            onChoose = { measure -> onChange { it.copy(measure = measure.id) } },
            label = { it.label },
            note = { it.note },
        )
        Spacer(Modifier.height(Gap.s8))

        Choice(
            heading = "Theme",
            options = THEMES,
            chosen = prefs.themeChoice,
            onChoose = { theme -> onChange { it.copy(theme = theme.id) } },
            label = { it.label },
            note = { it.note },
        )
        Spacer(Modifier.height(Gap.s8))

        Choice(
            heading = "Glass",
            options = GLASSES,
            chosen = prefs.finish,
            onChoose = { finish -> onChange { it.copy(glass = finish.id) } },
            label = { it.label },
            note = { it.note },
        )
        Spacer(Modifier.height(Gap.s8))

        /* Both go quiet on `plain`, which is not glass, rather
           than staying live and doing nothing. */
        val glassy = prefs.finish != Finish.PLAIN
        Choice(
            heading = "Blur",
            options = BLURS,
            chosen = prefs.blurChoice,
            enabled = glassy,
            onChoose = { blur -> onChange { it.copy(blur = blur.id) } },
            label = { it.label },
            note = { it.note },
        )
        Spacer(Modifier.height(Gap.s8))

        Choice(
            heading = "Veil",
            options = VEILS,
            chosen = prefs.veilChoice,
            enabled = glassy,
            onChoose = { veil -> onChange { it.copy(veil = veil.id) } },
            label = { it.label },
            note = { it.note },
        )

        if (!glassy) {
            Spacer(Modifier.height(Gap.s5))
            Text(
                "Plain is not glass, so there is nothing for those two to move.",
                style = MaterialTheme.typography.bodySmall,
                color = c.inkSoft,
            )
        }

        Spacer(Modifier.height(Gap.s8))
        Choice(
            heading = "Calculators open in",
            options = TOOL_LANGS,
            chosen = prefs.lang,
            onChoose = { lang -> onLang(lang) },
            label = { it.label },
            note = { it.note },
        )

        /* Offline, and it is BELOW the reading preferences on
           purpose: those travel with the account and this one
           is about this handset only. Two different kinds of
           setting, and the sheet says so. */
        if (held != null) {
            SectionRule()
            Text("Offline", style = MaterialTheme.typography.headlineSmall, color = c.ink)
            Spacer(Modifier.height(Gap.s3))
            Text(
                "This one is about this phone and does not travel.",
                style = MaterialTheme.typography.bodySmall,
                color = c.inkSoft,
            )
            Spacer(Modifier.height(Gap.s7))
            HeldPanel(held, onForget)
        }

        /* WHICH BUILD THIS IS, at the foot of the one sheet
           every reader opens.

           Not decoration and not a version number nobody
           maintains: it is the commit, so a report about
           something not working can be matched to the code
           that was actually running. The failure it answers
           is worse than a wrong download. Android refuses to
           install an APK signed by a different key over one
           already installed, so a reader who taps install,
           sees "App not installed" and carries on is using
           the build from three releases ago while everybody
           believes the fix is being tested. */
        SectionRule()
        Text("This build", style = MaterialTheme.typography.headlineSmall, color = c.ink)
        Spacer(Modifier.height(Gap.s3))
        Text(
            builtFrom,
            style = MaterialTheme.typography.bodySmall,
            color = c.inkSoft,
            fontFamily = Faces.mono,
        )
        Spacer(Modifier.height(Gap.s3))
        Text(
            "If this does not match the build you were sent, the install did not "
                + "replace the old app. Uninstall it and install again: Android "
                + "refuses a new APK over one signed by a different key, and says "
                + "so only in a notice that is easy to miss.",
            style = MaterialTheme.typography.bodySmall,
            color = c.inkSoft,
        )
        Spacer(Modifier.height(Gap.s4))
    }
}

/** The rule between two kinds of setting: a hairline with air
    either side, which is how the sheet says "different subject"
    without another box. */
@Composable
private fun SectionRule() {
    val c = LocalReiad.current
    Spacer(Modifier.height(Gap.s9))
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(c.hairline),
    )
    Spacer(Modifier.height(Gap.s8))
}

/** One question, its answers, and what each answer does.

    A groove with one thumb riding in it — `Segmented` — so the
    whole set reads as one control rather than as three buttons
    that happen to be near each other, and the answer SLIDES to
    where it was sent. */
@Composable
private fun <T> Choice(
    heading: String,
    options: List<PrefOption<T>>,
    chosen: T,
    onChoose: (T) -> Unit,
    label: (PrefOption<T>) -> String,
    note: (PrefOption<T>) -> String,
    enabled: Boolean = true,
) {
    val c = LocalReiad.current
    val dim = if (enabled) 1f else 0.45f
    Column {
        Text(
            heading.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = c.inkSoft.copy(alpha = dim),
        )
        Spacer(Modifier.height(Gap.s4))
        Segmented(
            options = options,
            chosen = options.firstOrNull { it.id == chosen },
            onChoose = { onChoose(it.id) },
            label = label,
            enabled = enabled,
        )
        /* The sentence for the one that is CHOSEN, not all three.
           Three notes under three buttons is a paragraph nobody
           reads; one note under the answer you are on is a
           sentence saying what you just did — and it changes as
           softly as the thumb moves, because a line that blinks
           reads as an error. */
        options.firstOrNull { it.id == chosen }?.let { current ->
            if (note(current).isNotBlank()) {
                Spacer(Modifier.height(Gap.s4))
                AnimatedContent(
                    targetState = note(current),
                    transitionSpec = {
                        fadeIn(tween(Motion.FAST_MS)) togetherWith fadeOut(tween(Motion.QUICK_MS))
                    },
                    label = "note",
                ) { line ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            line,
                            style = MaterialTheme.typography.bodySmall,
                            color = c.inkSoft.copy(alpha = dim),
                        )
                    }
                }
            }
        }
    }
}
