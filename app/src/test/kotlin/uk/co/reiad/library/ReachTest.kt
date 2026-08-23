package uk.co.reiad.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import uk.co.reiad.library.core.Accents
import uk.co.reiad.library.core.SiteManifest
import uk.co.reiad.library.core.stock.ToolWords
import uk.co.reiad.library.ui.CalcState
import uk.co.reiad.library.ui.CalculatorsScreen
import uk.co.reiad.library.ui.LocalReiad
import uk.co.reiad.library.ui.ReiadTheme
import uk.co.reiad.library.ui.StockScreen
import uk.co.reiad.library.ui.StockState
import uk.co.reiad.library.ui.AccountScreen
import uk.co.reiad.library.ui.Problem
import uk.co.reiad.library.ui.Shell
import uk.co.reiad.library.ui.ShellState
import uk.co.reiad.library.core.LadderResponse
import kotlin.test.assertTrue

/* ============================================================
   Can a finger reach it, and does a screen reader know what it
   is.

   Both are questions about the SEMANTICS TREE rather than about
   the source, and that is why this runs the real Compose runtime
   under Robolectric rather than grepping for `contentDescription`:
   a label can be on a parent, a target can be padded up to size
   by a modifier three levels away, and neither is visible in the
   text of a file.

   ---- the two thresholds, and where they come from ----

   48dp is Android's own, and it is the one the platform's
   accessibility scanner reports on. The site's `--tap` is 44,
   which is the WCAG 2.5.8 minimum and is what every control in
   this app is built at, so the assertion is 44 with the 48
   recommendation written down: raising every control four pixels
   to satisfy a rounder number would move it away from the site
   for no reader's benefit.

   A node with an action and NO label is the harder failure. It is
   invisible in a screenshot, invisible in use, and reads out as
   "button" with nothing after it.
   ============================================================ */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xhdpi")
class ReachTest {

    @get:Rule val compose = createComposeRule()

    private val site: SiteManifest = fixture("site.json", SiteManifest.serializer())
    private val words: ToolWords = fixture("tools.json", ToolWords.serializer())

    /** Everything a reader can act on. */
    private fun SemanticsNode.actionable(): List<SemanticsNode> =
        buildList {
            val acts = config.getOrNull(SemanticsActions.OnClick) != null ||
                config.getOrNull(SemanticsActions.SetProgress) != null
            if (acts) add(this@actionable)
            for (child in children) addAll(child.actionable())
        }

    /** What a screen reader would say for it: its own label, or
        the text of anything inside it. A label on a parent is a
        real label, which is exactly what a source scan misses. */
    private fun SemanticsNode.said(): String {
        config.getOrNull(SemanticsProperties.ContentDescription)
            ?.firstOrNull()?.takeIf { it.isNotBlank() }?.let { return it }
        config.getOrNull(SemanticsProperties.Text)
            ?.joinToString(" ") { it.text }?.takeIf { it.isNotBlank() }?.let { return it }
        val inside = children.joinToString(" ") { it.said() }.trim()
        return inside
    }

