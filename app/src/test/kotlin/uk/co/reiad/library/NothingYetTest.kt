package uk.co.reiad.library

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import uk.co.reiad.library.core.Accents
import uk.co.reiad.library.core.Book
import uk.co.reiad.library.core.NavGroup
import uk.co.reiad.library.core.Piece
import uk.co.reiad.library.core.Prefs
import uk.co.reiad.library.core.School
import uk.co.reiad.library.core.stock.ToolWords
import uk.co.reiad.library.ui.AccountScreen
import uk.co.reiad.library.ui.CalcState
import uk.co.reiad.library.ui.CalculatorsScreen
import uk.co.reiad.library.ui.DietScreen
import uk.co.reiad.library.ui.DietState
import uk.co.reiad.library.ui.GroupScreen
import uk.co.reiad.library.Home
import uk.co.reiad.library.Ladder
import uk.co.reiad.library.ui.LiveScreen
import uk.co.reiad.library.ui.LiveState
import uk.co.reiad.library.ui.PieceScreen
import uk.co.reiad.library.ui.PortfolioScreen
import uk.co.reiad.library.ui.ReadingHub
import uk.co.reiad.library.ui.ReiadTheme
import uk.co.reiad.library.ui.RoutineScreen
import uk.co.reiad.library.ui.RoutineState
import uk.co.reiad.library.ui.SearchScreen
import uk.co.reiad.library.ui.SettingsSheet
import uk.co.reiad.library.ui.Shell
import uk.co.reiad.library.ui.ShellState
import uk.co.reiad.library.ui.SkillsScreen
import uk.co.reiad.library.ui.StockScreen
import uk.co.reiad.library.ui.StockState
import uk.co.reiad.library.ui.WorkbookScreen

/* ============================================================
   EVERY SCREEN, WITH NOTHING IN IT.

   ---- the state this is about ----

   A phone that has just been installed, on a train, with no
   signal: no manifest, no schools, no pieces, no account, no
   board, no routine, no log. Every screen in this app can be
   reached in that state, because the menu is the site's menu and
   it does not wait for a fetch to be pressable.

   `ScreensLookTest` draws all of these with the site's own
   fixtures, which is what proves they look right when the data
   is there. Nothing proved they SURVIVE when it is not, and that
   is the half a reader meets first: the empty case is the first
   thirty seconds of every install.

   ---- what it actually catches ----

   The shape of fault that hides here is not a wrong pixel, it is
   a throw: a `first { }` over an empty list, a `getValue` for a
   key nothing produced, a `[0]` on a list the network was going
   to fill. Each of those is invisible in every snapshot, passes
   every arithmetic test, and takes the whole screen down on a
   phone with no signal.

   So there is nothing to look at here and nothing asserted about
   what was drawn. Composing without throwing IS the assertion.
   ============================================================ */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xhdpi")
class NothingYetTest {

    @get:Rule val compose = createComposeRule()

    /** Mounted in the real theme, because half of what a screen
        does on its first frame is ask the palette a question. */
    private fun drawn(body: @Composable () -> Unit) {
        compose.setContent {
            ReiadTheme(accent = Accents.GREEN, dark = false) { body() }
        }
        compose.waitForIdle()
    }

    private val pad = PaddingValues(top = 84.dp, bottom = 96.dp)

    @Test fun theFrontPageWithNoManifest() = drawn {
        Home(site = null, stale = false, note = null, ticks = emptyMap(), audience = null, onOpen = {})
    }

    /** And the front page with a manifest that arrived EMPTY,
        which is a different thing from one that never came: the
        endpoint answered, and answered with nothing. */
    @Test fun theFrontPageWithAnEmptyManifest() = drawn {
        Home(
            site = uk.co.reiad.library.core.SiteManifest(),
            stale = true,
            note = null,
            ticks = emptyMap(),
            audience = null,
            onOpen = {},
        )
    }

    @Test fun theShellWithNoMenu() = drawn {
        Shell(
            state = ShellState(site = null, current = null, audience = null, drawerOpen = false),
            onHome = {}, onGroup = {}, onItem = {}, onDrawer = {},
            onSearch = {}, onSettings = {}, onAccount = {}, onAudience = {},
        ) {}
    }

    /** The menu OPEN over nothing, which is the state a reader
        reaches by pressing More before the first fetch lands. */
    @Test fun theMenuOverNothing() = drawn {
        Shell(
            state = ShellState(site = null, current = null, audience = null, drawerOpen = true),
            onHome = {}, onGroup = {}, onItem = {}, onDrawer = {},
            onSearch = {}, onSettings = {}, onAccount = {}, onAudience = {},
        ) {}
    }

