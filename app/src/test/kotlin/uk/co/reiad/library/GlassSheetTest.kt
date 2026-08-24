package uk.co.reiad.library

import androidx.compose.foundation.background
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import uk.co.reiad.library.core.Accents
import uk.co.reiad.library.ui.Card
import uk.co.reiad.library.ui.Chip
import uk.co.reiad.library.ui.Gap
import uk.co.reiad.library.ui.Groove
import uk.co.reiad.library.ui.LocalReiad
import uk.co.reiad.library.ui.Pane
import uk.co.reiad.library.ui.Plate
import uk.co.reiad.library.ui.ReiadTheme
import uk.co.reiad.library.ui.Rung

/* ============================================================
   Every kind of glass on one page, so a person can look at it.

   The fourth sheet, and the one the site's material section is
   actually ABOUT: six kinds, three axes, and the rule that as a
   surface gets thicker it gets less polished and less clear. The
   specular fix changed every card in one commit, and what nobody
   could do afterwards was SEE all six kinds beside each other:
   each looked plausible alone, which is exactly what the site
   says about the ladder breaking.

   The nesting row is the one that finds faults: a plate on a
   pane on the paper is three thicknesses stacked, and any kind
   that paints too strong a ground reads as a hole or a sticker
   the moment it sits on another.

       ./gradlew :app:recordPaparazziDebug --tests "*GlassSheetTest*"
   ============================================================ */
class GlassSheetTest {

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

        Label("PANE, WHICH HOLDS OTHER THINGS")
        Pane {
            Text("A pane", style = MaterialTheme.typography.titleSmall, color = c.ink)
            Text(
                "The thickest kind, nine millimetres, and the least polished.",
                style = MaterialTheme.typography.bodySmall,
                color = c.inkSoft,
            )
        }

        Label("CARD, WHICH TAKES YOU SOMEWHERE")
        Card {
            Text("A card", style = MaterialTheme.typography.titleSmall, color = c.ink)
            Text(
                "Five millimetres. It lifts and its light follows a finger.",
                style = MaterialTheme.typography.bodySmall,
                color = c.inkSoft,
            )
        }

        Label("PLATE, WHICH IS READ AND NEVER PRESSED")
        Plate(Modifier.fillMaxWidth()) {
            Text("RETURN ON HOLDINGS", style = MaterialTheme.typography.labelSmall, color = c.accent)
            Text("+0.5%", style = MaterialTheme.typography.headlineSmall, color = c.ink)
        }

        Label("RUNG, A ROW STANDING ON NOTHING")
        Rung {
            Text("A row in a ladder", style = MaterialTheme.typography.bodyMedium, color = c.ink)
        }

        Label("CHIP AND GROOVE, THE SMALLEST AND THE INVERSE")
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Chip("SAVED COPY")
            Spacer(Modifier.width(Gap.s6))
            Box(Modifier.weight(1f)) { Groove(0.62f) }
        }

        /* THE ROW THAT FINDS FAULTS. Three thicknesses stacked:
           any kind painting too strong a ground reads as a hole
           or a sticker the moment it sits on another. */
        Label("NESTED: A PLATE AND A GROOVE INSIDE A PANE")
        Pane {
            Text("কতটা হলো", style = MaterialTheme.typography.titleSmall, color = c.ink)
            Spacer(Modifier.height(Gap.s4))
            Plate(Modifier.fillMaxWidth()) {
                Text("3 টা পাঠ", style = MaterialTheme.typography.bodyMedium, color = c.ink)
                Spacer(Modifier.height(Gap.s3))
                Groove(0.3f)
            }
        }

        Label("AND A CARD ON A PANE, WHICH EVERY HUB HAS")
        Pane {
            Card {
                Text("A card in a pane", style = MaterialTheme.typography.bodyMedium, color = c.ink)
            }
        }
    }

    @Test fun glassLight() = sheet(dark = false) { Everything() }

    @Test fun glassDark() = sheet(dark = true) { Everything() }
}
