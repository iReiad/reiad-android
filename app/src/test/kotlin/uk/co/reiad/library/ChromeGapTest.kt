package uk.co.reiad.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import uk.co.reiad.library.core.SiteManifest
import uk.co.reiad.library.ui.LocalChromeGaps
import uk.co.reiad.library.ui.ReiadTheme
import uk.co.reiad.library.ui.Shell
import uk.co.reiad.library.ui.ShellState
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/* ============================================================
   A page opens below the bar, and the bar is measured.

   ---- what shipped, and why nothing here could see it ----

   Both floating bars carry `windowInsetsPadding`, so on a phone
   with a cutout each sits that much further from its edge. The
   clearance a page kept was two constants, 84dp and 96dp, tuned
   on an emulator with no cutout. So every screen opened UNDER
   the top bar: the first heading of a hub was cut in half and
   Bangla prose ran into the clock.

   It is worse than a number that is merely too small, because it
   is RIGHT on the device it was written on. Every snapshot here
   renders with no insets, so every snapshot was correct.

   And a bar's height is not a constant for a second reason: it
   holds text, and a reader who sets the type size to 150% grows
   it, which no number written down can follow.

   ---- so this asserts the loop rather than the number ----

   The bar reports what it measured and the page reads it back.
   A revert to a constant leaves the seeded default in place and
   the two stop agreeing, which is what fails here. Insets are
   nought under Robolectric, so what this can prove is the
   feedback, and the feedback is the half that was missing.
   ============================================================ */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h915dp")
class ChromeGapTest {

    @get:Rule val rule = createComposeRule()

    private val site: SiteManifest = fixture("site.json", SiteManifest.serializer())

    private fun shell(content: @androidx.compose.runtime.Composable () -> Unit) {
        rule.setContent {
            ReiadTheme {
                Shell(
                    state = ShellState(site = site, current = "home", audience = "learn", drawerOpen = false),
                    onHome = {}, onGroup = {}, onItem = {}, onDrawer = {},
                    onSearch = {}, onSettings = {}, onAccount = {}, onAudience = {},
                    content = content,
                )
            }
        }
    }

    /** The number a page keeps clear is what the bar came to,
        not a number somebody typed. */
    @Test fun theClearanceIsWhatTheBarMeasured() {
        var top = 0.dp
        var bottom = 0.dp
        shell {
            val gaps = LocalChromeGaps.current
            top = gaps.top
            bottom = gaps.bottom
            Box(Modifier.fillMaxSize()) { Text("page", Modifier.testTag("page")) }
        }
        rule.waitForIdle()

        val bar = rule.onNodeWithTag("topbar", useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        val barBottom = bar.bottom
        assertTrue(
            barBottom.value > 0f,
            "the top bar measured nothing, so nothing is feeding the clearance",
        )
        /* EQUALITY, not "at least". A constant that happens to
           clear the bar on the device it was tuned on is exactly
           what shipped, and `>=` would have passed on it here:
           with no insets the bar comes to 80dp and the old
           constant was 84. The clearance IS the bar plus one
           gap, and nothing else can be. */
        assertEquals(
            (barBottom + 12.dp).value, top.value, 0.5f,
            "a page opens at ${top.value}dp, the bar ends at ${barBottom.value}dp. " +
                "The clearance has stopped being what the bar measured, which is how " +
                "every screen ended up opening behind it on a phone with a cutout",
        )
        assertTrue(bottom.value > 0f, "nothing is feeding the bottom clearance either")
    }
}
