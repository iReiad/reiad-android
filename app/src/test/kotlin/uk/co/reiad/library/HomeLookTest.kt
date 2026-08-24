package uk.co.reiad.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import org.junit.Rule
import org.junit.Test
import uk.co.reiad.library.core.Accents
import uk.co.reiad.library.core.SiteManifest
import uk.co.reiad.library.ui.LocalReiad
import uk.co.reiad.library.ui.ReiadTheme

class HomeLookTest {
    @get:Rule val pz = paparazzi()

    private val site: SiteManifest = fixture("site.json", SiteManifest.serializer())

    @Test fun home() {
        pz.snapshot {
            ReiadTheme(accent = Accents.GREEN, dark = false) {
                val c = LocalReiad.current
                Box(Modifier.fillMaxSize().background(c.paper)) {
                    Home(
                        site = site,
                        stale = false,
                        note = null,
                        ticks = mapOf("money" to setOf("a", "b", "c")),
                        audience = null,
                        onOpen = {},
                    )
                }
            }
        }
    }

    /** A board with two SMALLS on it, which have to sit side by
        side: the paired grid is what makes the three sizes a
        home screen's rather than a stretch, and no other
        snapshot holds a board that was actually arranged. */
    @Test fun homePaired() {
        pz.snapshot {
            ReiadTheme(accent = Accents.GREEN, dark = false) {
                val c = LocalReiad.current
                Box(Modifier.fillMaxSize().background(c.paper)) {
                    Home(
                        site = site,
                        stale = false,
                        note = null,
                        ticks = mapOf("money" to setOf("a", "b", "c")),
                        audience = null,
                        onOpen = {},
                        board = listOf(
                            "progress:small", "stock:small",
                            "routine:wide", "streak:wide",
                            "continue:wide", "pulse:tall",
                        ),
                        daysActive = setOf(
                            "2026-08-20", "2026-08-21", "2026-08-22", "2026-08-23",
                            "2026-07-01", "2026-06-15", "2026-05-04",
                        ),
                        routineGlance = uk.co.reiad.library.data.RoutineGlance(
                            date = java.time.LocalDate.now().toString(),
                            marked = 5,
                            of = 8,
                        ),
                    )
                }
            }
        }
    }

    /** The front page WHILE SOMETHING IS BEING READ.

        The transport is not one of the arrangeable widgets and
        never enters the board, so no other snapshot can hold it:
        it arrives when the voice starts and goes when it stops.
        A reader who walked away from a lesson holds it from
        here, which is the whole reason it exists. */
    @Test fun homeReading() {
        pz.snapshot {
            ReiadTheme(accent = Accents.GREEN, dark = false) {
                val c = LocalReiad.current
                Box(Modifier.fillMaxSize().background(c.paper)) {
                    Home(
                        site = site,
                        stale = false,
                        note = null,
                        ticks = emptyMap(),
                        audience = null,
                        onOpen = {},
                        board = listOf("continue:wide", "progress:small", "stock:small"),
                        speaking = uk.co.reiad.library.read.Speaking(
                            on = true, at = 11, block = 3, total = 40,
                        ),
                        readingTitle = "বিও অ্যাকাউন্ট খোলা: ধাপে ধাপে",
                    )
                }
            }
        }
    }

    /** The board LOOSE: the jiggle dress. Not the strips it
        replaced: the widget itself is the handle, two glass
        badges ride the corner, and move-up/-down live in the
        frame's accessibility actions rather than as drawn
        chrome. Mounted through `WidgetFrame` directly because
        `arranging` is the page's own state; frame time is fixed
        by Paparazzi so the lean draws at its first frame. */
    @Test fun homeArranging() {
        pz.snapshot {
            ReiadTheme(accent = Accents.GREEN, dark = false) {
                val c = LocalReiad.current
                Box(Modifier.fillMaxSize().background(c.paper)) {
                    Column(Modifier.fillMaxSize().padding(uk.co.reiad.library.ui.Gap.s8)) {
                        val kinds = site.widgets?.kinds.orEmpty().associateBy { it.id }
                        for (spec in listOf("continue:wide", "progress:small")) {
                            val placed = uk.co.reiad.library.core.parsePlaced(
                                spec,
                                uk.co.reiad.library.ui.DRAWABLE,
                            )!!
                            uk.co.reiad.library.ui.WidgetFrame(
                                kind = uk.co.reiad.library.core.kindOf(placed.id, kinds, placed.size),
                                placed = placed,
                                arranging = true,
                                first = spec.startsWith("continue"),
                                last = !spec.startsWith("continue"),
                                lang = "bn",
                                onUp = {}, onDown = {}, onResize = {}, onRemove = {},
                            ) {
                                uk.co.reiad.library.ui.Widget(
                                    placed.id,
                                    placed.size,
                                    uk.co.reiad.library.ui.BoardData(
                                        site = site,
                                        ticks = mapOf("money" to setOf("a", "b", "c")),
                                        bookmarks = emptyMap(),
                                        pieces = emptyList(),
                                        sway = uk.co.reiad.library.ui.Sway(),
                                        icons = emptyMap(),
                                        lang = "bn",
                                    ),
                                    uk.co.reiad.library.ui.BoardActions(
                                        onSchool = {}, onItem = {}, onPiece = {},
                                        onResume = { _, _ -> }, onStory = {},
                                    ),
                                )
                            }
                            Spacer(Modifier.height(uk.co.reiad.library.ui.Gap.s7))
                        }
                    }
                }
            }
        }
    }
}