    @Test fun aGroupWithNoItems() = drawn {
        GroupScreen(
            group = NavGroup(id = "learn", label = "Learning"),
            accents = emptyMap(),
            bottomPadding = 96.dp,
            canOpenHere = { false },
            onOpenHere = {},
        )
    }

    @Test fun theSkillsHubWithNoGroup() = drawn {
        SkillsScreen(group = null, head = null, bottomPadding = 96.dp, onOpen = {})
    }

    @Test fun thePortfolioWithNoCases() = drawn {
        PortfolioScreen(cases = emptyList(), head = null, bottomPadding = 96.dp, onOpen = {})
    }

    @Test fun aLadderWithNoStages() = drawn {
        Ladder(
            school = uk.co.reiad.library.core.LadderSchool(key = "money", bn = "টাকা"),
            stages = emptyList(),
            ticks = emptySet(),
            stale = false,
            onBack = {}, onOpen = { _, _ -> }, onOpenBook = {},
        )
    }

    /** A piece whose body never arrived. The parser is handed an
        empty string here, which is the case a lesson opened off
        a cold cache actually presents. */
    @Test fun aPieceWithNoBody() = drawn {
        PieceScreen(
            piece = Piece(slug = "x", title = "A piece"),
            previous = null,
            next = null,
            stale = false,
            bottomPadding = 96.dp,
            kept = null,
            signedIn = false,
            onKeep = { _, _ -> },
            onOpen = {},
            onBack = {},
        )
    }

    @Test fun aReadingHubWithNoPieces() = drawn {
        ReadingHub(
            title = "Insights",
            pieces = emptyList(),
            stale = false,
            bottomPadding = 96.dp,
            onOpen = {},
        )
    }

    @Test fun aWorkbookThatNeverArrived() = drawn {
        WorkbookScreen(
            stage = "s1",
            stageName = "One",
            school = School.MONEY,
            book = null,
            failed = true,
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

    @Test fun searchOverNothing() = drawn {
        SearchScreen(site = null, pieces = emptyList(), onOpen = {}, onClose = {})
    }

    @Test fun theAccountSignedOut() = drawn {
        AccountScreen(
            reader = null,
            kept = emptyList(),
            targets = emptyList(),
            daysActive = emptySet(),
            ticksOf = { 0 },
            onOpenKept = {},
            onRemoveTarget = {},
            onExport = {},
            exported = null,
            onErase = {},
            erasing = null,
            problem = null,
            linkSent = false,
            bottomPadding = 96.dp,
            onGoogle = {},
            onLink = {},
            onSignOut = {},
        )
    }

    @Test fun theRoutineSignedOut() = drawn {
        RoutineScreen(
            state = RoutineState(loading = false, signedOut = true),
            onMark = { _, _ -> }, onMood = {}, onNote = {}, onOpenSite = {},
            contentPadding = pad,
        )
    }

    /** Signed IN with a routine that has no shape yet, which is
        the account-exists-but-empty case the arithmetic has to
        answer with a sentence rather than a nought. */
    @Test fun theRoutineWithNothingInIt() = drawn {
        RoutineScreen(
            state = RoutineState(loading = false, routineId = "r", today = "2026-08-24"),
            onMark = { _, _ -> }, onMood = {}, onNote = {}, onOpenSite = {},
            contentPadding = pad,
        )
    }

    @Test fun theDietWithNoProfile() = drawn {
        DietScreen(
            state = DietState(loading = false, today = "2026-08-24"),
            onWeight = {}, onRemove = {}, onAdd = { _, _ -> }, onOpenSite = {},
            contentPadding = pad,
        )
    }

    @Test fun theStockCheckWithNoWords() = drawn {
        StockScreen(
            words = ToolWords(),
            state = StockState(),
            onState = {}, onCopyLink = {}, onExport = {}, onLang = {},
            contentPadding = pad,
        )
    }

    @Test fun theCalculatorsWithNoWords() = drawn {
        CalculatorsScreen(
            words = ToolWords(),
            state = CalcState(),
            onState = {},
            lang = "bn",
            onLang = {},
            contentPadding = pad,
        )
    }

    @Test fun theLivePortfolioBeforeItConnects() = drawn {
        LiveScreen(state = LiveState(loading = false, trouble = null), onConnect = {}, contentPadding = pad)
    }

    @Test fun theSettingsSheetOverDefaults() = drawn {
        SettingsSheet(prefs = Prefs(), onChange = {}, onClose = {})
    }

    /** A book whose days are empty, which used to reach a `!!`
        on the way to its footer. */
    @Test fun aWorkbookWithNoDays() = drawn {
        WorkbookScreen(
            stage = "s1",
            stageName = "One",
            school = School.MONEY,
            book = Book(),
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
}
