package uk.co.reiad.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import uk.co.reiad.library.core.NavItem
import uk.co.reiad.library.core.SiteManifest
import uk.co.reiad.library.ui.LocalReiad
import uk.co.reiad.library.ui.ReiadTheme
import uk.co.reiad.library.ui.Shell
import uk.co.reiad.library.ui.ShellState
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/* ============================================================
   Can a reader get to their account, and in how many taps.

   ---- why this is its own file ----

   "clicking on account just takes me back to library" was a true
   report about a screen that renders perfectly, that
   `ScreensLookTest` snapshots, that `ReachTest` walks for labels
   and target sizes, and that `goTo()` sends the right nav item
   to. Every one of those passed. None of them asked the question
   a reader asks, which is not "does the account screen work" but
   "how do I get there from where I am standing".

   The answer was: open the bottom bar's More, scroll a drawer
   past four groups, find a fifth group with one row in it. The
   site does not ask that. Its top bar carries the account control
   as the third button in `.top-tools`, after search and the theme
   toggle, on every page: `aab/src/signin.ts` appends it there and
   says so. The app's top bar had the first two and stopped.

   So this file asserts the CHROME, and it asserts it the way a
   finger meets it: the account is reachable from the top-level
   shell with the drawer shut, and the drawer still reaches it
   too. A screen nobody can find is a screen that does not exist.
   ============================================================ */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xhdpi")
class AccountReachTest {

    @get:Rule val compose = createComposeRule()

    private val site: SiteManifest = fixture("site.json", SiteManifest.serializer())

    private fun walk(node: SemanticsNode, into: MutableList<SemanticsNode>) {
        into.add(node)
        node.children.forEach { walk(it, into) }
    }

    private fun nodes(): List<SemanticsNode> =
        mutableListOf<SemanticsNode>().also { walk(compose.onRoot().fetchSemanticsNode(), it) }

