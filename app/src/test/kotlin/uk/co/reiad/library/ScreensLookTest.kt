package uk.co.reiad.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import uk.co.reiad.library.core.Accents
import uk.co.reiad.library.core.SiteManifest
import uk.co.reiad.library.core.stock.ToolWords
import uk.co.reiad.library.ui.CalcState
import uk.co.reiad.library.ui.CalculatorsScreen
import uk.co.reiad.library.ui.Gap
import uk.co.reiad.library.ui.LocalReiad
import uk.co.reiad.library.ui.BodyView
import uk.co.reiad.library.ui.LessonHead
import uk.co.reiad.library.ui.Path
import uk.co.reiad.library.ui.Paths
import uk.co.reiad.library.ui.PageHead
import uk.co.reiad.library.ui.Problem
import uk.co.reiad.library.ui.ReiadTheme
import uk.co.reiad.library.ui.Shell
import uk.co.reiad.library.ui.SetupState
import uk.co.reiad.library.ui.ShellState
import uk.co.reiad.library.ui.Skeleton
import uk.co.reiad.library.ui.StockScreen
import uk.co.reiad.library.ui.StockState
import uk.co.reiad.library.ui.DietScreen
import uk.co.reiad.library.ui.DietState
import uk.co.reiad.library.core.diet.Ancestry
import uk.co.reiad.library.core.diet.Body
import uk.co.reiad.library.core.diet.DietDay
import uk.co.reiad.library.core.diet.DietEntry
import uk.co.reiad.library.core.diet.DietProfile
import uk.co.reiad.library.core.diet.FloorHit
import uk.co.reiad.library.core.diet.Sex
import uk.co.reiad.library.core.diet.Target
import uk.co.reiad.library.ui.GroupScreen
import uk.co.reiad.library.ui.LiveScreen
import uk.co.reiad.library.ui.LiveState
import uk.co.reiad.library.ui.SearchScreen
import uk.co.reiad.library.ui.SettingsSheet
import uk.co.reiad.library.ui.WorkbookScreen
import uk.co.reiad.library.core.BookResponse
import uk.co.reiad.library.core.School
import uk.co.reiad.library.core.Prefs
import uk.co.reiad.library.ui.KeepSchool
import uk.co.reiad.library.ui.HeldPanel
import uk.co.reiad.library.data.Held
import uk.co.reiad.library.ui.RoutineScreen
import uk.co.reiad.library.ui.RoutineState
import uk.co.reiad.library.core.routine.RoutineShape
import uk.co.reiad.library.core.routine.SEASONS
import uk.co.reiad.library.ui.AccountScreen
import uk.co.reiad.library.core.LessonResponse
import uk.co.reiad.library.core.Lesson
import uk.co.reiad.library.core.Stage
import uk.co.reiad.library.core.LadderResponse
import uk.co.reiad.library.core.rungsOf
import uk.co.reiad.library.core.standingOf
import uk.co.reiad.library.core.BodyParser
import uk.co.reiad.library.core.Reader
import uk.co.reiad.library.core.Scenario
import uk.co.reiad.library.core.Comment
import uk.co.reiad.library.ui.Thread
import uk.co.reiad.library.ui.ThreadState

/* Every screen, drawn, so a change to the design is something
   somebody can look at rather than something they have to guess
   at from a diff. */
class ScreensLookTest {
    @get:Rule val pz = paparazzi()

    private val site: SiteManifest = fixture("site.json", SiteManifest.serializer())
    private val words: ToolWords = fixture("tools.json", ToolWords.serializer())

    private fun page(dark: Boolean = false, body: @androidx.compose.runtime.Composable () -> Unit) {
        pz.snapshot {
            ReiadTheme(accent = Accents.GREEN, dark = dark) {
                val c = LocalReiad.current
                Box(Modifier.fillMaxSize().background(c.paper)) { body() }
            }
        }
    }

    @Test fun stock() = page {
        StockScreen(
            words = words,
            state = StockState(),
            onState = {},
            onCopyLink = {},
            onExport = {},
            onLang = {},
            contentPadding = PaddingValues(
                start = Gap.s8, end = Gap.s8, top = Gap.s11, bottom = 90.dp,
            ),
        )
    }

    @Test fun calculators() = page {
        CalculatorsScreen(
            words = words,
            state = CalcState(),
            onState = {},
            lang = "bn",
            onLang = {},
            titles = site.tools.associate { it.id to (it.en to it.bn) },
            contentPadding = PaddingValues(
                start = Gap.s8, end = Gap.s8, top = Gap.s11, bottom = 90.dp,
            ),
        )
    }

    @Test fun shell() = page {
        Shell(
            state = ShellState(site = site, current = null, audience = null, drawerOpen = false),
            onHome = {}, onGroup = {}, onItem = {}, onDrawer = {},
            onSearch = {}, onSettings = {},
                onAccount = {}, onAudience = {},
        ) {
            Home(site, stale = false, note = null, ticks = emptyMap(), audience = null, onOpen = {})
        }
    }

