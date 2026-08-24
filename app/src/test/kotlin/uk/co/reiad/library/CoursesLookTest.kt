package uk.co.reiad.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.CompositionLocalProvider
import kotlinx.serialization.builtins.ListSerializer
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import uk.co.reiad.library.core.Accents
import uk.co.reiad.library.core.Bookmark
import uk.co.reiad.library.core.Course
import uk.co.reiad.library.core.CourseResponse
import uk.co.reiad.library.core.ProgrammeSummary
import uk.co.reiad.library.core.courseLessonId
import uk.co.reiad.library.core.laddered
import uk.co.reiad.library.core.QuizQuestion
import uk.co.reiad.library.core.QuizResponse
import uk.co.reiad.library.core.ReadingResponse
import uk.co.reiad.library.core.TicketResponse
import uk.co.reiad.library.courses.Answer
import uk.co.reiad.library.courses.LessonSource
import uk.co.reiad.library.ui.CourseLessonScreen
import uk.co.reiad.library.ui.CourseModuleScreen
import uk.co.reiad.library.ui.CourseProgrammeScreen
import uk.co.reiad.library.ui.CourseScreen
import uk.co.reiad.library.ui.CourseShelfScreen
import uk.co.reiad.library.ui.LocalReiad
import uk.co.reiad.library.ui.ReiadTheme
import kotlin.test.assertTrue

/* ============================================================
   The course section, drawn and reached.

   Two things at once, because the section arrived as five screens
   at a stroke and both questions are worth asking of it:

     - a PICTURE, so a person can look at what was built. The
       renders land in `app/src/test/snapshots/images/` under
       `./gradlew :app:recordPaparazziDebug`.
     - the SEMANTICS TREE, so a screen reader can name every
       target and a finger can hit it. `ReachTest`'s own two rules,
       applied to the new screens: nothing under the site's 44dp,
       and nothing actionable without a label.

   Both are drawn from the fixture the site's own emitter produced
   (see `CoursesTest` for what that is and why it is the one
   fixture here that is not a captured answer), so a title on
   screen is a title in the shape the API sends and the counts
   under it are the API's arithmetic.

   ---- the lesson screen is AUDITED and not drawn ----

   Its three panels fetch their own content when they compose, the
   way the website's own player mounts each of them, and a
   Paparazzi snapshot is ONE frame: the picture would be of the
   skeleton every time, which is a picture of the moment before
   the screen. `ScreensLookTest` has the same note about the
   sheets, and solved it with `LocalStill` because an animation
   can be told to be finished. A fetch cannot.

   The reach audit has a clock, so it does get there. `Canned`
   below answers instantly and reaches nothing, which is what lets
   the audit walk a real quiz: the QUIZ is the one thing in this
   section that builds inputs of its own, and every option on it
   is a target.

   No video anywhere here, deliberately: a `Reel.Ready` builds a
   real ExoPlayer, and a harness with no media stack under it is a
   fight with the harness rather than a look at the design. The
   audit's stub refuses the ticket, so the video panel draws its
   refusal card and the rest of the screen is real.
   ============================================================ */

private val shelfRows: List<ProgrammeSummary> = fixtureField(
    "courses.json", "courses", ListSerializer(ProgrammeSummary.serializer()),
)

private val answer: CourseResponse =
    fixture("course-first.json", CourseResponse.serializer())

private val course: Course = answer.course

/** A reader part way through: the first lesson of the first
    module ticked, and nothing else.

    Part way rather than empty on purpose. An empty screen draws
    every bar at zero and every card saying "open", which is the
    one state where a bar being wrong is invisible. */
private val read: Set<String> = setOf(
    courseLessonId(course.slug, "01_beginnings", "01_a-welcome"),
)

/** A source that answers instantly and reaches nothing.

    The quiz is two questions, one of each kind, because
    single-answer and select-all are different controls and the
    single-answer one carries the rule that picking one clears the
    rest of its question. */
private object Canned : LessonSource {
    override suspend fun ticket(drive: String): Answer<TicketResponse> =
        Answer.Unreachable("no network in a test")

    override suspend fun reading(drive: String): Answer<ReadingResponse> =
        Answer.Got(
            ReadingResponse(
                ok = true,
                title = "A page to read",
                html = "<h3>What counts as data</h3><p>A saved page, sanitised by the " +
                    "Worker and rendered through the same parser a lesson uses.</p>",
            ),
        )

