package uk.co.reiad.library

import android.app.Application
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import java.io.File
import uk.co.reiad.library.ui.ButtonKind
import uk.co.reiad.library.ui.Gap
import uk.co.reiad.library.ui.LocalReiad
import uk.co.reiad.library.ui.Pane
import uk.co.reiad.library.ui.PillButton

/* ============================================================
   When the app dies, it has to say so ON THE PHONE.

   ---- the report this exists because of ----

   "build 404c6e8 doesn't open, just crashes." Three words of
   diagnosis were the whole of what the phone offered, because a
   crash at launch is the one failure the app cannot report
   through any screen it draws: there is no screen. The person
   holding the phone has no logcat, and the person with logcat
   has no phone.

   ---- what this does ----

   Two small things, both boring on purpose.

   Every uncaught exception is written to a file BEFORE the
   process dies: the build stamp, the moment, the whole stack.
   The write is the first thing in the handler and everything in
   it is try-caught, because a crash handler that crashes eats
   the evidence.

   And the process records that it died EARLY. A crash inside the
   first ten seconds increments a counter; an activity that
   survives past ten seconds resets it. Two early deaths in a row
   mean the normal screen cannot come up, so the activity opens
   the guard screen INSTEAD of the app: what happened, the stack,
   a copy button, and a way to try again. An app that cannot open
   becomes an app that opens and explains itself.

   SharedPreferences rather than DataStore, deliberately: this
   runs while the process is dying and before anything is
   composed, and the one storage that is synchronous, always
   loaded and dependency-free is the right one for both moments.
   ============================================================ */
class ReiadApp : Application() {

    override fun onCreate() {
        super.onCreate()
        val born = System.currentTimeMillis()
        val head = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                File(filesDir, CRASH_FILE).writeText(
                    buildString {
                        appendLine(BuildConfig.BUILT_FROM)
                        appendLine(java.util.Date().toString())
                        appendLine()
                        appendLine(android.util.Log.getStackTraceString(error))
                    },
                )
                if (System.currentTimeMillis() - born < EARLY_MS) {
                    val guard = getSharedPreferences(GUARD, Context.MODE_PRIVATE)
                    guard.edit()
                        .putInt(STRAIGHT, guard.getInt(STRAIGHT, 0) + 1)
                        .commit()
                }
            }
            head?.uncaughtException(thread, error)
        }
    }

    companion object {
        const val GUARD = "guard"
        const val STRAIGHT = "straight-crashes"
        const val CRASH_FILE = "last-crash.txt"
        const val EARLY_MS = 10_000L

        /** The recorded crash, or null. */
        fun lastCrash(context: Context): String? =
            File(context.filesDir, CRASH_FILE).takeIf { it.exists() }
                ?.readText()?.takeIf { it.isNotBlank() }

        /** Two early deaths in a row: the normal screen is what
            is crashing, so do not draw it. */
        fun troubled(context: Context): Boolean =
            context.getSharedPreferences(GUARD, Context.MODE_PRIVATE)
                .getInt(STRAIGHT, 0) >= 2 && lastCrash(context) != null

        /** The activity lived: whatever died before, the screen
            comes up now, so the next crash starts its own count. */
        fun settled(context: Context) {
            context.getSharedPreferences(GUARD, Context.MODE_PRIVATE)
                .edit().putInt(STRAIGHT, 0).apply()
        }

        fun forgetCrash(context: Context) {
            File(context.filesDir, CRASH_FILE).delete()
            settled(context)
        }
    }
}

/** The screen the app opens INSTEAD of itself after two straight
    launch deaths. Everything on it works with nothing loaded: no
    manifest, no account, no network. */
@Composable
fun CrashScreen(stack: String, onCopy: () -> Unit, onTryAgain: () -> Unit) {
    val c = LocalReiad.current
    Column(
        Modifier
            .fillMaxSize()
            .padding(Gap.s8)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Gap.s6),
    ) {
        Spacer(Modifier.height(Gap.s10))
        Text(
            "অ্যাপটা খুলতে গিয়ে ভেঙে পড়েছে",
            style = MaterialTheme.typography.headlineSmall,
            color = c.ink,
        )
        Text(
            "যা ঘটেছে তার পুরো বিবরণ নিচে আছে। কপি করে পাঠিয়ে দিলে " +
                "পরের বিল্ডে এটা সারানো যাবে। The full report is below: " +
                "copy it and send it on, and the next build can fix it.",
            style = MaterialTheme.typography.bodyMedium,
            color = c.inkSoft,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Gap.s5)) {
            PillButton("রিপোর্ট কপি করুন", onCopy, kind = ButtonKind.SOLID)
            PillButton("আবার চেষ্টা করুন", onTryAgain, kind = ButtonKind.GHOST)
        }
        Pane(Modifier.fillMaxWidth()) {
            Text(
                stack,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = c.inkSoft,
            )
        }
    }
}
