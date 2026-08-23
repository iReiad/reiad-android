package uk.co.reiad.library.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Arrangement
import uk.co.reiad.library.read.REMIND_TIMES
import uk.co.reiad.library.core.BLURS
import uk.co.reiad.library.core.LANGS
import uk.co.reiad.library.core.langOf
import uk.co.reiad.library.core.Blur
import uk.co.reiad.library.core.Finish
import uk.co.reiad.library.core.GLASSES
import uk.co.reiad.library.core.Kind
import uk.co.reiad.library.data.Held
import uk.co.reiad.library.core.MEASURES
import uk.co.reiad.library.core.Measure
import uk.co.reiad.library.core.SCALES
import uk.co.reiad.library.core.Scale
import uk.co.reiad.library.core.measureOf
import uk.co.reiad.library.core.scaleOf
import uk.co.reiad.library.core.PrefOption
import uk.co.reiad.library.core.Prefs
import uk.co.reiad.library.core.THEMES
import uk.co.reiad.library.core.Theme
import uk.co.reiad.library.core.VEILS
import uk.co.reiad.library.core.Veil

/* ============================================================
   What a reader can change, and what it does.

   Four settings on this sheet and every one of them has a
   sentence under it saying what it will do. That is not padding:
   a setting whose effect a reader cannot predict is a setting
   they press once and never again, and half of these are about
   how a surface looks, which is exactly the kind of thing nobody
   experiments with blind.

   The tables are `core/Prefs.kt`, which holds the site's own ids,
   labels and notes. Nothing on this screen names a value.

   ---- three finishes, not three blurs ----

   `plain` is the one to keep working. It is what a reader who has
   asked their system for less transparency gets, and what anybody
   who finds moving text under a bar hard to read chooses. So the
   two sliders under it go quiet rather than pretending: a blur
   control on a finish with no blur is a control that lies.
   ============================================================ */

@Composable
fun SettingsSheet(
    prefs: Prefs,
    onChange: ((Prefs) -> Prefs) -> Unit,
    onClose: () -> Unit,
    /** What the app is holding for offline reading, and the way
        to be rid of it. Optional so that this sheet can still be
        drawn on its own, which is what the render test does. */
    held: Held? = null,
    onForget: () -> Unit = {},
    /** When one reminder a day arrives, as `"21:00"`, and null
        for off. This handset's rather than the account's, which
        is why it sits under the heading that says so. */
    remindAt: String? = null,
    onRemind: (String?) -> Unit = {},
    /** Which build this is. Passed in rather than read from
        `BuildConfig` here, so the render test can pin a fixed
        string and the snapshot does not change on every commit. */
    builtFrom: String = uk.co.reiad.library.BuildConfig.BUILT_FROM,
) {
    val c = LocalReiad.current
    val reduced = rememberReducedMotion()
    val retreat = rememberRetreat(onBack = onClose)
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                onClose()
            },
    ) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .retreating(retreat, reduced)
                .clip(RoundedCornerShape(topStart = Corner.lg, topEnd = Corner.lg))
                .material(Kind.PANE, c, Corner.lg, ground = c.paper)
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { }
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = Gap.s8, vertical = Gap.s8)
                .verticalScroll(rememberScrollState()),
        ) {
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

            /* The seventh preference the site stores, and the
               one this sheet did not have. It writes `tool-lang`,
               which the calculators have read since long before
               there were accounts, so choosing here and choosing
               on the stock screen are ONE choice rather than two
               that can disagree. */
            Spacer(Modifier.height(Gap.s7))
            Choice(
                heading = "Calculators open in",
                options = LANGS,
                chosen = langOf(prefs.lang),
                onChoose = { lang -> onChange { it.copy(lang = lang.id) } },
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

            /* This phone's own, and it is BELOW the reading
               preferences on purpose: those travel with the
               account and these do not. Two different kinds of
               setting, and the sheet says so once for both. */
            Spacer(Modifier.height(Gap.s10))
            Text("This phone", style = MaterialTheme.typography.headlineSmall, color = c.ink)
            Spacer(Modifier.height(Gap.s3))
            Text(
                "These are about this handset and do not travel with your account.",
                style = MaterialTheme.typography.bodySmall,
                color = c.inkSoft,
            )
            Spacer(Modifier.height(Gap.s7))

            /* One a day, naming the lesson you were in the middle
               of, and NOTHING when there is no such lesson. It
               never mentions a day that was missed: a notification
               saying "you haven't read today" is a streak in a
               sentence, and ROUTINE.md's rule is that nothing here
               may go down. */
            Text("A reminder", style = MaterialTheme.typography.titleSmall, color = c.ink)
            Spacer(Modifier.height(Gap.s2))
            Text(
                "Once a day, naming the lesson you were in the middle of. Nothing at "
                    + "all if there isn't one.",
                style = MaterialTheme.typography.bodySmall,
                color = c.inkSoft,
            )
            Spacer(Modifier.height(Gap.s5))
            Row(horizontalArrangement = Arrangement.spacedBy(Gap.s3)) {
                for ((label, time) in REMIND_TIMES) {
                    val id = time?.toString()
                    Tap(onClick = { onRemind(id) }) {
                        Chip(label, tone = if (remindAt == id) c.accent else c.inkSoft)
                    }
                }
            }

            if (held != null) {
                Spacer(Modifier.height(Gap.s9))
                Text("Offline", style = MaterialTheme.typography.titleSmall, color = c.ink)
                Spacer(Modifier.height(Gap.s5))
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
            Spacer(Modifier.height(Gap.s10))
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
            Spacer(Modifier.height(Gap.s10))
        }
    }
}

/** One question, its answers, and what each answer does.

    A groove with the answers riding in it, so the whole set reads
    as one control rather than as three buttons that happen to be
    near each other. */
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
        Row(
            Modifier
                .fillMaxWidth()
                .height(Gap.tap)
                .clip(RoundedCornerShape(Corner.pill))
                .material(Kind.GROOVE, c, Corner.pill, ground = c.paperSunk)
                .padding(Gap.s2),
        ) {
            for (option in options) {
                val on = option.id == chosen
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(Corner.pill))
                        .material(
                            kind = Kind.CONTROL,
                            colours = c,
                            corner = Corner.pill,
                            ground = if (on) c.accent.copy(alpha = dim) else Color.Transparent,
                        )
                        .clickable(role = Role.RadioButton, enabled = enabled) { onChoose(option.id) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label(option),
                        style = MaterialTheme.typography.labelLarge,
                        color = (if (on) c.paper else c.inkSoft).copy(alpha = dim),
                        /* Truncated inside the thumb rather than
                           wrapped out of it. A two-line label in a
                           pill this size does not make the pill
                           taller, it spills over the edge, and
                           "Follow my system" did exactly that.
                           `PrefLabelsTest` keeps them short; this
                           is what happens if one ever is not. */
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        /* The sentence for the one that is CHOSEN, not all three.
           Three notes under three buttons is a paragraph nobody
           reads; one note under the answer you are on is a
           sentence saying what you just did. */
        options.firstOrNull { it.id == chosen }?.let { current ->
            if (note(current).isNotBlank()) {
                Spacer(Modifier.height(Gap.s4))
                Text(
                    note(current),
                    style = MaterialTheme.typography.bodySmall,
                    color = c.inkSoft.copy(alpha = dim),
                )
            }
        }
    }
}
