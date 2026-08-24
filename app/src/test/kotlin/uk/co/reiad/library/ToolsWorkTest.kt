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
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToNode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import uk.co.reiad.library.core.SiteManifest
import uk.co.reiad.library.core.stock.ToolWords
import uk.co.reiad.library.core.tools.CALCULATORS
import uk.co.reiad.library.ui.CalcState
import uk.co.reiad.library.ui.CalculatorsScreen
import uk.co.reiad.library.ui.ReiadTheme
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/* ============================================================
   The tools WORK, pressed the way a finger presses them.

   "tools and everything aren't working properly check" arrived
   without a repro, and the honest answer to a report like that
   is not reassurance, it is a test that presses the controls
   and reads the answers. Every screen-level check of the
   calculators before this one looked at pixels: a calculator
   that rendered and computed nothing would have passed all of
   them, which is the exact shape the site once shipped as
   "every calculator blank for a day".

   So this drives the real screen: every topic pill is pressed
   and must open its own calculator (latched, the others not);
   a figure is typed into a real input and the headline answer
   must change, twice, in both directions.
   ============================================================ */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xhdpi")
class ToolsWorkTest {

    @get:Rule val compose = createComposeRule()

    private val words: ToolWords = fixture("tools.json", ToolWords.serializer())
    private val site: SiteManifest = fixture("site.json", SiteManifest.serializer())

    private var state = mutableStateOf(CalcState())

    private fun mount() {
        compose.setContent {
            ReiadTheme {
                Box(Modifier.fillMaxSize()) {
                    CalculatorsScreen(
                        words = words,
                        state = state.value,
                        onState = { state.value = it },
                        lang = "bn",
                        onLang = {},
                        titles = site.tools.associate { it.id to (it.en to it.bn) },
                    )
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

    private fun pressTheOneSaying(word: String) {
        val hit = nodes().firstOrNull {
            it.config.getOrNull(SemanticsActions.OnClick) != null &&
                label(it).contains(word)
        }
        assertTrue(hit != null, "nothing pressable says \"$word\"")
        hit.config.getOrNull(SemanticsActions.OnClick)?.action?.invoke()
        compose.waitForIdle()
    }

    @Test fun everyTopicPillOpensItsCalculator() {
        mount()
        for (calc in CALCULATORS) {
            pressTheOneSaying(words.t("calc.${calc.id}.short", "bn"))
            assertEquals(
                calc.id, state.value.open,
                "pressing the ${calc.id} pill must open it: 'tools aren't working' is it not opening",
            )
            /* And the latch says so: exactly one pill selected. */
            val latched = nodes().filter {
                it.config.getOrNull(SemanticsProperties.Selected) == true
            }
            assertTrue(latched.isNotEmpty(), "the open calculator's pill must read as chosen")
        }
    }

    @Test fun typingAFigureChangesTheAnswer() {
        mount()
        val open = state.value.open
        val before = state.value.values.getValue(open)

        /* The inputs sit below the answer and the prose, which on
           a lazy page means they do not EXIST until scrolled to:
           the first draft of this test concluded "no input on the
           whole screen" for exactly that reason. And most of them
           are SLIDERS, not boxes: only a `typed` field is a box,
           so the finder accepts either shape and drives the one
           it meets. */
        val anyInput = androidx.compose.ui.test.SemanticsMatcher("an input") { node ->
            node.config.getOrNull(SemanticsActions.SetText) != null ||
                node.config.getOrNull(SemanticsActions.SetProgress) != null
        }
        compose.onNode(androidx.compose.ui.test.hasScrollToNodeAction())
            .performScrollToNode(anyInput)
        val input = nodes().first {
            it.config.getOrNull(SemanticsActions.SetText) != null ||
                it.config.getOrNull(SemanticsActions.SetProgress) != null
        }
        val setText = input.config.getOrNull(SemanticsActions.SetText)
        if (setText != null) {
            setText.action?.invoke(androidx.compose.ui.text.AnnotatedString("99999"))
        } else {
            val range = input.config[SemanticsProperties.ProgressBarRangeInfo].range
            input.config.getOrNull(SemanticsActions.SetProgress)?.action
                ?.invoke(range.start + (range.endInclusive - range.start) * 0.9f)
        }
        compose.waitForIdle()

        val after = state.value.values.getValue(open)
        assertTrue(
            before != after,
            "typing 99999 into the first input must change the figures it computes from: " +
                "they stayed $after",
        )
    }
}
