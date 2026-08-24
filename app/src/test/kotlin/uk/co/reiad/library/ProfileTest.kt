package uk.co.reiad.library

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import uk.co.reiad.library.core.Profile
import uk.co.reiad.library.core.ProgressKeys
import uk.co.reiad.library.core.School
import uk.co.reiad.library.ui.SetupState
import uk.co.reiad.library.ui.TargetDraft
import uk.co.reiad.library.ui.ready
import uk.co.reiad.library.ui.seeded

/* ============================================================
   The profile, and the one filter that is load bearing.

   Every other table this app reads is `auth.uid() = user_id`, so
   a read with no filter returns your own rows and nothing else.
   `profiles` is the exception: its select policy is `using
   (true)`, deliberately, because a comment has to show its
   author's name to somebody signed out.

   The site learned it the hard way. `getProfile()` asked for
   `profiles?select=...&limit=1`, PostgREST answered with
   whichever row the planner reached first out of the WHOLE
   table, and because a non-HOT update moves a row to the end of
   the heap, SAVING your profile was what made the next read
   return somebody else's. The account page drew a stranger's
   name, `setup_at` came back null so the setup form reappeared,
   and pressing Save again wrote the right row and guaranteed the
   same wrong read.

   With one account it is invisible. This app is being tested
   with one account.

   So this reads the SOURCE. A fixture is kinder than Postgres,
   which is the exact failure `next/account.test.ts` was caught
   by: its fake answered every GET on `profiles` with the
   reader's own row, so 117 checks passed against a page drawing
   the wrong person.
   ============================================================ */
class ProfileTest {

    private val library: String =
        File("src/main/kotlin/uk/co/reiad/library/account/Library.kt").readText()

    /** Comments name the thing they are about, so the assertions
        below would pass on the prose alone. */
    private val code: String = library
        .replace(Regex("""/\*[\s\S]*?\*/"""), "")
        .replace(Regex("""(?m)//.*$"""), "")

    private fun request(after: String): String {
        val at = code.indexOf(after)
        assertTrue(at >= 0, "$after is gone from Library.kt")
        val open = code.indexOf('"', at)
        return code.substring(open, code.indexOf('\n', code.indexOf(") {", open)))
    }

    @Test fun theProfileReadNamesTheReader() {
        val read = request("suspend fun profile()")
        assertTrue(
            "id=eq." in read,
            "the profile read has no id filter, so PostgREST answers with whichever " +
                "row the planner reaches first out of a table readable by anyone. " +
                "It is right with one account and worse than a coin toss with two:\n  " +
                read.trim(),
        )
        assertTrue(
            "/profiles" in read,
            "this is meant to be the profiles read and does not name the table",
        )
    }

    @Test fun theProfileWriteNamesTheReaderToo() {
        val at = code.indexOf("suspend fun saveProfile(")
        assertTrue(at >= 0, "saveProfile is gone from Library.kt")
        val body = code.substring(at, code.indexOf("suspend fun ", at + 10))
        assertTrue(
            "profiles?id=eq." in body,
            "the profile PATCH has no id filter. The update policy already makes it " +
                "impossible to touch anyone else's row, so this is a second lock on a " +
                "door that never opens, and it stays because the READ is the one with " +
                "no second lock.",
        )
    }

    /** And the answer is read, on EVERY write. A 400 reported as
        success is the failure the site had for two days, when
        `following` named a school the CHECK constraint had not
        heard of, and it is the failure that lost a day of routine
        marks in this app: a write whose status nobody reads.

        The status is read in ONE place now, the `write` helper,
        so this asserts the delegation and the helper: every
        Boolean-shaped escape from it would show up as an http
        verb outside it. */
    @Test fun aRefusedSaveIsNotReportedAsSuccess() {
        val at = code.indexOf("private suspend fun write(")
        assertTrue(at >= 0, "the write helper is gone from Library.kt")
        val helper = code.substring(at, code.indexOf("suspend fun ", at + 30))
        assertTrue(
            "isSuccess()" in helper,
            "the write helper does not read the answer, so a 400 on any write in " +
                "this file reports as saved",
        )
        assertTrue(
            ": Boolean = withToken" !in code,
            "a write in Library.kt answers with a Boolean again. The routine lost a " +
                "day of marks to a Boolean nobody read: answer with a sentence or " +
                "null through the write helper, and make the screen show the sentence.",
        )
        for (fn in listOf("saveProfile", "keep", "saveScenario", "addTarget", "removeTarget")) {
            val here = code.indexOf("suspend fun $fn(")
            assertTrue(here >= 0, "$fn is gone from Library.kt")
            val body = code.substring(here, code.indexOf("suspend fun ", here + 10))
            assertTrue(
                "write(" in body,
                "$fn does not go through the write helper, so its status is read " +
                    "nowhere and a refused save reports as success",
            )
        }
    }

