package uk.co.reiad.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
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
import uk.co.reiad.library.ui.Problem
import uk.co.reiad.library.ui.ReiadTheme
import uk.co.reiad.library.ui.Shell
import uk.co.reiad.library.ui.ShellState
import uk.co.reiad.library.ui.Skeleton
import uk.co.reiad.library.ui.StockScreen
import uk.co.reiad.library.ui.StockState
import uk.co.reiad.library.ui.RoutineScreen
import uk.co.reiad.library.ui.RoutineState
import uk.co.reiad.library.core.routine.RoutineShape
import uk.co.reiad.library.core.routine.SEASONS
import uk.co.reiad.library.ui.AccountScreen
import uk.co.reiad.library.core.LessonResponse
import uk.co.reiad.library.core.Lesson
import uk.co.reiad.library.core.Stage
import uk.co.reiad.library.core.LadderResponse

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
            onSearch = {}, onSettings = {}, onAudience = {},
        ) {
            Home(site, stale = false, note = null, ticks = emptyMap(), audience = null, onOpen = {})
        }
    }

    @Test fun drawer() = page {
        Shell(
            state = ShellState(site = site, current = null, audience = null, drawerOpen = true),
            onHome = {}, onGroup = {}, onItem = {}, onDrawer = {},
            onSearch = {}, onSettings = {}, onAudience = {},
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

    @Test fun lesson() = page {
        val page = fixture("lesson-papers.json", LessonResponse.serializer())
        Reading(
            school = site.ladders.first { it.key == "money" },
            stage = Stage(slug = "basics-1", bn = "\u09ac\u09c7\u09b8\u09bf\u0995", en = "Basics"),
            lesson = Lesson(slug = "x", bn = "\u09ac\u09bf\u0993 \u0985\u09cd\u09af\u09be\u0995\u09be\u0989\u09a8\u09cd\u099f"),
            page = page.lesson,
            ticked = false, isMoney = true,
            onBack = {}, onTick = {}, checks = emptySet(), onCheck = {}, lessonKey = "k",
        )
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
}
