package uk.co.reiad.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import uk.co.reiad.library.core.Accents
import uk.co.reiad.library.core.KIND_SIZES
import uk.co.reiad.library.core.SiteManifest
import uk.co.reiad.library.core.WidgetSize
import uk.co.reiad.library.core.catalogueFloor
import uk.co.reiad.library.core.spanOf
import uk.co.reiad.library.ui.BoardActions
import uk.co.reiad.library.ui.BoardData
import uk.co.reiad.library.ui.DRAWABLE
import uk.co.reiad.library.ui.ReiadTheme
import uk.co.reiad.library.ui.Sway
import uk.co.reiad.library.ui.Widget
import kotlin.test.assertTrue
import kotlin.test.fail

/* ============================================================
   EVERY WIDGET, AT EVERY SIZE IT OFFERS.

   ---- why this is not covered already ----

   The picker offers a kind's sizes out of `KIND_SIZES`, and the
   resize badge steps through them. So every pair in that table
   is a thing a reader can reach with two presses, and the board
   snapshots hold about four of them: the ones somebody thought
   to arrange while writing a test.

   A kind that draws at `wide` and throws at `small` is a crash
   nobody meets until the day they press the size badge, and it
   is invisible to every other test in this repository: the
   arithmetic passes, the snapshot passes, the board packs
   correctly, and the app dies on a button.

   ---- and twice over ----

   Once with the site's real manifest and once with NOTHING,
   because a widget's two hard cases are the day the data is
   there and the day it is not, and the second is every first
   launch.
   ============================================================ */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xhdpi")
class EverySizeTest {

    @get:Rule val compose = createComposeRule()

    private val site: SiteManifest = fixture("site.json", SiteManifest.serializer())

    private fun data(full: Boolean) = BoardData(
        site = if (full) site else null,
        ticks = if (full) mapOf("money" to setOf("a", "b")) else emptyMap(),
        bookmarks = emptyMap(),
        pieces = emptyList(),
        sway = Sway(),
        icons = emptyMap(),
        lang = "bn",
    )

    private val act = BoardActions(
        onSchool = {}, onItem = {}, onPiece = {}, onResume = { _, _ -> }, onStory = {},
    )

    /** The width the grid would give a widget of this span on a
        412dp phone, so a square is drawn as a square rather than
        at the full width where nothing can be too narrow. */
    private fun widthFor(size: WidgetSize) =
        if (spanOf(size) == 1) 178.dp else 372.dp

    private fun drawEvery(full: Boolean) {
        val kinds = catalogueFloor().filter { it.id in DRAWABLE }
        assertTrue(kinds.size >= 10, "only ${kinds.size} kinds; the floor is wrong")

        compose.setContent {
            ReiadTheme(accent = Accents.GREEN, dark = false) {
                Box {
                    for (kind in kinds) {
                        for (id in KIND_SIZES[kind.id].orEmpty()) {
                            val size = WidgetSize.of(id)
                                ?: fail("${kind.id} offers the size $id, which is not one")
                            Box(Modifier.width(widthFor(size))) {
                                Widget(kind.id, size, data(full), act)
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    @Test fun everyKindAtEverySizeWithTheSiteBehindIt() = drawEvery(full = true)

    @Test fun everyKindAtEverySizeWithNothingBehindIt() = drawEvery(full = false)

    /** And the table itself is honest: a kind that offers a size
        the app cannot draw would put a badge on a widget that
        steps it into nothing. */
    @Test fun everySizeOfferedIsARealSize() {
        for ((kind, sizes) in KIND_SIZES) {
            assertTrue(sizes.isNotEmpty(), "$kind offers no size at all")
            for (id in sizes) {
                assertTrue(WidgetSize.of(id) != null, "$kind offers $id, which is not a size")
            }
        }
    }
}