    @Test fun drawer() = page {
        Shell(
            state = ShellState(site = site, current = null, audience = null, drawerOpen = true),
            onHome = {}, onGroup = {}, onItem = {}, onDrawer = {},
            onSearch = {}, onSettings = {},
                onAccount = {}, onAudience = {},
        ) {
            Home(site, stale = false, note = null, ticks = emptyMap(), audience = null, onOpen = {})
        }
    }

    /** The two states that used to be a black page. */
    @Test fun waiting() = page {
        Box(Modifier.fillMaxSize().background(LocalReiad.current.paper)) {
            Column(
                Modifier.fillMaxSize().padding(Gap.s8),
            ) {
                Skeleton(lines = 3)
            }
        }
    }

    @Test fun problem() = page {
        Column(
            Modifier.fillMaxSize().padding(Gap.s8),
        ) {
            Problem(
                title = "The calculators could not load",
                detail = "The site answered 404. That address may not be live yet.",
                onRetry = {},
            )
        }
    }

    @Test fun homeDark() = page(dark = true) {
        Home(site, stale = false, note = null, ticks = emptyMap(), audience = null, onOpen = {})
    }

    @Test fun accountSignedOut() = page {
        AccountScreen(
            reader = null, kept = emptyList(), targets = emptyList(),
            daysActive = emptySet(), ticksOf = { 0 },
            onOpenKept = {}, onRemoveTarget = {}, onExport = {},
            exported = null, onErase = {}, erasing = null,
            problem = null, linkSent = false, bottomPadding = 96.dp,
            onGoogle = {}, onLink = {}, onSignOut = {},
        )
    }


    /** A lesson's prose, drawn from blocks parsed HERE.

        It was `Reading()` and it was FLAKY, which is worse than
        no snapshot: `Reading` parses the body on
        `Dispatchers.Default` and draws a skeleton until that
        lands, deliberately, because a long lesson parsed inside
        composition is a hitch on the frame a reader is watching.
        Whether Paparazzi caught the skeleton or the prose was a
        race with a real thread pool, so the recorded image was
        sometimes six grey bars and nothing failed.

        The picture is of the PROSE, so the parse belongs in the
        test. `ReadingSettlesTest` is what asserts that the screen
        gets from one to the other. */


    /** A thread with a comment, a reply and a body that looks
        like markup, so a picture shows all three rules at once. */
    @Test fun thread() = page {
        Column(Modifier.padding(Gap.s8)) {
            Thread(
                state = ThreadState(
                    slug = "a-piece", section = "insights", count = 3, loading = false,
                    comments = listOf(
                        Comment(
                            id = 1, authorName = "Rony Reiad",
                            body = "The bit about the buy-below price is the part " +
                                "I keep coming back to.",
                            createdAt = "2026-08-20T10:00:00Z",
                            replies = listOf(
                                Comment(
                                    id = 2, parentId = 1, authorName = "Nadia",
                                    body = "Same. It is the only number that changes " +
                                        "what I actually do.",
                                    createdAt = "2026-08-20T11:00:00Z",
                                ),
                            ),
                        ),
                        Comment(
                            id = 3, authorName = "Someone",
                            body = "<b>not markup</b>, on purpose: a body is text.",
                            createdAt = "2026-08-21T09:00:00Z",
                        ),
                    ),
                ),
                signedIn = true,
                onLeave = { _, _ -> },
                onRetry = {},
            )
        }
    }

    @Test fun lessonBody() = page {
        val page = requireNotNull(
            fixture("lesson-papers.json", LessonResponse.serializer()).lesson,
        ) { "the lesson fixture has no page in it" }
        val blocks = BodyParser.parse(page.body).blocks
        Column(Modifier.padding(horizontal = Gap.s8)) {
            /* The head the route draws, with the same four parts:
               the trail, the icon and both names, the definition
               against its accent rail, and the minutes. Written
               out here rather than calling `Reading()` for the
               reason in the doc above. */
            LessonHead(
                title = "\u09b6\u09c7\u09df\u09be\u09b0",
                also = "Share / Stock",
                icon = "book",
                eyebrow = "BASICS",
                oneLiner = page.blurb,
                meta = "\u09e9 \u09ae\u09bf\u09a8\u09bf\u099f \u09aa\u09dc\u09be",
            )
            Spacer(Modifier.height(Gap.s7))
            BodyView(blocks)
        }
    }

    @Test fun ladder() = page {
        val ladder = fixture("money.json", LadderResponse.serializer())
        ReiadTheme(accent = Accents.GREEN, dark = false) {
            Ladder(
                school = site.ladders.first { it.key == "money" },
                stages = ladder.stages,
                ticks = emptySet(),
                stale = false,
                onBack = {}, onOpen = { _, _ -> }, onOpenBook = {},
            )
        }
    }

