package uk.co.reiad.library.core

import kotlin.test.Test
import kotlin.test.assertTrue

/* ============================================================
   A settings label has to fit in its own thumb.

   The four preference tables ride in segmented controls three
   across a handset, which at the app's label size is about eleven
   characters. "Follow my system" is sixteen: it wrapped to two
   lines and drew OUTSIDE the pill it belongs to, over the option
   beside it.

   That is not a thing a screenshot check catches either, because
   it renders as a plausible-looking overlap rather than as an
   error. It is a length, so it is checked as one, and the note
   beside each option is where a sentence goes.
   ============================================================ */
class PrefLabelsTest {

    private val tables = mapOf(
        "THEMES" to THEMES.map { it.label },
        "GLASSES" to GLASSES.map { it.label },
        "BLURS" to BLURS.map { it.label },
        "VEILS" to VEILS.map { it.label },
    )

    /** Eleven, measured off the widest that fits: `Always dark`
        is exactly eleven and sat inside its thumb; `Follow my
        system` at sixteen did not. */
    private val fits = 11

    @Test fun everyLabelFitsItsThumb() {
        for ((name, labels) in tables) {
            for (label in labels) {
                assertTrue(
                    label.length <= fits,
                    "$name has \"$label\" at ${label.length} characters, " +
                        "which does not fit a third of a handset. Move the words " +
                        "into the option's note.",
                )
            }
        }
    }

    /** And every table is three wide or fewer, because that is
        what the control draws. A fourth option would not overflow
        a thumb, it would make all of them too narrow at once. */
    @Test fun everyTableIsThreeAcrossAtMost() {
        for ((name, labels) in tables) {
            assertTrue(labels.size <= 3, "$name has ${labels.size} options across one row")
        }
    }
}
