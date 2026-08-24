package uk.co.reiad.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
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
import uk.co.reiad.library.ui.ReiadTheme
import uk.co.reiad.library.ui.Shell
import uk.co.reiad.library.ui.ShellState
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/* ============================================================
   Nothing on screen at once does the same job twice.

   ---- the report ----

   "same button twice.. that is not a logical thing", with the two
   circled: the person button at the top right and the "আপনার" tab
   at the bottom, both lit, both going to the page already on
   screen.

   Neither was a bug in the ordinary sense. The top bar carries
   the account because the site's does, on every page, and
   `AccountReachTest` exists to make sure it never stops. The bar
   shows the group you are standing in so it is never a row of
   five unlit tabs. Each rule is right; the two together put one
   destination on screen twice, and no test could see that,
   because every test here asks whether something is REACHABLE.

   ---- so this asks the opposite question ----

   Not "can a reader get there" but "how many ways are on screen
   at once", and the answer for a destination has to be one. A
   MENU is not one of them: a drawer listing every destination is
   a different kind of thing from a dedicated control, which is
   why these mount the shell with the drawer shut.

   The `you` group is the one that would come back. Its single
   listed item is the account, so any rule that lets a group into
   the bar has to skip it or this returns.
   ============================================================ */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xhdpi")
class OnceOnlyTest {

    @get:Rule val compose = createComposeRule()

    private val site: SiteManifest = fixture("site.json", SiteManifest.serializer())

    private var mounted = false

    /** Which groups a press opened, so a test can ask where a
        control actually went rather than what it was called. */
    private val groupPresses = mutableListOf<String>()

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

    /** Where the reader is standing, as state, because
        `setContent` may be called once per test and three of
        these need to walk the chrome from more than one place. */
    private val standing = mutableStateOf<String?>(null)