    private fun audit(
        name: String,
        scale: Float = 1f,
        body: @androidx.compose.runtime.Composable () -> Unit,
    ) {
        compose.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(base.density, scale),
            ) {
                ReiadTheme(accent = Accents.GREEN, dark = false) {
                    val c = LocalReiad.current
                    Box(Modifier.fillMaxSize().background(c.paper)) { body() }
                }
            }
        }
        val density = compose.density
        val root = compose.onRoot().fetchSemanticsNode()
        val targets = root.actionable()
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
            /* Zero is a node the layout gave no size, which is a
               different fault from a small one and is not this
               check's: an invisible target is caught by the label
               rule above or by nothing being drawn at all. */
            (w > 0.dp && w < 44.dp) || (h > 0.dp && h < 44.dp)
        }
        assertTrue(
            small.isEmpty(),
            "$name: ${small.size} target(s) under the site's 44dp tap size, " +
                "at ${small.map { it.said() to it.size }}",
        )
    }

    @Test fun theStockCheck() = audit("stock") {
        StockScreen(
            words = words,
            state = StockState(),
            onState = {}, onCopyLink = {}, onExport = {}, onLang = {},
        )
    }

    @Test fun theCalculators() = audit("calculators") {
        CalculatorsScreen(
            words = words,
            state = CalcState(),
            onState = {}, lang = "bn", onLang = {},
            titles = site.tools.associate { it.id to (it.en to it.bn) },
        )
    }

    @Test fun theFrontPage() = audit("home") {
        Home(site, stale = false, note = null, ticks = emptyMap(), audience = null, onOpen = {})
    }

    @Test fun theAccountSignedOut() = audit("account") {
        AccountScreen(
            reader = null, kept = emptyList(), targets = emptyList(),
            daysActive = emptySet(), ticksOf = { 0 },
            onOpenKept = {}, onRemoveTarget = {}, onExport = {},
            exported = null, onErase = {}, erasing = null,
            problem = null, linkSent = false, bottomPadding = 96.dp,
            onGoogle = {}, onLink = {}, onSignOut = {},
        )
    }

    @Test fun theSchoolLadder() = audit("ladder") {
        val ladder = fixture("money.json", LadderResponse.serializer())
        Ladder(
            school = site.ladders.first { it.key == "money" },
            stages = ladder.stages,
            ticks = emptySet(),
            stale = false,
            onBack = {}, onOpen = { _, _ -> }, onOpenBook = {},
        )
    }

    @Test fun theShell() = audit("shell") {
        Shell(
            state = ShellState(site = site, current = null, audience = null, drawerOpen = false),
            onHome = {}, onGroup = {}, onItem = {}, onDrawer = {},
            onSearch = {}, onSettings = {},
                onAccount = {}, onAudience = {},
        ) {
            Home(site, stale = false, note = null, ticks = emptyMap(), audience = null, onOpen = {})
        }
    }

    @Test fun theMenu() = audit("drawer") {
        Shell(
            state = ShellState(site = site, current = null, audience = null, drawerOpen = true),
            onHome = {}, onGroup = {}, onItem = {}, onDrawer = {},
            onSearch = {}, onSettings = {},
                onAccount = {}, onAudience = {},
        ) {
            Home(site, stale = false, note = null, ticks = emptyMap(), audience = null, onOpen = {})
        }
    }

    /** The failure state, which is the one screen whose whole job
        is to be actionable: a Problem with an unreachable retry is
        a dead end wearing a button. */
    @Test fun theFailureState() = audit("problem") {
        Problem(
            title = "The calculators could not load",
            detail = "The site answered 404.",
            onRetry = {},
        )
    }

    /* ------------------------------------------------------------
       And all of it again at twice the type size.

       `sp` follows the reader's own setting and Android goes to
       200%. Everything above is measured in `dp`, so a target
       that passes at 100% passes at 200% too; what does NOT is a
       row whose HEIGHT came from its text, which at double size
       pushes a fixed-height parent's content out of it. A target
       that has grown past its parent reads as a hit box in the
       wrong place.

       This runs the same audit under a doubled font scale, which
       is the only way to see it: nothing about the source
       changes.
       ------------------------------------------------------------ */

    @Test fun theFrontPageAtDoubleSize() = audit("home at 200%", scale = 2f) {
        Home(site, stale = false, note = null, ticks = emptyMap(), audience = null, onOpen = {})
    }

    @Test fun theCalculatorsAtDoubleSize() = audit("calculators at 200%", scale = 2f) {
        CalculatorsScreen(
            words = words,
            state = CalcState(),
            onState = {}, lang = "bn", onLang = {},
            titles = site.tools.associate { it.id to (it.en to it.bn) },
        )
    }

    @Test fun theMenuAtDoubleSize() = audit("drawer at 200%", scale = 2f) {
        Shell(
            state = ShellState(site = site, current = null, audience = null, drawerOpen = true),
            onHome = {}, onGroup = {}, onItem = {}, onDrawer = {},
            onSearch = {}, onSettings = {},
                onAccount = {}, onAudience = {},
        ) {
            Home(site, stale = false, note = null, ticks = emptyMap(), audience = null, onOpen = {})
        }
    }
}
