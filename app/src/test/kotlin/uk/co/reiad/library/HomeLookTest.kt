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
}
