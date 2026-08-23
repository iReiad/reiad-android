package uk.co.reiad.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import org.junit.Rule
import org.junit.Test
import uk.co.reiad.library.core.Accents
import uk.co.reiad.library.ui.ButtonKind
import uk.co.reiad.library.ui.Gap
import uk.co.reiad.library.ui.LocalReiad
import uk.co.reiad.library.ui.Pane
import uk.co.reiad.library.ui.PillButton
import uk.co.reiad.library.ui.ReiadTheme

/* ============================================================
   Every button on one page, so a person can look at them.

   The third sheet, after the icons and the text boxes, and made
   for the same reason: there were three ways to draw a button
   and nobody could see it, because no screen showed two ways at
   once. Nineteen bare `Control(Modifier.clickable(...))` sites
   each had their own idea of case, colour and width, and every
   one of them let the press ripple bleed square corners because
   the clickable sat outside the pill clip.

   The kinds are the site's own four, and the ladder between them
   is LOUDNESS: solid is the one action a screen is for, soft
   sits on a panel, ghost acts without claiming ground, quiet is
   a word that happens to be pressable. `pressed` is the third
   axis and is a latch, not an emphasis.

       ./gradlew :app:recordPaparazziDebug --tests "*ButtonSheetTest*"
   ============================================================ */
class ButtonSheetTest {

    @get:Rule val paparazzi = paparazzi(HANDSET)

    private fun sheet(dark: Boolean, body: @androidx.compose.runtime.Composable () -> Unit) {
        paparazzi.snapshot {
            ReiadTheme(accent = Accents.GREEN, dark = dark) {
                val c = LocalReiad.current
                Box(Modifier.fillMaxSize().background(c.paper).padding(Gap.s8)) {
                    Column(verticalArrangement = Arrangement.spacedBy(Gap.s6)) { body() }
                }
            }
        }
    }

    private @androidx.compose.runtime.Composable
    fun Label(text: String) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = LocalReiad.current.accent,
        )
    }

    private @androidx.compose.runtime.Composable
    fun Everything() {
        val c = LocalReiad.current

        Label("THE FOUR KINDS, QUIETEST LAST")
        Row(horizontalArrangement = Arrangement.spacedBy(Gap.s5)) {
            PillButton("Solid", {}, kind = ButtonKind.SOLID)
            PillButton("Soft", {}, kind = ButtonKind.SOFT)
            PillButton("Ghost", {}, kind = ButtonKind.GHOST)
            PillButton("Quiet", {}, kind = ButtonKind.QUIET)
        }

        Label("A LATCH, OFF AND ON")
        Row(horizontalArrangement = Arrangement.spacedBy(Gap.s5)) {
            PillButton("Save", {}, kind = ButtonKind.SOFT, pressed = false)
            PillButton("Saved ✓", {}, kind = ButtonKind.SOFT, pressed = true)
        }

        Label("WITH A MARK, AND A SHORT LABEL STILL 44 WIDE")
        Row(horizontalArrangement = Arrangement.spacedBy(Gap.s5)) {
            PillButton("Add food", {}, kind = ButtonKind.SOLID, icon = "plus")
            PillButton("Go", {}, kind = ButtonKind.GHOST)
        }

        Label("DISABLED, BESIDE THE BOX THAT WILL ENABLE IT")
        PillButton("Send", {}, kind = ButtonKind.SOLID, enabled = false)

        Label("THE DANGER COLOUR ON TWO LOUDNESSES")
        Row(horizontalArrangement = Arrangement.spacedBy(Gap.s5)) {
            PillButton("Erase everything", {}, kind = ButtonKind.SOFT, tint = c.danger)
            PillButton("Erase everything", {}, kind = ButtonKind.SOLID, tint = c.danger)
        }

        Label("BANGLA KEEPS ITS FACE AND ITS CASE")
        Row(horizontalArrangement = Arrangement.spacedBy(Gap.s5)) {
            PillButton("উত্তর দেখুন", {}, kind = ButtonKind.SOFT)
            PillButton("পড়া হয়েছে ✓", {}, kind = ButtonKind.SOFT, pressed = true)
        }

        Label("WIDE, WHICH IS THE ACCOUNT PAGE'S SHAPE")
        PillButton("Take a copy of everything", {}, kind = ButtonKind.SOFT, wide = true)
        PillButton("Continue with Google", {}, kind = ButtonKind.SOLID, wide = true)

        Label("ON A PANE, WHICH IS WHERE MOST OF THEM LIVE")
        Pane {
            Row(horizontalArrangement = Arrangement.spacedBy(Gap.s5)) {
                PillButton("Save", {}, kind = ButtonKind.SOLID)
                PillButton("Cancel", {}, kind = ButtonKind.GHOST)
            }
        }
    }

    @Test fun buttonsLight() = sheet(dark = false) { Everything() }

    @Test fun buttonsDark() = sheet(dark = true) { Everything() }
}