    private fun label(n: SemanticsNode): String =
        (n.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ") ?: "") +
            " " + (n.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") ?: "")

    private fun pressable(n: SemanticsNode): Boolean =
        n.config.getOrNull(SemanticsActions.OnClick) != null

    /** Everything the shell offers with nothing opened. */
    private fun mountShell(
        drawerOpen: Boolean = false,
        onItem: (NavItem) -> Unit = {},
        onAccount: () -> Unit = {},
    ) {
        compose.setContent {
            ReiadTheme {
                Box(Modifier.fillMaxSize()) {
                    Shell(
                        state = ShellState(site, null, null, drawerOpen),
                        onHome = {},
                        onGroup = {},
                        onItem = onItem,
                        onDrawer = {},
                        onSearch = {},
                        onSettings = {},
                        onAccount = onAccount,
                        onAudience = {},
                    ) { Box(Modifier.fillMaxSize()) }
                }
            }
        }
        compose.waitForIdle()
    }

    /** The fixture really does have an account row, so a failure
        below is the chrome's and not the manifest's. */
    @Test fun theManifestHasAnAccount() {
        val rows = site.nav.flatMap { it.items }
        assertTrue(
            rows.any { it.key == "account" },
            "shared/nav.ts should carry an account entry and the fixture should have it; " +
                "without that every assertion here is checking the wrong thing",
        )
    }

    /** ONE TAP, with nothing opened first.

        Not "reachable eventually". The site puts this control on
        every page and so does the app now: a reader standing on
        the front page can see where their account is without
        opening anything. */
    @Test fun theAccountIsOneTapFromAnywhere() {
        var pressed = 0
        mountShell(onAccount = { pressed += 1 })
        val found = nodes().filter { pressable(it) && label(it).contains("account", true) }
        assertTrue(
            found.isNotEmpty(),
            "nothing on the top-level chrome mentions the account. The reader's way in was " +
                "More, then a drawer scrolled past four groups, and the report that came " +
                "back was 'clicking on account just takes me back to library'. " +
                "What is pressable up here: " +
                nodes().filter { pressable(it) }.joinToString(", ") { label(it).trim() },
        )
        found.first().config.getOrNull(SemanticsActions.OnClick)?.action?.invoke()
        compose.waitForIdle()
        assertEquals(1, pressed, "the account control should call onAccount and nothing else")
    }

    /** And it is a real target, not a four-pixel hit box. */
    @Test fun theAccountControlIsBigEnough() {
        mountShell()
        val found = nodes().first { pressable(it) && label(it).contains("account", true) }
        val w = found.size.width / compose.density.density
        val h = found.size.height / compose.density.density
        assertTrue(
            w >= 44f && h >= 44f,
            "the account control is ${w}x${h}dp and the site's `--tap` is 44",
        )
    }

    /** The drawer still reaches it, because the drawer is the
        menu and the menu is one table. Losing the row from there
        in the course of adding a button would be trading one way
        in for another. */
    @Test fun theDrawerStillHasTheAccountRow() {
        var went: NavItem? = null
        mountShell(drawerOpen = true, onItem = { went = it })
        /* By its TEXT, not by its label. The top bar is still on
           screen behind the drawer and its account button reads
           "Sign in to your account", so a substring match on the
           whole label finds that first and this passed against
           the wrong control. The drawer's row carries the nav
           table's own label as text. */
        val row = nodes().firstOrNull {
            pressable(it) &&
                it.config.getOrNull(SemanticsProperties.Text)
                    ?.any { t -> t.text.trim().equals("Account", true) } == true
        }
        assertTrue(row != null, "the drawer lost its account row")
        row.config.getOrNull(SemanticsActions.OnClick)?.action?.invoke()
        compose.waitForIdle()
        assertEquals(
            "account",
            went?.key,
            "the drawer's account row should hand the account nav item back",
        )
    }
}

/* ============================================================
   The same question on a tablet, where the chrome is a rail.

   A rail is not a small bottom bar and the difference is what
   went wrong: the bar shows five GROUPS, so a tab opening its
   group is right; the rail shows every ITEM, and every row of it
   called `onGroup` too. Seventeen rows, each landing one tap
   short of the thing it named, and both render identically.

   `w840dp` is the width at which `rememberChrome()` answers with
   a rail rather than a bar.
   ============================================================ */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w840dp-h1000dp-xhdpi")
class RailReachTest {

    @get:Rule val compose = createComposeRule()

    private val site: SiteManifest = fixture("site.json", SiteManifest.serializer())

    private fun walk(node: SemanticsNode, into: MutableList<SemanticsNode>) {
        into.add(node)
        node.children.forEach { walk(it, into) }
    }

    /** Every row of the rail hands back the item it names. */
    @Test fun everyRailRowOpensTheItemItNames() {
        val went = mutableListOf<NavItem>()
        compose.setContent {
            ReiadTheme {
                Box(Modifier.fillMaxSize()) {
                    Shell(
                        state = ShellState(site, null, null, false),
                        onHome = {},
                        onGroup = { error("a rail row opened a GROUP: it lists items") },
                        onItem = { went.add(it) },
                        onDrawer = {},
                        onSearch = {},
                        onSettings = {},
                        onAccount = {},
                        onAudience = {},
                    ) { Box(Modifier.fillMaxSize()) }
                }
            }
        }
        compose.waitForIdle()

        val all = mutableListOf<SemanticsNode>().also {
            walk(compose.onRoot().fetchSemanticsNode(), it)
        }
        val labels = site.nav.flatMap { it.items }.map { it.label }.toSet()
        val rows = all.filter { n ->
            n.config.getOrNull(SemanticsActions.OnClick) != null &&
                n.config.getOrNull(SemanticsProperties.Text)
                    ?.any { it.text.trim() in labels } == true
        }
        assertTrue(
            rows.isNotEmpty(),
            "no rail rows were found at all, so this is asserting nothing. " +
                "Pressable here: " + all.mapNotNull { n ->
                    n.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }
                }.joinToString(" | "),
        )
        for (row in rows) row.config.getOrNull(SemanticsActions.OnClick)?.action?.invoke()
        compose.waitForIdle()
        assertEquals(
            rows.size,
            went.size,
            "every rail row should hand back one nav item",
        )
        assertTrue(
            went.any { it.key == "account" },
            "the rail should reach the account: it got " + went.map { it.key },
        )
    }
}
