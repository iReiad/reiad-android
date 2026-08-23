package uk.co.reiad.library

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import uk.co.reiad.library.core.LessonResponse
import uk.co.reiad.library.core.Lesson
import uk.co.reiad.library.core.SiteManifest
import uk.co.reiad.library.core.Stage
import uk.co.reiad.library.ui.ReiadTheme
import kotlin.test.assertTrue

/* ============================================================
   A lesson gets from the skeleton to the prose.

   `Reading` parses the body on `Dispatchers.Default` and draws a
   skeleton until that lands, deliberately: a long lesson parsed
   inside composition is a hitch on the frame a reader is
   watching, and `PieceScreen` gives the same reason at length.

   The cost is that the screen has TWO states and the picture of
   it is only ever of one. The Paparazzi snapshot used to be
   `Reading()` and was therefore a race with a real thread pool:
   it recorded six grey bars about as often as it recorded the
   lesson, and nothing failed either way. That snapshot is
   `lessonBody` now, drawn from blocks the test parses.

   This is the other half, and it is the half that says the
   screen WORKS: it waits, the way a reader does, and asserts the
   prose arrives. A skeleton that never resolves renders
   perfectly.
   ============================================================ */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xhdpi")
class ReadingSettlesTest {

    @get:Rule val compose = createComposeRule()

    private val site: SiteManifest = fixture("site.json", SiteManifest.serializer())

    @Test fun theProseArrivesAfterTheSkeleton() {
        val page = requireNotNull(
            fixture("lesson-papers.json", LessonResponse.serializer()).lesson,
        ) { "the lesson fixture has no page in it" }
        /* A real sentence out of the fixture, not a word this
           test invents: the assertion has to fail if the parser
           stops producing prose, not if somebody rewords a
           lesson. */
        val opening = page.body
            .substringAfter("<p>").substringBefore("</p>")
            .replace(Regex("<[^>]+>"), "")
            .take(24)
        assertTrue(
            opening.length > 8,
            "the fixture's first paragraph is '$opening', which is too short to look for",
        )

        compose.setContent {
            ReiadTheme {
                Reading(
                    school = site.ladders.first { it.key == "money" },
                    stage = Stage(slug = "basics-1", en = "Basics"),
                    lesson = Lesson(slug = "x", bn = "বিও"),
                    page = page,
                    ticked = false,
                    isMoney = true,
                    onBack = {}, onTick = {},
                    checks = emptySet(), onCheck = {},
                    lessonKey = "k",
                )
            }
        }

        /* Explicitly, because the parse is on another dispatcher:
           `waitForIdle` alone answers about the composition and
           not about a thread pool this screen deliberately hands
           work to. */
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText(opening, substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(opening, substring = true).assertIsDisplayed()
    }
}
