package uk.co.reiad.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test
import uk.co.reiad.library.ui.Icon
import uk.co.reiad.library.ui.LocalReiad
import uk.co.reiad.library.ui.ReiadTheme
import uk.co.reiad.library.ui.SHAPES

/* ============================================================
   Every icon, at the size it is actually drawn, on one sheet.

   `IconNamesTest` already asserts that every NAME resolves to a
   drawing. It cannot tell whether the drawing is the right one,
   and that is the failure that shipped twice: the theme mark is
   a circle with a line down the middle of it here, because
   everything is stroked and the site fills half its circle, and
   at 19dp that is the international sign for "no". It sat in the
   top bar for three releases and was reported from a photograph.

   A name that resolves to the wrong picture cannot be caught by
   reading source. It can be caught by looking, and this is the
   sheet to look at:

       ./gradlew :app:recordPaparazziDebug

   Not a golden test. Like every other file here it exists so a
   person, or a model, can SEE what the app draws.
   ============================================================ */
class IconSheetTest {

    @get:Rule val paparazzi = Paparazzi(deviceConfig = HANDSET, maxPercentDifference = 0.1)

    @Test fun everyIcon() {
        val names = SHAPES.keys.sorted()
        paparazzi.snapshot {
            ReiadTheme {
                val c = LocalReiad.current
                Column(
                    Modifier
                        .fillMaxSize()
                        .background(c.paper)
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    for (row in names.chunked(4)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            for (name in row) {
                                Column(
                                    Modifier.width(94.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    /* At 19dp, which is the size
                                       the top bar's round buttons
                                       draw one at, and 34dp beside
                                       it so a wrong shape can be
                                       told from a small one. */
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Icon(name, size = 19.dp, tint = c.ink)
                                        Icon(name, size = 34.dp, tint = c.ink)
                                    }
                                    Text(
                                        name,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = c.inkSoft,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