    override suspend fun quiz(drive: String): Answer<QuizResponse> = Answer.Got(
        QuizResponse(
            ok = true,
            title = "A few questions",
            parsed = true,
            questions = listOf(
                QuizQuestion(
                    n = 1,
                    prompt = "<p>Which of these is a data source?</p>",
                    multiple = false,
                    options = listOf("A spreadsheet", "A hunch", "A conversation"),
                ),
                QuizQuestion(
                    n = 2,
                    prompt = "<p>Select every step of the process.</p>",
                    multiple = true,
                    options = listOf("Ask", "Prepare", "Guess", "Analyse"),
                ),
            ),
        ),
    )
}

class CoursesLookTest {
    @get:Rule val pz = paparazzi()

    private fun page(dark: Boolean = false, body: @androidx.compose.runtime.Composable () -> Unit) {
        pz.snapshot {
            CompositionLocalProvider(uk.co.reiad.library.ui.LocalStill provides true) {
                /* GREEN going in, because every one of these
                   screens sets its own gold and a render started
                   in gold could not show that it does. */
                ReiadTheme(accent = Accents.GREEN, dark = dark) {
                    val c = LocalReiad.current
                    Box(Modifier.fillMaxSize().background(c.paper)) { body() }
                }
            }
        }
    }

    @Test fun shelf() = page {
        CourseShelfScreen(
            answer = Answer.Got(shelfRows),
            read = read,
            bottomPadding = 90.dp,
            onOpen = {}, onRetry = {}, onAccount = {},
        )
    }

    /** And in the dark, because gold on a dark ground is where a
        borrowed accent usually goes wrong. */
    @Test fun shelfDark() = page(dark = true) {
        CourseShelfScreen(
            answer = Answer.Got(shelfRows),
            read = read,
            bottomPadding = 90.dp,
            onOpen = {}, onRetry = {}, onAccount = {},
        )
    }

    /** "Not yours", which is what somebody who is not the admin
        gets, and it must read as an answer rather than as a
        failure: there is nothing to retry. */
    @Test fun shelfNotYours() = page {
        CourseShelfScreen(
            answer = Answer.NotYours(
                "This section is one person's own copy of a third-party course. " +
                    "It is not published.",
            ),
            read = emptySet(),
            bottomPadding = 90.dp,
            onOpen = {}, onRetry = {}, onAccount = {},
        )
    }

    @Test fun programme() = page {
        CourseProgrammeScreen(
            programme = shelfRows.first(),
            read = read,
            bottomPadding = 90.dp,
            onOpen = {}, onBack = {},
        )
    }

    @Test fun oneCourse() = page {
        CourseScreen(
            programme = "sample-certificate",
            holder = answer.programme,
            course = course,
            read = read,
            bookmark = Bookmark(id = read.first()),
            bottomPadding = 90.dp,
            onOpenLesson = {}, onOpenModule = {}, onBack = {},
        )
    }

    @Test fun moduleSummary() = page {
        CourseModuleScreen(
            course = course,
            mod = course.modules.first(),
            rungs = laddered("sample-certificate", course),
            read = read,
            bottomPadding = 90.dp,
            onOpenLesson = {}, onOpenModule = {}, onBack = {},
        )
    }

}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xhdpi")
class CoursesReachTest {

    @get:Rule val compose = createComposeRule()

    private fun SemanticsNode.actionable(): List<SemanticsNode> = buildList {
        if (config.getOrNull(SemanticsActions.OnClick) != null) add(this@actionable)
        for (child in children) addAll(child.actionable())
    }

    private fun SemanticsNode.said(): String {
        config.getOrNull(SemanticsProperties.ContentDescription)
            ?.firstOrNull()?.takeIf { it.isNotBlank() }?.let { return it }
        config.getOrNull(SemanticsProperties.Text)
            ?.joinToString(" ") { it.text }?.takeIf { it.isNotBlank() }?.let { return it }
        return children.joinToString(" ") { it.said() }.trim()
    }

