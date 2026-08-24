package uk.co.reiad.library

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/* ============================================================
   The one character this project bans, in this repository too.

   `CLAUDE.md` opens by banning U+2014 everywhere on the site and
   `scripts/check-dashes.ts` holds it there. Nothing held it HERE,
   and the app is the same project: the copy a reader sees on a
   phone is the same prose, and a rule enforced in one of two
   repositories is a rule that is about to be broken in the other.

   It already had been. `DietBody.kt` printed a bare dash where a
   waist had not been measured, which is the shape the site's rule
   is actually about: the mark was doing the work of a sentence,
   and the sentence ("not known") is both shorter to read and the
   true thing.

   ---- and the check owns its own exception ----

   `SpeechTest` is allowed one because the character IS its
   subject: it asserts the reader steps over a paragraph that is
   nothing but a dash. An exemption is keyed by the file with the
   reason, and a STALE one fails, for the reason every list of
   exceptions in this project is checked both ways.

   The character is not written out anywhere in this file, for the
   reason the site's own check is not: a rule that contains the
   character it bans always matches itself.
   ============================================================ */
class DashesTest {

    private val banned = '—'

    /** Keyed by path, with the reason. */
    private val allowed = mapOf(
        "core/src/test/kotlin/uk/co/reiad/library/core/SpeechTest.kt" to
            "the character is its subject: it asserts that read-aloud steps over a " +
                "paragraph which is nothing but this mark.",
        "app/src/test/kotlin/uk/co/reiad/library/DashesTest.kt" to
            "this file, which cannot name what it bans without matching itself. It " +
                "does not, and this line is here so that stays deliberate.",
    )

    private val root = File("..").canonicalFile

    private fun sources(): List<File> = root.walkTopDown()
        .onEnter { dir ->
            dir.name !in setOf("build", ".git", ".gradle", ".idea", "snapshots")
        }
        .filter { it.isFile }
        .filter { it.extension in setOf("kt", "kts", "md", "xml", "json", "yml", "yaml", "pro") }
        .toList()

    @Test fun `nothing in this repository carries the mark`() {
        val offenders = sources()
            .filter { it.readText().contains(banned) }
            .map { it.relativeTo(root).path.replace(File.separatorChar, '/') }
            .filter { it !in allowed }

        assertTrue(
            offenders.isEmpty(),
            "CLAUDE.md opens by banning U+2014 and these carry it: $offenders. A " +
                "sentence that needs one is usually two ideas that have not been " +
                "separated: a colon, a full stop, a pair of commas or brackets. An " +
                "en dash in a number range is fine and is not this.",
        )
    }

    @Test fun `every exemption still names a file that carries one`() {
        for ((path, why) in allowed) {
            val file = File(root, path)
            assertTrue(file.isFile, "the exemption for $path ($why) names no file")
            assertTrue(
                file.readText().contains(banned),
                "$path is exempt ($why) and no longer carries the mark, so the " +
                    "reason is about nothing. Take the line out.",
            )
        }
    }
}
