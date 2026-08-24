package uk.co.reiad.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import org.junit.Rule
import org.junit.Test
import uk.co.reiad.library.core.Accents
import uk.co.reiad.library.ui.AmbientGround
import uk.co.reiad.library.ui.ButtonKind
import uk.co.reiad.library.ui.Card
import uk.co.reiad.library.ui.Gap
import uk.co.reiad.library.ui.LocalReiad
import uk.co.reiad.library.ui.Pane
import uk.co.reiad.library.ui.PillButton
import uk.co.reiad.library.ui.Plate
import uk.co.reiad.library.ui.ReiadTheme
import uk.co.reiad.library.ui.Sway

/* ============================================================
   The ground the glass refracts, looked at.

   The fifth sheet. The field is deliberately below conscious
   attention on a moving phone, which is exactly why it needs a
   still page where a person can ask the three questions that
   matter: is there depth behind the glass now, do the faces
   stay FLAT over it, and is the whole thing still the site's
   palette rather than a lava lamp.

   The second one is a correction. Panes and cards used to let a
   tenth of the field through, and on a dark handset the pool
   behind a card read as a lamp switched on inside it: reported
   twice. The faces are solid now and the field is a third of
   what it was, anchored off the edges of the page so the calmest
   part of it is the middle, where the reading is.

       ./gradlew :app:recordPaparazziDebug --tests "*AmbientSheetTest*"
   ============================================================ */
class AmbientSheetTest {

    @get:Rule val paparazzi = paparazzi(HANDSET)

    private fun sheet(dark: Boolean) {
        paparazzi.snapshot {
            ReiadTheme(accent = Accents.GREEN, dark = dark) {
                val c = LocalReiad.current
                Box(Modifier.fillMaxSize().background(c.paper)) {
                    AmbientGround(Sway(), Modifier.matchParentSize())
                    Column(
                        Modifier.fillMaxSize().padding(Gap.s8),
                        verticalArrangement = Arrangement.spacedBy(Gap.s6),
                    ) {
                        Pane {
                            Text(
                                "A pane on the field",
                                style = MaterialTheme.typography.titleSmall,
                                color = c.ink,
                            )
                            Text(
                                "A solid face. The field is what it sits ON.",
                                style = MaterialTheme.typography.bodySmall,
                                color = c.inkSoft,
                            )
                        }
                        Card {
                            Text(
                                "A card on the field",
                                style = MaterialTheme.typography.bodyMedium,
                                color = c.ink,
                            )
                        }
                        Plate(Modifier.fillMaxWidth()) {
                            Text(
                                "A PLATE STAYS SOLID",
                                style = MaterialTheme.typography.labelSmall,
                                color = c.accent,
                            )
                            Text("+0.5%", style = MaterialTheme.typography.headlineSmall, color = c.ink)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(Gap.s5)) {
                            PillButton("Solid", {}, kind = ButtonKind.SOLID)
                            PillButton("Ghost", {}, kind = ButtonKind.GHOST)
                        }
                        /* Bare field below, so the pools themselves
                           are visible in the bottom half. */
                    }
                }
            }
        }
    }

    @Test fun ambientLight() = sheet(dark = false)

    @Test fun ambientDark() = sheet(dark = true)
}
