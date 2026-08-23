package uk.co.reiad.library

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import uk.co.reiad.library.core.Prefs

/* ============================================================
   Every preference this app STORES is one it also USES, and one
   a reader can change here.

   ---- the bug this is written against ----

   `text` and `measure` had been in `Prefs` since the app was
   written. They were serialised, they synced with the account
   under `reader-prefs`, `SyncTest` asserted the key by name, and
   NOTHING IN THE APP READ EITHER OF THEM. A reader who chose
   Comfortable type on their laptop got normal type on their
   phone, could not change it here at all, and every check
   passed: the field round-tripped perfectly and did nothing.

   That shape has no symptom. A stored-and-ignored preference is
   invisible in a screenshot, invisible in a sync test, and
   invisible to the reader except as the vague sense that the app
   is not listening.

   So this asks the two questions a field cannot answer about
   itself: is it READ anywhere outside the record, and is it
   OFFERED in the sheet where a reader would look for it.
   ============================================================ */
class PrefsReachTest {

    private fun sources(): List<File> =
        File("src/main/kotlin").walkTopDown().filter { it.extension == "kt" }.toList() +
            File("../core/src/main/kotlin").walkTopDown().filter { it.extension == "kt" }

    /** The record's own file is not evidence: a field is declared
        there and that is what makes it a field. Everywhere else
        is. */
    private fun elsewhere(): String = sources()
        .filter { it.name != "Prefs.kt" }
        .joinToString("\n") { it.readText() }

    private val settings: String =
        File("src/main/kotlin/uk/co/reiad/library/ui/Settings.kt").readText()

    /** Every property of `Prefs` except the plumbing. `ts` is the
        record's own timestamp and `theme` is written to its own
        key beside this one, both of which are said in that
        file. */
    private val plumbing = setOf(
        /* The record's own timestamp: `reader-prefs` is the one
           synced key whose rule is MARK, and a mark reconciles on
           the `ts` inside its value. Said at length in
           `Prefs.kt`. */
        "ts",
        /* Kotlin's, not this app's: the serializer plugin adds a
           `Companion` and `$stable` to every data class. */
        "Companion",
    )

    private fun stored(): List<String> =
        Prefs::class.java.declaredFields
            .map { it.name }
            .filter { it !in plumbing && !it.contains("$") }
            .also {
                check(it.size >= 6) { "only ${it.size} preferences found; the reflection is wrong" }
            }

    @Test fun everyStoredPreferenceIsReadSomewhere() {
        val text = elsewhere()
        val dead = stored().filter { name ->
            /* `prefs.text`, `it.copy(text = ...)`, `.text` on a
               `Prefs`: any of them counts. What does not count is
               only ever appearing in the record. */
            !Regex("""\b(prefs|it|p)\.$name\b""").containsMatchIn(text) &&
                !Regex("""\b$name\s*=""").containsMatchIn(text)
        }
        assertTrue(
            dead.isEmpty(),
            "these are stored and synced and nothing outside the record touches them: " +
                dead.joinToString(", ") +
                ". A preference that does nothing is worse than one that is missing: " +
                "the reader sets it, the sync test passes, and the app ignores it.",
        )
    }

    @Test fun everyReadingPreferenceIsOfferedInTheSheet() {
        /* The four the site's own preferences panel offers, by
           the name the column carries. `glass`, `blur` and `veil`
           are the app's three and are already there. */
        val offered = listOf("text", "measure", "theme", "glass", "blur", "veil")
        val missing = offered.filter { name ->
            !Regex("""copy\($name\s*=""").containsMatchIn(settings)
        }
        assertTrue(
            missing.isEmpty(),
            "the settings sheet does not let a reader change: " + missing.joinToString(", ") +
                ". `lang` is deliberately not in this list: it is the calculators' own " +
                "language and is set on the calculators.",
        )
    }

    /** And the type size actually reaches the typography rather
        than one style.

        Scaling the body alone would make Comfortable a page of
        normal headings over larger paragraphs, which is not
        larger type, it is a different design. */
    @Test fun theTypeScaleReachesTheWholeRamp() {
        val theme = File("src/main/kotlin/uk/co/reiad/library/ui/Theme.kt").readText()
        for (style in listOf(
            "displayLarge", "headlineLarge", "headlineSmall",
            "titleMedium", "bodyLarge", "bodySmall", "labelSmall",
        )) {
            assertTrue(
                Regex("""$style\s*=\s*$style\.up\(\)""").containsMatchIn(theme),
                "$style is not scaled, so one line of the app stays at the old size",
            )
        }
    }
}
