package uk.co.reiad.library.ui

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/* ============================================================
   What a press feels like, not only what it looks like.

   The material answers a finger with light; this is the same
   answer through the vibrator, and the vocabulary is deliberately
   small. A STATE CHANGE hums (a tick made, a latch thrown, a
   segment chosen) and plain navigation does not, because a
   reading app that buzzes on every row is a reading app somebody
   turns the vibrator off for, and then the ticks lose their voice
   too.

   Everything goes through `View.performHapticFeedback`, which is
   the polite path: it respects the system's own haptic feedback
   setting, costs no permission, and needs no amplitude arithmetic
   of ours. The richer constants arrived over the years, so each
   verb asks the version and falls back to the nearest older
   sound rather than to silence.
   ============================================================ */

/** The four verbs a surface can say through the vibrator. */
@Stable
class Touch internal constructor(private val view: View) {

    /** A control acted: a button did its thing. */
    fun tap() {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    /** A position moved among positions: a segment chosen, a tab
        switched, a step of a walker taken. Lighter than a tap. */
    fun tick() {
        view.performHapticFeedback(
            if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.SEGMENT_TICK
            else HapticFeedbackConstants.CLOCK_TICK,
        )
    }

    /** Something latched on or off: a checkpoint, a save, a
        follow. `on` matters because Android gives the two
        directions different sounds from API 34. */
    fun latch(on: Boolean) {
        view.performHapticFeedback(
            when {
                Build.VERSION.SDK_INT >= 34 && on -> HapticFeedbackConstants.TOGGLE_ON
                Build.VERSION.SDK_INT >= 34 -> HapticFeedbackConstants.TOGGLE_OFF
                Build.VERSION.SDK_INT >= 30 -> HapticFeedbackConstants.CONFIRM
                else -> HapticFeedbackConstants.KEYBOARD_TAP
            },
        )
    }

    /** Something finished: a lesson ticked read, a day done. The
        firmest of the four. */
    fun confirm() {
        view.performHapticFeedback(
            if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM
            else HapticFeedbackConstants.KEYBOARD_TAP,
        )
    }
}

@Composable
fun rememberTouch(): Touch {
    val view = LocalView.current
    return remember(view) { Touch(view) }
}
