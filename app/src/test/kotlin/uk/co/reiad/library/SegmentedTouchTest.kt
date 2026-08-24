package uk.co.reiad.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import uk.co.reiad.library.ui.ReiadTheme
import uk.co.reiad.library.ui.Segmented
import kotlin.test.assertEquals

/* ============================================================
   A finger on the track picks the segment under it.

   ---- what this is really testing ----

   The switch that shipped was a row of boxes with a `clickable`
   each. On a real phone six of the seven reading preferences
   came back holding the FIRST option of their row whatever was
   pressed, and the seventh only looked fine because it is stored
   under a different key. Every snapshot was correct and every
   other test passed: what was wrong sat between the finger and
   the handler, which is exactly where a per-child hit target
   puts it.

   So there is no per-segment target now. The track is one
   gesture surface and the segment is arithmetic on the pointer's
   x, which is a thing a test can state exactly: press at 5/6 of
   the width and you get the third of three.

   The old control could not be tested this way at all. Under
   Robolectric no injected touch reached any of its options,
   including the row that worked on a phone, so a touch test
   would have reported the harness rather than the app.
   ============================================================ */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h915dp")
class SegmentedTouchTest {

    @get:Rule val rule = createComposeRule()

    private val three = listOf("one", "two", "three")

    private fun track(): () -> String {
        var chosen by mutableStateOf("one")
        rule.setContent {
            ReiadTheme {
                Box(Modifier.fillMaxWidth().padding(16.dp)) {
                    Segmented(
                        options = three,
                        chosen = chosen,
                        onChoose = { chosen = it },
                        modifier = Modifier.testTag("track"),
                        label = { it },
                    ) { option, _ -> Text(option) }
                }
            }
        }
        return { chosen }
    }

    private fun pressAt(fraction: Float) {
        rule.onNodeWithTag("track").performTouchInput {
            down(Offset(width * fraction, height / 2f))
            up()
        }
        rule.waitForIdle()
    }

    @Test fun `a tap picks the segment under the finger`() {
        val now = track()
        pressAt(0.5f)
        assertEquals("two", now(), "the middle of the track is the middle option")
        pressAt(0.9f)
        assertEquals("three", now(), "the right of the track is the last option")
        pressAt(0.1f)
        assertEquals("one", now(), "the left of the track is the first option")
    }

    /** The bug, stated as the thing that must not happen: a press
        anywhere but the first third must not come back as the
        first option. */
    @Test fun `a press does not fall back to the first option`() {
        val now = track()
        pressAt(0.75f)
        assertEquals("three", now(), "a press on the right came back as ${now()}")
    }

    /** And the slide, which is what was asked for: hold, move
        across, let go where you stopped. */
    @Test fun `sliding across commits where the finger stopped`() {
        val now = track()
        rule.onNodeWithTag("track").performTouchInput {
            down(Offset(width * 0.1f, height / 2f))
            moveTo(Offset(width * 0.5f, height / 2f))
            moveTo(Offset(width * 0.92f, height / 2f))
            up()
        }
        rule.waitForIdle()
        assertEquals("three", now(), "the finger ended over the last option")
    }

    /** A slide that comes back lands where it came back TO, not
        where it went. A switch that remembered the furthest point
        would be a switch you cannot correct without lifting. */
    @Test fun `a slide that returns commits where it returned to`() {
        val now = track()
        rule.onNodeWithTag("track").performTouchInput {
            down(Offset(width * 0.1f, height / 2f))
            moveTo(Offset(width * 0.95f, height / 2f))
            moveTo(Offset(width * 0.5f, height / 2f))
            up()
        }
        rule.waitForIdle()
        assertEquals("two", now())
    }
}