    @Test fun routine() = page {
        RoutineScreen(
            state = RoutineState(
                loading = false,
                signedOut = false,
                routineId = "r",
                today = "2026-08-23",
                greeting = "\u09b6\u09c1\u09ad \u09b8\u0995\u09be\u09b2",
                shape = fixtureField("routine.json", "shape", RoutineShape.serializer()),
                season = SEASONS[2],
            ),
            onMark = { _, _ -> }, onMood = {}, onNote = {}, onOpenSite = {},
            contentPadding = PaddingValues(
                start = Gap.s8, end = Gap.s8, top = 84.dp, bottom = 96.dp,
            ),
        )
    }

    @Test fun keepSchool() = page {
        Column(Modifier.fillMaxSize().padding(Gap.s8)) {
            Spacer(Modifier.height(Gap.s10))
            KeepSchool(following = false, held = Held(0, 0), lessons = 89, waiting = false, onToggle = {})
            Spacer(Modifier.height(Gap.s9))
            KeepSchool(following = true, held = Held(0, 0), lessons = 89, waiting = true, onToggle = {})
            Spacer(Modifier.height(Gap.s9))
            KeepSchool(following = true, held = Held(41, 620_000), lessons = 89, waiting = false, onToggle = {})
            Spacer(Modifier.height(Gap.s9))
            KeepSchool(following = true, held = Held(89, 1_430_000), lessons = 89, waiting = false, onToggle = {})
            Spacer(Modifier.height(Gap.s10))
            HeldPanel(Held(89, 1_430_000), onForget = {})
        }
    }

    @Test fun group() = page {
        GroupScreen(
            group = site.nav.first { it.id == "learn" },
            accents = site.accents,
            bottomPadding = 96.dp,
            canOpenHere = { true },
            onOpenHere = {},
        )
    }

    @Test fun live() = page {
        LiveScreen(
            state = LiveState(loading = false, trouble = null),
            onConnect = {},
            contentPadding = PaddingValues(
                start = Gap.s8, end = Gap.s8, top = 84.dp, bottom = 96.dp,
            ),
        )
    }

    @Test fun search() = page {
        SearchScreen(site = site, pieces = emptyList(), onOpen = {}, onClose = {})
    }

    @Test fun settings() = page {
        SettingsSheet(
            prefs = Prefs(),
            onChange = {},
            onClose = {},
            held = Held(89, 1_430_000),
            onForget = {},
            /* Pinned, because the real one is the commit and this
               snapshot would then differ on every commit, which
               is a diff that means nothing and hides the ones
               that do. */
            builtFrom = "0000000  1970-01-01 00:00",
        )
    }

    @Test fun workbook() = page {
        val book = fixture("book-stufe-1.json", BookResponse.serializer())
        WorkbookScreen(
            stage = "stufe-1",
            stageName = "Stufe 1",
            school = School.DEUTSCH,
            book = book.book,
            failed = false,
            onOpenOnSite = {},
            days = emptySet(),
            written = emptyMap(),
            answers = emptyMap(),
            bottomPadding = 96.dp,
            onWrite = { _, _ -> },
            onTickDay = {},
            onReveal = {},
            onBack = {},
        )
    }

    @Test fun diet() = page {
        DietScreen(
            state = DietState(
                loading = false,
                today = "2026-08-23",
                profile = DietProfile(
                    heightCm = 172.0, birthYear = 1992, sex = "male",
                    ancestry = "asian", activity = "light", goal = "lose", ratePct = 0.5,
                ),
                day = DietDay(date = "2026-08-23", weightKg = 74.0, waistCm = 88.0),
                entries = listOf(
                    DietEntry(id = "1", date = "2026-08-23", label = "Rice, cooked",
                        labelBn = "\u09ad\u09be\u09a4", qty = 200.0, unit = "g", kcal = 260.0),
                    DietEntry(id = "2", date = "2026-08-23", label = "Dal",
                        labelBn = "\u09a1\u09be\u09b2", qty = 150.0, unit = "g", kcal = 180.0),
                    DietEntry(id = "3", date = "2026-08-23", label = "Egg",
                        labelBn = "\u09a1\u09bf\u09ae", qty = 2.0, unit = "", kcal = 155.0),
                ),
                body = Body(172.0, 74.0, 34.0, Sex.MALE, Ancestry.ASIAN, waistCm = 88.0),
                target = Target(1900, -400, listOf(), 0.5),
                maintenance = 2300.0,
            ),
            onWeight = {}, onRemove = {}, onOpenSite = {},
            contentPadding = PaddingValues(
                start = Gap.s8, end = Gap.s8, top = 84.dp, bottom = 96.dp,
            ),
        )
    }

    @Test fun dietEmpty() = page {
        DietScreen(
            state = DietState(
                loading = false,
                today = "2026-08-23",
                profile = DietProfile(heightCm = 172.0, birthYear = 1992, sex = "male"),
                target = Target(1200, -8, listOf(FloorHit.RATE, FloorHit.RESTING, FloorHit.ABSOLUTE), 0.01),
            ),
            onWeight = {}, onRemove = {}, onOpenSite = {},
            contentPadding = PaddingValues(
                start = Gap.s8, end = Gap.s8, top = 84.dp, bottom = 96.dp,
            ),
        )
    }
}
