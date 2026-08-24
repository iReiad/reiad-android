package uk.co.reiad.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.serialization.builtins.ListSerializer
import org.junit.Rule
import org.junit.Test
import uk.co.reiad.library.core.Accents
import uk.co.reiad.library.core.routine.Entry
import uk.co.reiad.library.core.routine.RoutineShape
import uk.co.reiad.library.core.routine.consistency
import uk.co.reiad.library.core.routine.echo
import uk.co.reiad.library.core.routine.everMarked
import uk.co.reiad.library.core.routine.flock
import uk.co.reiad.library.core.routine.garden
import uk.co.reiad.library.core.routine.gardenFrom
import uk.co.reiad.library.core.routine.heat
import uk.co.reiad.library.core.routine.momentum
import uk.co.reiad.library.core.routine.neverMarked
import uk.co.reiad.library.core.routine.runs
import uk.co.reiad.library.core.routine.seasonOf
import uk.co.reiad.library.ui.Gap
import uk.co.reiad.library.ui.LocalReiad
import uk.co.reiad.library.ui.ReiadTheme
import uk.co.reiad.library.ui.RoutineScreen
import uk.co.reiad.library.ui.RoutineState

/* ============================================================
   The routine LIVED IN, which is the longest page in the app.

   `ScreensLookTest.routine` draws the tool on its first morning:
   a shape, an empty day, and nothing else, because that is what a
   new reader meets. But every panel past the bands only exists
   once there is a history (the jar, the year, the week's shape,
   the balance, the moods' ribbon), so that picture could not show
   any of them, and a card nobody can see is a card that rots.

   This one draws the fixture's own twenty entries, built through
   the SAME arithmetic `readRoutine` uses, at five screens tall so
   the whole column is on the picture.
   ============================================================ */
class RoutineLookTest {
    /** Five screens: three (the account's TALL) still cut the
        column at the consistency panel. The width is a phone's. */
    @get:Rule val pz = paparazzi(HANDSET.copy(screenHeight = 915 * 3 * 5))

    @Test fun routineLived() {
        val shape = fixtureField("routine.json", "shape", RoutineShape.serializer())
        val entries = fixtureField(
            "routine.json", "entries", ListSerializer(Entry.serializer()),
        )
        val today = fixtureField(
            "routine.json", "today", kotlinx.serialization.serializer<String>(),
        )
        pz.snapshot {
            androidx.compose.runtime.CompositionLocalProvider(
                uk.co.reiad.library.ui.LocalStill provides true,
            ) {
                ReiadTheme(accent = Accents.GREEN, dark = false) {
                    val c = LocalReiad.current
                    Box(Modifier.fillMaxSize().background(c.paper)) {
                        RoutineScreen(
                            state = RoutineState(
                                loading = false,
                                routineId = "r",
                                today = today,
                                greeting = "সুপ্রভাত",
                                shape = shape,
                                entry = entries.firstOrNull { it.date == today },
                                entries = entries,
                                heat = heat(shape, entries, today, 12),
                                consistency = consistency(shape, entries, today, 28),
                                neverMarked = neverMarked(shape, entries),
                                momentum = momentum(shape, entries, today, 28),
                                runs = runs(entries, today, 365),
                                echo = echo(entries, today),
                                season = seasonOf(today),
                                flock = flock(everMarked(entries, "brd")),
                                garden = garden(everMarked(entries, "pln"), gardenFrom(null)),
                            ),
                            onMark = { _, _ -> }, onMood = {}, onNote = {}, onOpenSite = {},
                            contentPadding = PaddingValues(
                                start = Gap.s8, end = Gap.s8, top = 84.dp, bottom = 96.dp,
                            ),
                        )
                    }
                }
            }
        }
    }
}