    /** `ReachTest`'s two rules, on this section's screens.

        `scale` is the reader's own type size: 2f is the 200% a
        phone's accessibility settings offer, and a target that is
        big enough at 100% and not at 200% is a target that stops
        working for exactly the reader who needs it most. */
    private fun audit(
        name: String,
        scale: Float = 1f,
        body: @androidx.compose.runtime.Composable () -> Unit,
    ) {
        compose.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, scale)) {
                ReiadTheme(accent = Accents.GREEN, dark = false) {
                    val c = LocalReiad.current
                    Box(Modifier.fillMaxSize().background(c.paper)) { body() }
                }
            }
        }
        val density = compose.density
        val targets = compose.onRoot().fetchSemanticsNode().actionable()
        assertTrue(targets.isNotEmpty(), "$name has nothing to press, which cannot be right")

        val unnamed = targets.filter { it.said().isBlank() }
        assertTrue(
            unnamed.isEmpty(),
            "$name: ${unnamed.size} target(s) a screen reader cannot name, " +
                "at ${unnamed.map { it.boundsInRoot }}",
        )

        val small = targets.filter { node ->
            val w = with(density) { node.size.width.toDp() }
            val h = with(density) { node.size.height.toDp() }
            (w > 0.dp && w < 44.dp) || (h > 0.dp && h < 44.dp)
        }
        assertTrue(
            small.isEmpty(),
            "$name: ${small.size} target(s) under the site's 44dp tap size, " +
                "at ${small.map { it.said() to it.size }}",
        )
    }

    @Test fun theShelf() = audit("courses-shelf") {
        CourseShelfScreen(
            answer = Answer.Got(shelfRows),
            read = read,
            bottomPadding = 90.dp,
            onOpen = {}, onRetry = {}, onAccount = {},
        )
    }

    @Test fun theProgramme() = audit("courses-programme") {
        CourseProgrammeScreen(
            programme = shelfRows.first(),
            read = read,
            bottomPadding = 90.dp,
            onOpen = {}, onBack = {},
        )
    }

    @Test fun theCourse() = audit("courses-course") {
        CourseScreen(
            programme = "sample-certificate",
            holder = answer.programme,
            course = course,
            read = read,
            bookmark = Bookmark(id = read.first()),
            bottomPadding = 90.dp,
            onOpenLesson = {}, onOpenModule = {}, onBack = {},
        )
    }

    /** The same course at 200% type, which is where a row that
        fits by two pixels stops fitting. */
    @Test fun theCourseAtDoubleType() = audit("courses-course-200", scale = 2f) {
        CourseScreen(
            programme = "sample-certificate",
            holder = answer.programme,
            course = course,
            read = read,
            bookmark = Bookmark(id = read.first()),
            bottomPadding = 90.dp,
            onOpenLesson = {}, onOpenModule = {}, onBack = {},
        )
    }

    @Test fun theModuleSummary() = audit("courses-module") {
        CourseModuleScreen(
            course = course,
            mod = course.modules.first(),
            rungs = laddered("sample-certificate", course),
            read = read,
            bottomPadding = 90.dp,
            onOpenLesson = {}, onOpenModule = {}, onBack = {},
        )
    }

    /**
     * A lesson, with the tick, the continue button, the files it
     * came with, and the way on.
     *
     * The first lesson of the fixture on purpose: it is the one
     * carrying a video, captions, a transcript AND an attached
     * file, so the Files list has rows in it to reach. No network
     * is involved: with no session on this device the client
     * answers `SignedOut` without opening a socket, and the three
     * content panels draw their refusal cards.
     */
    @Test fun aLesson() = audit("courses-lesson") {
        val mod = course.modules.first()
        CourseLessonScreen(
            source = Canned,
            course = course,
            mod = mod,
            /* The one carrying a video, captions, a transcript AND
               an attached file, so the Files list has rows in it
               to reach. The video panel draws its refusal, which
               is what `Canned` answers a ticket with. */
            lesson = mod.lessons.first(),
            rungs = laddered("sample-certificate", course),
            read = read,
            answers = emptySet(),
            bottomPadding = 90.dp,
            onTick = {}, onMarkAndGo = { _, _, _ -> }, onOpenLesson = {},
            onAnswer = { _, _, _, _, _ -> }, onClearAnswers = {},
            onOpenOriginal = {}, onBack = {},
        )
    }

    /** And the quiz, whose every option is a target and whose
        controls are built here rather than borrowed. */
    @Test fun aQuiz() = audit("courses-quiz") {
        val mod = course.modules.first()
        CourseLessonScreen(
            source = Canned,
            course = course,
            mod = mod,
            lesson = mod.lessons.first { it.kind == "quiz" },
            rungs = laddered("sample-certificate", course),
            read = read,
            /* One already picked, because a restored answer draws
               differently from an unpicked one and both are
               targets. */
            answers = setOf(
                uk.co.reiad.library.core.courseAnswerId(
                    courseLessonId(course.slug, mod.slug, "03_a-few-questions"), 1, 0,
                ),
            ),
            bottomPadding = 90.dp,
            onTick = {}, onMarkAndGo = { _, _, _ -> }, onOpenLesson = {},
            onAnswer = { _, _, _, _, _ -> }, onClearAnswers = {},
            onOpenOriginal = {}, onBack = {},
        )
    }
}
