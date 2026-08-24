package uk.co.reiad.library

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import uk.co.reiad.library.data.store
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/* ============================================================
   The app OPENS. Asserted, because it shipped not doing so.

   "build 404c6e8 doesn't open, just crashes" arrived with no
   stack, no logcat and no way to get either, and five hundred
   tests were green: every one of them mounts a screen or calls
   a function, and not one of them launched the activity that is
   the app. The gap this closes is exactly that one.

   ---- what a launch test can and cannot promise ----

   Robolectric runs the real `onCreate`, the real `App()`
   composition, the real stores and the real model init on this
   JVM. What it does not run is the device's own renderer, so a
   crash that lives in RenderEffect or a vendor blit is out of
   its reach; `Guard.kt` is the half that answers those, by
   making the NEXT such crash carry its stack to the screen.

   ---- why the hostile-state launches matter more ----

   A fresh launch is the easy case. The launches that die in the
   world are launches over what a previous build STORED: a board
   in the old spelling, a cache from a schema two releases back,
   plain garbage where JSON should be. Each of those is a real
   file a real phone holds right now, so each is a test.
   ============================================================ */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xhdpi")
class LaunchTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun launches() {
        /* The crash this file exists for arrived a beat AFTER the
           first frame: a coroutine in the model's init resumed
           off a store read and met a field declared below the
           block, and the NPE surfaced as an "uncaught exceptions
           before the test" failure in whichever test ran NEXT.
           So a launch is not "it came up": it is "it came up and
           nothing it started died". The handler catches what the
           background threads throw; draining the main looper
           re-raises what they posted back. */
        val died = java.util.Collections.synchronizedList(mutableListOf<Throwable>())
        val before = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { _, e -> died.add(e) }
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    assertTrue(activity.window.decorView.isAttachedToWindow)
                }
                repeat(10) {
                    Thread.sleep(30)
                    org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
                }
            }
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(before)
        }
        assertTrue(
            died.isEmpty(),
            "the launch itself threw: " + died.firstOrNull()?.let {
                android.util.Log.getStackTraceString(it)
            },
        )
    }

    @Test fun aColdStartComesUp() = launches()

    @Test fun aLaunchOverAStoredBoardComesUp() {
        runBlocking {
            context.store.edit {
                /* The spellings REAL boards hold: the old pair,
                   the new three, an id no build has ever had, and
                   a duplicate, all at once. */
                it[stringPreferencesKey("home-board")] =
                    """{"board":["continue:full","progress:half","pulse:tall",""" +
                    """"progress:half","weather:wide","market:wide"],"ts":1}"""
            }
        }
        launches()
    }

    @Test fun aLaunchOverGarbageCachesComesUp() {
        runBlocking {
            context.store.edit {
                it[stringPreferencesKey("cache:site")] = "{not json"
                it[stringPreferencesKey("cache:news")] = "[]"
                it[stringPreferencesKey("cache:routine-today")] = """{"date":12}"""
                it[stringPreferencesKey("cache:diet-today")] = "null"
                it[stringPreferencesKey("home-board")] = "\"a string, not a record\""
                it[stringPreferencesKey("learn-read")] = "{}"
                it[stringPreferencesKey("learn-last")] = "not a url"
            }
        }
        launches()
    }

    @Test fun aLaunchOverYesterdaysGlancesComesUp() {
        runBlocking {
            context.store.edit {
                it[stringPreferencesKey("cache:routine-today")] =
                    """{"date":"2001-01-01","marked":3,"of":8}"""
                it[stringPreferencesKey("cache:diet-today")] =
                    """{"date":"2001-01-01","kcal":1810,"target":2200,"entries":4}"""
            }
        }
        launches()
    }

    /* ---------- the guard around the guard ---------- */

    @Test fun twoEarlyDeathsOpenTheReportInsteadOfTheApp() {
        java.io.File(context.filesDir, ReiadApp.CRASH_FILE)
            .writeText("stamp\nboom\n\njava.lang.RuntimeException: boom")
        context.getSharedPreferences(ReiadApp.GUARD, Context.MODE_PRIVATE)
            .edit().putInt(ReiadApp.STRAIGHT, 2).commit()

        assertTrue(ReiadApp.troubled(context))
        /* The activity still LAUNCHES: safe mode is a screen, not
           an exit. */
        launches()
    }

    @Test fun oneEarlyDeathStillOpensTheApp() {
        context.getSharedPreferences(ReiadApp.GUARD, Context.MODE_PRIVATE)
            .edit().putInt(ReiadApp.STRAIGHT, 1).commit()
        assertEquals(false, ReiadApp.troubled(context))
    }

    @Test fun tryAgainForgetsTheCrash() {
        java.io.File(context.filesDir, ReiadApp.CRASH_FILE).writeText("x")
        context.getSharedPreferences(ReiadApp.GUARD, Context.MODE_PRIVATE)
            .edit().putInt(ReiadApp.STRAIGHT, 5).commit()
        ReiadApp.forgetCrash(context)
        assertEquals(null, ReiadApp.lastCrash(context))
        assertEquals(false, ReiadApp.troubled(context))
    }
}
