package uk.co.reiad.library

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import uk.co.reiad.library.core.Prefs
import uk.co.reiad.library.ui.ReiadTheme
import uk.co.reiad.library.ui.SettingsSheet
import kotlin.test.assertEquals

/* ============================================================
   Pressing a setting changes that setting.

   Reported from a real phone: "all settings except light dark
   aren't working, pressing a button just goes back to the first
   one". Every row rendered, every row highlighted something,
   every check here was green, and six of the seven preferences
   did nothing.

   The theme working and the rest not is the clue that makes this
   worth a test rather than a look: the theme is the one field
   stored under its OWN key, and the other six share one JSON
   record. So the fault is in the record, not in the drawing, and
   a snapshot can never see it.

   This drives the real sheet and asserts what the change lambda
   actually produces, which is the half between the control and
   the store.

   **Through the semantics action rather than an injected touch,
   and the reason is worth writing down.** Robolectric dispatches
   no touch into this sheet at all: every option, including the
   theme row that works on a real phone, stays unchanged under
   `performClick` and moves under the action. So an injected
   touch here proves nothing either way, and a test that used one
   would report a bug that is the harness rather than the app.
   What this can prove is that every row is wired to the field it
   names, which is the half a screenshot cannot show.
   ============================================================ */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h915dp")
class SettingsWorkTest {

    @get:Rule val rule = createComposeRule()

    /** The sheet, with a real `Prefs` in a state holder, so a
        press has to travel through `onChange` and come back as a
        redraw exactly as it does in the app. */
    private fun sheet(): () -> Prefs {
        var prefs by mutableStateOf(Prefs())
        rule.setContent {
            ReiadTheme {
                Box(Modifier) {
                    SettingsSheet(
                        prefs = prefs,
                        onChange = { change -> prefs = change(prefs) },
                        onClose = {},
                    )
                }
            }
        }
        return { prefs }
    }

    @Test fun `pressing an option chooses that option`() {
        val now = sheet()

        rule.onNodeWithText("Comfortable").performSemanticsAction(SemanticsActions.OnClick)
        rule.waitForIdle()
        assertEquals("large", now().text, "Type size: pressed Comfortable")

        rule.onNodeWithText("Wide").performSemanticsAction(SemanticsActions.OnClick)
        rule.waitForIdle()
        assertEquals("wide", now().measure, "Line width: pressed Wide")

        rule.onNodeWithText("Deep").performSemanticsAction(SemanticsActions.OnClick)
        rule.waitForIdle()
        assertEquals("deep", now().blur, "Blur: pressed Deep")

        rule.onNodeWithText("Dense").performSemanticsAction(SemanticsActions.OnClick)
        rule.waitForIdle()
        assertEquals("dense", now().veil, "Veil: pressed Dense")

        rule.onNodeWithText("English").performSemanticsAction(SemanticsActions.OnClick)
        rule.waitForIdle()
        assertEquals("en", now().lang, "Calculators open in: pressed English")

        /* Last, because `plain` is not glass and takes the blur
           and veil rows out with it. */
        rule.onNodeWithText("Paper").performSemanticsAction(SemanticsActions.OnClick)
        rule.waitForIdle()
        assertEquals("paper", now().glass, "Glass: pressed Paper")
    }

    /** And the ones already chosen stay chosen. A row that reset
        its neighbours would look identical one press at a time. */
    @Test fun `choosing one leaves the others alone`() {
        val now = sheet()

        rule.onNodeWithText("Comfortable").performSemanticsAction(SemanticsActions.OnClick)
        rule.onNodeWithText("Wide").performSemanticsAction(SemanticsActions.OnClick)
        rule.waitForIdle()

        assertEquals("large", now().text, "the type size did not survive the next press")
        assertEquals("wide", now().measure)
        assertEquals("bn", now().lang, "an untouched preference moved")
        assertEquals("frost", now().glass, "an untouched preference moved")
    }
}
