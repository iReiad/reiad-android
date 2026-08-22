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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import uk.co.reiad.library.core.BLURS
import uk.co.reiad.library.core.Blur
import uk.co.reiad.library.core.Finish
import uk.co.reiad.library.core.GLASSES
import uk.co.reiad.library.core.Kind
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
