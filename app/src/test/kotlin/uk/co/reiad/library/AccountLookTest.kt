package uk.co.reiad.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
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
import uk.co.reiad.library.ui.BodyView
import uk.co.reiad.library.ui.LessonHead
import uk.co.reiad.library.ui.Path
import uk.co.reiad.library.ui.Paths
import uk.co.reiad.library.ui.PageHead
import uk.co.reiad.library.ui.Problem
import uk.co.reiad.library.ui.ReiadTheme
import uk.co.reiad.library.ui.Shell
import uk.co.reiad.library.ui.SetupState
import uk.co.reiad.library.ui.ShellState
import uk.co.reiad.library.ui.Skeleton
import uk.co.reiad.library.ui.StockScreen
import uk.co.reiad.library.ui.StockState
import uk.co.reiad.library.ui.DietScreen
import uk.co.reiad.library.ui.DietState
import uk.co.reiad.library.core.diet.Ancestry
import uk.co.reiad.library.core.diet.Body
import uk.co.reiad.library.core.diet.DietDay
import uk.co.reiad.library.core.diet.DietEntry
import uk.co.reiad.library.core.diet.DietProfile
import uk.co.reiad.library.core.diet.FloorHit
import uk.co.reiad.library.core.diet.Sex
import uk.co.reiad.library.core.diet.Target
import uk.co.reiad.library.ui.GroupScreen
import uk.co.reiad.library.ui.LiveScreen
import uk.co.reiad.library.ui.LiveState
import uk.co.reiad.library.ui.SearchScreen
import uk.co.reiad.library.ui.SettingsSheet
import uk.co.reiad.library.ui.WorkbookScreen
import uk.co.reiad.library.core.BookResponse
import uk.co.reiad.library.core.School
import uk.co.reiad.library.core.Prefs
import uk.co.reiad.library.ui.KeepSchool
import uk.co.reiad.library.ui.HeldPanel
import uk.co.reiad.library.data.Held
import uk.co.reiad.library.ui.RoutineScreen
import uk.co.reiad.library.ui.RoutineState
import uk.co.reiad.library.core.routine.RoutineShape
import uk.co.reiad.library.core.routine.SEASONS
import uk.co.reiad.library.ui.AccountScreen
import uk.co.reiad.library.core.LessonResponse
import uk.co.reiad.library.core.Lesson
import uk.co.reiad.library.core.Stage
import uk.co.reiad.library.core.LadderResponse
import uk.co.reiad.library.core.rungsOf
import uk.co.reiad.library.core.standingOf
import uk.co.reiad.library.core.BodyParser
import uk.co.reiad.library.core.Reader
import uk.co.reiad.library.core.Scenario

/* ============================================================
   The account, THREE SCREENS TALL.

   Paparazzi renders one frame, so a screen longer than a handset
   is a screen whose picture only ever shows its head. The
   account is nine sections and a form, and snapshotting it at
   915dp was a picture of the sign-in panel over and over while
   everything below it could rot quietly.

   The WIDTH is unchanged, which is the whole point: every line
   breaks where it breaks on a real phone. Only the frame is
   longer.
   ============================================================ */
class AccountLookTest {

    @get:Rule val pz = paparazzi(TALL)

    private val site: SiteManifest = fixture("site.json", SiteManifest.serializer())

    private fun tall(body: @androidx.compose.runtime.Composable () -> Unit) {
        pz.snapshot {
            ReiadTheme(accent = Accents.GREEN, dark = false) {
                val c = LocalReiad.current
                Box(Modifier.fillMaxSize().background(c.paper)) { body() }
            }
        }
    }

    /** Signed in, with the three questions unanswered, which is
        what a new reader meets. The vocabularies come out of the
        fixture rather than being typed here, for the reason the
        screen takes them as a parameter: they are a CHECK
        constraint and there is one list. */
    @Test fun accountSetup() = tall {
        AccountScreen(
            reader = Reader(id = "r1", email = "you@example.com", name = "Rony Reiad"),
            kept = emptyList(), targets = emptyList(),
            daysActive = setOf("2026-08-21", "2026-08-22", "2026-08-23"),
            ticksOf = { 0 },
            onOpenKept = {}, onRemoveTarget = {}, onExport = {},
            exported = null, onErase = {}, erasing = null,
            problem = null, linkSent = false, bottomPadding = 96.dp,
            onGoogle = {}, onLink = {}, onSignOut = {},
            setup = SetupState(name = "Rony Reiad", following = setOf("money")),
            schools = site.ladders,
            paces = site.profile.paces,
            targetKinds = site.profile.targetKinds,
            started = setOf("money"),
            /* One school's real ladder, with a real reading
               position in it. The bar's denominator is what the
               rows say, not a number typed here. */
            paths = run {
                val money = site.ladders.first { it.key == "money" }
                val rungs = rungsOf(
                    fixture("money.json", LadderResponse.serializer()).stages,
                )
                listOf(
                    Path(
                        school = money,
                        at = standingOf(
                            ladder = rungs,
                            read = rungs.take(5).map { it.id }.toSet(),
                            last = rungs[4].id,
                            checks = setOf("${rungs[1].id}#0", "${rungs[1].id}#1"),
                        ),
                    ),
                )
            },
        )
    }

    /** Saved checks, on their own, for the reason the paths
        section is: the account is taller than the frame. */
    @Test fun accountSaved() = tall {
        Column(Modifier.padding(Gap.s8)) {
            AccountScreen(
                reader = Reader(id = "r1", email = "you@example.com", name = "Rony Reiad"),
                kept = emptyList(), targets = emptyList(),
                daysActive = emptySet(), ticksOf = { 0 },
                onOpenKept = {}, onRemoveTarget = {}, onExport = {},
                exported = null, onErase = {}, erasing = null,
                problem = null, linkSent = false, bottomPadding = 0.dp,
                onGoogle = {}, onLink = {}, onSignOut = {},
                setup = SetupState(asked = true, name = "Rony Reiad"),
                scenarios = listOf(
                    Scenario(
                        id = "1", name = "Beximco, August",
                        summary = "68.0 \u00b7 Worth accumulating",
                    ),
                    Scenario(
                        id = "2", name = "Square, half year",
                        summary = "41.2 \u00b7 Trim",
                    ),
                ),
            )
        }
    }

    /** "Where you are", on its own.

        A screen this long is taller than the frame, so the
        account snapshot only ever shows its head. A section
        nobody can see in a picture is a section that can rot
        quietly, which is what the first version of `ReachTest`
        was written about. */
    @Test fun accountPaths() = tall {
        val rungs = rungsOf(fixture("money.json", LadderResponse.serializer()).stages)
        val deutsch = fixture("deutsch.json", LadderResponse.serializer())
        Column(Modifier.padding(Gap.s8)) {
            Paths(
                listOf(
                    Path(
                        school = site.ladders.first { it.key == "money" },
                        at = standingOf(
                            ladder = rungs,
                            read = rungs.take(7).map { it.id }.toSet(),
                            last = rungs[6].id,
                            checks = setOf("${rungs[1].id}#0", "${rungs[1].id}#1"),
                        ),
                    ),
                    /* And one nobody has opened, which is the
                       state most schools are in for most readers
                       and the one that has to read as an
                       invitation rather than as a failure. */
                    Path(
                        school = site.ladders.first { it.key == "deutsch" },
                        at = standingOf(rungsOf(deutsch.stages), emptySet()),
                    ),
                ),
                onOpen = {},
            )
        }
    }
}
