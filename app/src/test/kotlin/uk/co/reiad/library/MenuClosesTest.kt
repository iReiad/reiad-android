package uk.co.reiad.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.geometry.Offset
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import uk.co.reiad.library.core.SiteManifest
import uk.co.reiad.library.ui.ReiadTheme
import uk.co.reiad.library.ui.Shell
import uk.co.reiad.library.ui.ShellState
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/* ============================================================
   The menu CLOSES, all four ways.

   "pressing menu button opens that, but never closes." The open
   half had a test from the day the drawer existed; the close
   half never did, and a control that only opens is a trap with
   five ways in and none out that anybody can find. So this
   presses the actual controls on the actual shell and asserts
   the drawer state each callback would leave:

   More again toggles it shut. The scrim closes it. A tab both
   navigates and closes. A menu row closes on its way to the
   destination. Each is a separate test because each is a
   separate wire, and the report was about exactly one of them
   being loose.
   ============================================================ */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xhdpi")
class MenuClosesTest {

    @get:Rule val compose = createComposeRule()

    private val site: SiteManifest = fixture("site.json", SiteManifest.serializer())

    private var drawer = mutableStateOf(true)
    private val groupsOpened = mutableListOf<String>()

    private fun mount() {
        compose.setContent {
            ReiadTheme {
                Box(Modifier.fillMaxSize()) {
                    Shell(
                        state = ShellState(site, null, null, drawerOpen = drawer.value, signedIn = true),
                        onHome = {},
                        onGroup = { groupsOpened.add(it.id) },
                        onItem = {},
                        onDrawer = { drawer.value = it },
                        onSearch = {},
                        onSettings = {},
                        onAccount = {},
                        onAudience = {},
                    ) { Box(Modifier.fillMaxSize()) }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun walk(node: SemanticsNode, into: MutableList<SemanticsNode>) {
        into.add(node)
        node.children.forEach { walk(it, into) }
    }

    private fun nodes(): List<SemanticsNode> =
        mutableListOf<SemanticsNode>().also { walk(compose.onRoot().fetchSemanticsNode(), it) }

    private fun label(n: SemanticsNode): String =
        (n.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ") ?: "") +
            " " + (n.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") ?: "")

    private fun press(word: String) {
        val hit = nodes().firstOrNull {
            it.config.getOrNull(SemanticsActions.OnClick) != null &&
                label(it).contains(word, ignoreCase = true)
        }
        assertTrue(hit != null, "nothing pressable says \"$word\" while the menu is open")
        hit.config.getOrNull(SemanticsActions.OnClick)?.action?.invoke()
        compose.waitForIdle()
    }

    @Test fun moreTogglesItShut() {
        mount()
        assertTrue(drawer.value)
        press("More")
        assertEquals(false, drawer.value, "More opened the menu; More again must close it")
    }

    /** The same press as a FINGER, through the segmented track's
        own gesture, because the phone does not press semantics
        nodes: the report was about a real thumb on a real bar
        with the sheet open. More is the last of five stops, so
        the tap lands at nine tenths of the track. */
    /** THE SEQUENCE THE PHONE ACTUALLY RUNS: closed, a finger
        opens it, the same finger closes it. The first draft of
        the finger test mounted with the menu already open and
        passed against the broken build, because the fault was a
        stale capture: `Segmented`'s pointer gesture kept the
        FIRST composition's `onChoose`, whose More stop closed
        over drawerOpen=false for ever, so every real press said
        "open". Semantics presses rebuilt fresh each composition,
        which is why the accessibility path worked and the thumb
        did not. */
    @Test fun aFingerRoundTripOpensThenCloses() {
        drawer.value = false
        mount()
        compose.onNodeWithTag("bottombar").performTouchInput {
            click(Offset(width * 0.9f, height * 0.55f))
        }
        compose.waitForIdle()
        assertEquals(true, drawer.value, "the first press opens the menu")
        compose.onNodeWithTag("bottombar").performTouchInput {
            click(Offset(width * 0.9f, height * 0.55f))
        }
        compose.waitForIdle()
        assertEquals(false, drawer.value, "the second press must close it: 'opens that, but never closes'")
    }

    @Test fun aFingerOnMoreTogglesItShut() {
        mount()
        assertTrue(drawer.value)
        compose.onNodeWithTag("bottombar").performTouchInput {
            click(Offset(width * 0.9f, height * 0.55f))
        }
        compose.waitForIdle()
        assertEquals(false, drawer.value, "a finger on More with the menu open must close it")
    }

    @Test fun aTabNavigatesAndCloses() {
        mount()
        /* The second stop of five: a group tab, by position,
           because pressing "the first thing that says শেখা" found
           the audience switch inside the open sheet instead of
           the bar. */
        compose.onNodeWithTag("bottombar").performTouchInput {
            click(Offset(width * 0.3f, height * 0.55f))
        }
        compose.waitForIdle()
        assertEquals(false, drawer.value, "a bar tab pressed with the menu open must also close it")
        assertTrue(groupsOpened.isNotEmpty(), "and it must still open its group")
    }

    @Test fun aMenuRowClosesOnItsWayOut() {
        mount()
        val item = site.nav.flatMap { it.items }.first { !it.soon }
        press(item.label)
        assertEquals(false, drawer.value)
    }
}