    /* ---------- the id that is not the key ---------- */

    /** `following` holds SCHOOL IDS and the money school's ticks
        are filed under `learn-read`.

        The CHECK constraint allows `money`, so sending the key
        would be a 400 on the whole patch rather than one ignored
        field, and the money school is the one everybody has
        started. */
    @Test fun aStartedSchoolIsNamedByItsIdNotItsKey() {
        val ticks = mapOf(ProgressKeys.read(School.MONEY) to setOf("basics-1/one"))
        assertEquals(setOf("money"), startedIn(ticks))
        assertTrue(
            "learn" !in startedIn(ticks),
            "the storage key reached `following`, which the constraint refuses",
        )
    }

    @Test fun anUntouchedSchoolIsNotStarted() {
        assertEquals(emptySet(), startedIn(emptyMap()))
        assertEquals(
            emptySet(),
            startedIn(mapOf(ProgressKeys.read(School.DEUTSCH) to emptySet())),
            "an empty set is not a started school",
        )
    }

    /* ---------- the form, and what it must not take away ---------- */

    /** Seeding never writes over anything already typed.

        A reader who starts filling this in while the profile is
        still in flight must not have it taken away underneath
        them, which is the whole reason `seeded()` is a
        null-or-blank test on every field rather than an
        assignment. */
    @Test fun seedingNeverOverwritesWhatWasTyped() {
        val typed = SetupState(name = "Rony", pace = "daily", following = setOf("deutsch"))
        val seeded = typed.seeded(
            profile = Profile(
                displayName = "Somebody Else",
                following = listOf("money"),
                pace = "sometimes",
            ),
            fallbackName = "Fallback",
            started = setOf("quran"),
        )
        assertEquals("Rony", seeded.name)
        assertEquals("daily", seeded.pace)
        assertEquals(setOf("deutsch"), seeded.following)
    }

    @Test fun anEmptyFormTakesTheAccountsAnswers() {
        val seeded = SetupState().seeded(
            profile = Profile(displayName = "Rony", following = listOf("money"), pace = "often"),
            fallbackName = "Fallback",
            started = setOf("quran"),
        )
        assertEquals("Rony", seeded.name)
        assertEquals("often", seeded.pace)
        assertEquals(
            setOf("money", "quran"),
            seeded.following,
            "a school already started should arrive pre-ticked",
        )
    }

    /** `setup_at` decides which of the two framings the form is
        in, and it is the field that has to survive the seed. */
    @Test fun aProfileWithNoSetupAtIsStillBeingAsked() {
        val fresh = SetupState().seeded(Profile(), "", emptySet())
        assertTrue(!fresh.asked, "a profile with no setup_at is a reader still being asked")
        val done = SetupState().seeded(Profile(setupAt = "2026-08-23T00:00:00Z"), "", emptySet())
        assertTrue(done.asked, "setup_at means answered, so the form stops asking")
    }

    /* ---------- the target form ---------- */

    /** Postgres constrains `label` to 1..80 and `target` to >= 0,
        and a course names a school. Checked before the button
        does anything, because a 400 arriving afterwards is a form
        that looked finished. */
    @Test fun aTargetIsCheckedBeforeItIsSent() {
        assertTrue(!TargetDraft().ready(), "an empty draft is not a row")
        assertTrue(
            !TargetDraft(kind = "course", label = "x", target = "10").ready(),
            "a course with no school would never move",
        )
        assertTrue(
            !TargetDraft(kind = "habit", label = "x", target = "0").ready(),
            "a target of nought is a bar that is already full",
        )
        assertTrue(
            !TargetDraft(kind = "habit", label = "x".repeat(81), target = "5").ready(),
            "a label over 80 is a 400",
        )
        assertTrue(
            TargetDraft(kind = "habit", label = "Turn up", target = "5").ready(),
            "a habit with a label and a number is a row",
        )
        assertTrue(
            TargetDraft(kind = "course", subject = "money", label = "Finish it", target = "60")
                .ready(),
        )
    }
}
