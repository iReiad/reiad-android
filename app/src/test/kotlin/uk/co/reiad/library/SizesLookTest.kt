package uk.co.reiad.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import uk.co.reiad.library.core.SiteManifest
import uk.co.reiad.library.ui.ReiadTheme
import uk.co.reiad.library.ui.Shell
import uk.co.reiad.library.ui.ShellState

/* ============================================================
   The same screen on three phones.

   "It should work perfectly throughout all phones" was the ask,
   from a Pixel 10, after two rounds of layout faults that were
   invisible here: the clearance was a constant tuned on one
   device, and every snapshot was taken at one width.

   One width is the whole problem. A drawing at 412dp cannot
   show a Bangla title truncating at 360, and it cannot show the
   shell swapping its bottom bar for a rail at tablet width,
   which is a different layout rather than the same one
   stretched.

   Not a golden test, like everything else in this directory.
   It exists so a person, or a model, can SEE the app at more
   than one size:

       ./gradlew :app:recordPaparazziDebug
   ============================================================ */
@RunWith(Parameterized::class)
class SizesLookTest(private val name: String, private val device: DeviceConfig) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun sizes(): List<Array<Any>> = listOf(
            arrayOf("small", SMALL),
            arrayOf("handset", HANDSET),
            arrayOf("tablet", TABLET),
        )
    }

    @get:Rule val paparazzi = paparazzi(device)

    private val site: SiteManifest = fixture("site.json", SiteManifest.serializer())

    @Test fun shell() {
        paparazzi.snapshot(name = name) {
            ReiadTheme {
                Shell(
                    state = ShellState(
                        site = site, current = null, audience = "learn", drawerOpen = false,
                    ),
                    onHome = {}, onGroup = {}, onItem = {}, onDrawer = {},
                    onSearch = {}, onSettings = {}, onAccount = {}, onAudience = {},
                ) {
                    Box(Modifier.fillMaxSize()) {
                        Home(
                            site = site,
                            stale = false,
                            note = null,
                            ticks = emptyMap(),
                            audience = "learn",
                            onOpen = {},
                        )
                    }
                }
            }
        }
    }
}
