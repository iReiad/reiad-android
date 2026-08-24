package uk.co.reiad.library.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/* ============================================================
   `remind-at` is not a synced key, and that is the assertion.

   Everything else this app stores travels to the account, which
   is the right default and is wrong for exactly one thing. A
   reminder is a fact about one HANDSET: the site cannot post
   one, a second phone should not inherit one, and a field the
   browser carries and can never act on is the "carried and never
   drawn" failure the manifest tests exist for, pointed the other
   way.

   The arithmetic of "when is the next nine in the evening" lives
   in `app/`, with the worker, and is asserted there.
   ============================================================ */
class RemindTimeTest {

    @Test fun `the reminder does not travel`() {
        assertTrue(
            REMIND_KEY !in SyncKeys.ALL,
            "$REMIND_KEY is in the sync table. A reminder set on a phone would arrive " +
                "on every other device the account touches, and the browser would carry " +
                "a field it can never act on.",
        )
    }

    /** And it is spelled the way it is spelled. The rule at the
        top of `ProgressKeys.kt` covers every key here: renaming
        one does not move a setting, it loses it. */
    @Test fun `the key is the one real phones hold`() {
        assertEquals("remind-at", REMIND_KEY)
    }
}
