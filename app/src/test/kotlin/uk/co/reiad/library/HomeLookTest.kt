package uk.co.reiad.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import org.junit.Rule
import org.junit.Test
import uk.co.reiad.library.core.Accents
import uk.co.reiad.library.core.SiteManifest
import uk.co.reiad.library.ui.LocalReiad
import uk.co.reiad.library.ui.ReiadTheme

class HomeLookTest {
    @get:Rule val pz = paparazzi()

    private val site: SiteManifest = fixture("site.json", SiteManifest.serializer())

    @Test fun home() {
        pz.snapshot {
            ReiadTheme(accent = Accents.GREEN, dark = false) {
                val c = LocalReiad.current
                Box(Modifier.fillMaxSize().background(c.paper)) {
                    Home(
                        site = site,
                        stale = false,
                        note = null,
                        ticks = mapOf("money" to setOf("a", "b", "c")),
                        audience = null,
                        onOpen = {},
                    )
                }
            }
        }
    }

    /** A board with two SMALLS on it, which have to sit side by
        side: the paired grid is what makes the three sizes a
        home screen's rather than a stretch, and no other
        snapshot holds a board that was actually arranged. */
    @Test fun homePaired() {
        pz.snapshot {
            ReiadTheme(accent = Accents.GREEN, dark = false) {
                val c = LocalReiad.current
                Box(Modifier.fillMaxSize().background(c.paper)) {
                    Home(
                        site = site,
                        stale = false,
                        note = null,
                        ticks = mapOf("money" to setOf("a", "b", "c")),
                        audience = null,
                        onOpen = {},
                        board = listOf(
                            "progress:small", "stock:small",
                            "continue:wide", "pulse:tall",
                        ),
                    )
                }
            }
        }
    }
}
