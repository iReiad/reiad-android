package uk.co.reiad.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import org.junit.Rule
import org.junit.Test
import uk.co.reiad.library.core.Accents
import uk.co.reiad.library.ui.Field
import uk.co.reiad.library.ui.FieldSize
import uk.co.reiad.library.ui.ButtonKind
import uk.co.reiad.library.ui.Gap
import uk.co.reiad.library.ui.LocalReiad
import uk.co.reiad.library.ui.Pane
import uk.co.reiad.library.ui.PillButton
import uk.co.reiad.library.ui.ReiadTheme

/* ============================================================
   Every text box on one page, so a person can look at them.

   Not a golden test. The icons were fixed twice by guessing and
   both guesses were wrong; what found the real fault was drawing
   all forty-three on one sheet and looking. A text box has the
   same problem: nine of them were spread across nine screens, no
   two the same, and nobody could see that because nobody ever saw
   two at once.

   The row that matters most is the LAST one. A field inside a
   card is the case the complaint was about: the card pads its
   contents, the field pads its text, and the two insets stack
   into a box whose text does not line up with the label over it.

       ./gradlew :app:recordPaparazziDebug --tests "*FieldSheetTest*"
   ============================================================ */
class FieldSheetTest {

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
        Label("EMPTY, WITH A HINT")
        Field(value = "", onValue = {}, description = "d", hint = "you@example.com")

        Label("FILLED")
        Field(value = "Rony Reiad", onValue = {}, description = "d")

        Label("WITH A LEADING MARK, WHICH IS WHAT SAYS SEARCH")
        Field(value = "", onValue = {}, description = "d", hint = "Search the library", icon = "search")

        Label("WITH A CONTROL INSIDE IT")
        Field(value = "74", onValue = {}, description = "d") {
            Text("kg", style = MaterialTheme.typography.labelSmall, color = LocalReiad.current.inkSoft)
        }

        Label("AN AREA, FOR A NOTE OR A COMMENT")
        Field(
            value = "", onValue = {}, description = "d", size = FieldSize.AREA,
            hint = "Whatever you want to remember about this.",
        )

        Label("NARROW, BESIDE A BUTTON")
        Row(
            horizontalArrangement = Arrangement.spacedBy(Gap.s5),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Box(Modifier.width(140.dp())) {
                Field(value = "", onValue = {}, description = "d", hint = "kg")
            }
            PillButton("Save", {}, kind = ButtonKind.SOLID)
        }

        /* THE ONE THE COMPLAINT WAS ABOUT. The label, the note and
           the box all have to start in the same column. */
        Label("INSIDE A CARD, WHICH IS WHERE ALMOST ALL OF THEM LIVE")
        Pane {
            Text(
                "Your name",
                style = MaterialTheme.typography.titleSmall,
                color = LocalReiad.current.ink,
            )
            Text(
                "What appears beside anything you write.",
                style = MaterialTheme.typography.bodySmall,
                color = LocalReiad.current.inkSoft,
            )
            Box(Modifier.fillMaxWidth().padding(top = Gap.s4)) {
                Field(value = "Rony Reiad", onValue = {}, description = "d")
            }
        }
    }

    private fun Int.dp() = androidx.compose.ui.unit.Dp(this.toFloat())

    @Test fun fieldsLight() = sheet(dark = false) { Everything() }

    @Test fun fieldsDark() = sheet(dark = true) { Everything() }
}
