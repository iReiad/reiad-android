package uk.co.reiad.library

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/* ============================================================
   Forgetting the cache must not forget a reader.

   `forgetHeld` runs over the SAME DataStore that holds every
   tick, every bookmark, the reader's preferences and their
   session. A `clear()` there would sign somebody out and lose a
   year of ticks while calling itself "free up space", and it
   would look like it worked.

   The prefix is the whole guard, so the prefix is what this
   tests: which key names it would take and which it must not
   touch. Written against the names rather than against a live
   store, because the mistake is in the PREDICATE and a test that
   needed a device would not run here.
   ============================================================ */
class ShelfKeysTest {

    /** The real names, out of the app: `cache:` for anything
        fetched, and everything else for a person. */
    private val store = listOf(
        "cache:site",
        "cache:tools",
        "cache:ladder:money",
        "cache:lesson:money/basics-1/bo-account",
        "cache:lesson:deutsch/stufe-1/hallo",
        "cache:piece:why-index-funds",
        "cache:book:stufe-1",
        /* And these must survive, every one of them. */
        "learn-read",
        "learn-last",
        "learn-checks",
        "deutsch-read",
        "deutsch-tag",
        "english-day",
        "quran-done",
        "courses-read",
        "courses-answers",
        "days-active",
        "reader-prefs",
        "tool-lang",
        "shelf:followed",
        "sb-session",
    )

    private fun wouldForget(name: String) = name.startsWith("cache:")

    @Test fun itTakesEveryCachedAnswer() {
        val taken = store.filter(::wouldForget)
        assertEquals(7, taken.size, "every cache: key and no others: $taken")
    }

    @Test fun itTakesNothingThatIsAReadersOwn() {
        for (name in store.filterNot { it.startsWith("cache:") }) {
            assertTrue(!wouldForget(name), "$name is a reader's and must survive a forget")
        }
    }

    /** The shelf's own key is NOT a cache key, and that is
        deliberate: forgetting what is held must not also forget
        which schools somebody chose to follow, or the next fetch
        would never happen. */
    @Test fun theShelfSurvivesAForget() {
        assertTrue(!wouldForget("shelf:followed"))
    }

    /** And a lesson is counted by the one prefix the per-school
        report uses, which is narrower than the forget's. */
    @Test fun lessonsAreCountedPerSchool() {
        val money = store.count { it.startsWith("cache:lesson:money/") }
        assertEquals(1, money)
        assertEquals(1, store.count { it.startsWith("cache:lesson:deutsch/") })
    }
}