    /** The chrome as a reader meets it: drawer shut, standing
        wherever `standing` says. */
    private fun mount(current: String?, onItem: (NavItem) -> Unit = {}, onAccount: () -> Unit = {}) {
        standing.value = current
        if (mounted) {
            compose.waitForIdle()
            return
        }
        mounted = true
        compose.setContent {
            ReiadTheme {
                Box(Modifier.fillMaxSize()) {
                    Shell(
                        state = ShellState(site, standing.value, null, drawerOpen = false, signedIn = true),
                        onHome = {},
                        onGroup = { groupPresses.add(it.id) },
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

    /** Pressable things whose label mentions a word, deduplicated
        by the node's own bounds: Compose nests a semantics node
        inside a clickable one and both carry the label, so
        counting nodes would report two for one control. */
    private fun controlsSaying(word: String): List<SemanticsNode> =
        nodes()
            .filter { pressable(it) && label(it).contains(word, ignoreCase = true) }
            .distinctBy { it.boundsInRoot.left to it.boundsInRoot.top }

    /* ---------- the one that was reported ---------- */

    /** THE REPORT, as an assertion, and it counts by DESTINATION
        rather than by name.

        The first draft looked for controls whose label mentions
        the account, and it passed against the broken build: the
        bar tab is labelled "আপনার", which does not contain the
        word "account" in any language a `contains` would match.
        A duplicate does not have to be called the same thing, so
        this presses every control on screen and counts how many
        of them end up at the account. */
    @Test fun standingOnTheAccountThereIsOneWayToIt() {
        val ways = mutableListOf<String>()
        mount(
            current = "account",
            onItem = { if (it.key == "account") ways.add("nav item") },
            onAccount = { ways.add("the account control") },
        )
        /* A group tab opens the group. The `you` group's only
           listed item is the account, so a tab that opens it IS a
           way to the account: this is what the two circles in the
           report were. */
        val toTheAccount = { id: String -> if (id == "you") ways.add("the `$id` tab") else null }

        for (node in nodes().filter { pressable(it) }
            .distinctBy { it.boundsInRoot.left to it.boundsInRoot.top }) {
            node.config.getOrNull(SemanticsActions.OnClick)?.action?.invoke()
        }
        compose.waitForIdle()
        /* The group handler is recorded through `mount`'s own
           `onGroup`, which the helper wires to this. */
        groupPresses.forEach { toTheAccount(it) }

        assertEquals(
            1, ways.size,
            "pressing everything on screen reached the account ${ways.size} times, by: " +
                "$ways. Two controls for one destination is a reader asking what the " +
                "difference is, and there is none.",
        )
    }

    /** And off it, still exactly one. This is the half that would
        pass by accident if the top bar's control were simply
        deleted, which is the wrong fix: the site carries it on
        every page and `AccountReachTest` says why. */
    @Test fun standingAnywhereElseThereIsStillOneWayToIt() {
        mount(current = null)
        assertEquals(1, controlsSaying("account").size)
    }

    /** The bar never offers the `you` group.

        Asserted by NAME rather than by counting tabs, because the
        rule is about that group and not about how many fit: it
        holds one listed item, the account, and the account has a
        control of its own. */
    @Test fun theBarNeverOffersTheGroupWhoseOnlyItemIsTheAccount() {
        val you = site.nav.firstOrNull { it.id == "you" }
        assertTrue(you != null, "the fixture has no `you` group, so this proves nothing")
        val tab = uk.co.reiad.library.ui.tabLabel(you.label)

        for (standing in listOf(null, "account", "admin")) {
            mount(current = standing)
            /* CONTAINS, not equals, and the difference is why the
               first draft of this test passed against the broken
               build. `label()` joins the content description and
               the drawn text, and a bar tab carries the same word
               as both, so it reads "আপনার আপনার" and an equality
               check matches nothing. A test that cannot fail is
               worse than no test: it reports that the thing it
               was written for is fixed. */
            val tabs = nodes().filter { pressable(it) && label(it).contains(tab) }
            assertTrue(
                tabs.isEmpty(),
                "standing on $standing, the bar offers a `$tab` tab. Its group holds " +
                    "the account and nothing else listed, and the account is already " +
                    "in the top bar.",
            )
        }
    }

    /* ---------- and the same question for the rest ---------- */

    /** No two pressable things in the chrome carry the same
        accessible name.

        A screen reader announces the name and nothing else, so two
        controls called the same thing are two identical
        announcements: a reader hears "Search" twice and has no way
        to know whether they do the same thing. It is also the
        cheapest possible detector for the fault above, because a
        duplicate destination almost always arrives as a duplicate
        name first. */
    @Test fun noTwoControlsInTheChromeAreCalledTheSameThing() {
        for (standing in listOf(null, "account", "money")) {
            mount(current = standing)
            val named = nodes()
                .filter { pressable(it) }
                .distinctBy { it.boundsInRoot.left to it.boundsInRoot.top }
                .map { label(it).trim() }
                .filter { it.isNotEmpty() }
            val twice = named.groupingBy { it }.eachCount().filterValues { it > 1 }
            assertTrue(
                twice.isEmpty(),
                "standing on $standing, the chrome offers these names more than once: " +
                    "$twice. Two controls with one name are two identical announcements " +
                    "to a screen reader and, usually, two ways to the same place.",
            )
        }
    }

    /** Pressing the account control calls the account and nothing
        else. A duplicate that fires two handlers is worse than one
        that fires none: the screen changes twice. */
    @Test fun theAccountControlDoesOneThing() {
        var account = 0
        var items = 0
        mount(current = null, onItem = { items += 1 }, onAccount = { account += 1 })
        controlsSaying("account").single()
            .config.getOrNull(SemanticsActions.OnClick)?.action?.invoke()
        compose.waitForIdle()
        assertEquals(1, account)
        assertEquals(0, items, "it also opened a nav item, so two things happened on one press")
    }
}
