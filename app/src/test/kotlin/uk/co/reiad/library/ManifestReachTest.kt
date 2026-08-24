package uk.co.reiad.library

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import uk.co.reiad.library.core.SiteManifest

/* ============================================================
   Everything the site sends, something DRAWS.

   ---- the other end of a contract that was half guarded ----

   `ManifestSurfaceTest` in core already asks the first half: the
   endpoint sends a key, and `SiteManifest` had better have a
   field for it, or `ignoreUnknownKeys` drops it silently and the
   feature is missing where nobody can see it is missing.

   This is the same question one step further in. A field can
   exist, parse perfectly, round-trip in every test, and be read
   by NOTHING. The manifest arrives, the property is populated,
   and the screen that should show it shows something else
   instead. There is no symptom: no crash, no blank, no wrong
   pixel. It is `PrefsReachTest`'s disease with the arrow pointing
   at the network rather than at the store.

   It was not hypothetical either. `skills` was sent by the site
   and read by nothing: seven skills with their status and, on the
   one that is not live yet, a note saying where it has got to.
   The app drew its cards from the nav table instead, so the
   coming-soon card said "আসছে" and stopped, while the sentence
   answering the question that chip provokes sat in memory
   unread.

   ---- what counts as reading it ----

   Any mention outside the model's own file. That is a low bar on
   purpose: this is a smoke alarm, not an audit. A field that is
   genuinely used will trip it a dozen times over, and a field
   nobody has touched trips nothing at all.
   ============================================================ */
class ManifestReachTest {

    private fun sources(): List<File> =
        File("src/main/kotlin").walkTopDown().filter { it.extension == "kt" }.toList() +
            File("../core/src/main/kotlin").walkTopDown().filter { it.extension == "kt" }

    /** Everywhere except the file the manifest is declared in:
        a field is declared there, and that is what makes it a
        field rather than what makes it used. */
    private fun elsewhere(): String = sources()
        .filter { it.name != "Api.kt" }
        .joinToString("\n") { it.readText() }

    /** `ok` is the envelope rather than the cargo: every endpoint
        on this site carries one, and what the app does when a
        fetch fails is decided by the status code and by whether
        the body parsed at all. A screen that drew differently
        because `ok` was false would be a second, quieter error
        path beside the one that already exists. */
    private val envelope = setOf("ok")

    private fun fields(): List<String> =
        SiteManifest::class.java.declaredFields
            .map { it.name }
            .filter { it !in envelope && !it.contains("$") && it != "Companion" }
            .also {
                check(it.size >= 15) { "only ${it.size} manifest fields found; the reflection is wrong" }
            }

    @Test fun everyThingTheSiteSendsIsReadSomewhere() {
        val text = elsewhere()
        val unread = fields().filter { name ->
            /* `site.skills`, `it.skills`, `manifest?.skills`: any
               mention outside the model. What does not count is
               only ever appearing in the declaration. */
            !Regex("""\.$name\b""").containsMatchIn(text)
        }
        assertTrue(
            unread.isEmpty(),
            "the site sends these and nothing in the app reads them: " +
                unread.joinToString(", ") +
                ". A field that parses and is never read is a feature the site thinks it " +
                "is offering and the app is quietly not showing, and there is no symptom " +
                "to notice: no crash, no blank, no wrong pixel.",
        )
    }
}
