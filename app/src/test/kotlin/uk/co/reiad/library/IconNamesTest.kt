package uk.co.reiad.library

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import uk.co.reiad.library.ui.SHAPES

/* ============================================================
   Every icon name in this app resolves to a drawing.

   The website has `check-icons.ts` for exactly this and the app
   had nothing. A name with no shape is not a crash: `Icon()`
   draws a dot, deliberately, so a missing icon looks missing
   rather than taking a screen down. That is the right runtime
   behaviour and it is also why nobody notices: a three pixel dot
   in a 20dp box reads as a bullet, on a screen that renders
   perfectly, in a snapshot nobody looks at twice.

   `"plus"` was one, added with the target form, and this file was
   written because of it.

   Read out of the SOURCE rather than by walking a rendered tree,
   because a name is a string literal at a call site and half of
   these call sites need a fixture, a theme and a phone before
   they are reached at all.
   ============================================================ */
class IconNamesTest {

    /** `Icon("name"` and `icon = "name"`, which are the two ways
        this app names one. A name computed rather than typed
        (`item.icon`) is not here and cannot be: it comes out of
        the manifest, and `ManifestSurfaceTest` is what covers
        the manifest. */
    private val calls = Regex("""(?:Icon\(\s*|icon\s*=\s*)"([a-z0-9-]+)"""")

    private fun sources(): List<File> =
        File("src/main/kotlin").walkTopDown().filter { it.extension == "kt" }.toList()

    @Test fun everyIconNameHasAShape() {
        val files = sources()
        assertTrue(files.size > 10, "found ${files.size} sources; the walk is wrong")

        val missing = mutableListOf<String>()
        var seen = 0
        for (file in files) {
            val text = file.readText()
            for (m in calls.findAll(text)) {
                val name = m.groupValues[1]
                seen += 1
                if (name !in SHAPES) missing.add("${file.name}: \"$name\"")
            }
        }
        assertTrue(seen > 20, "only $seen icon names found; the pattern is wrong")
        assertTrue(
            missing.isEmpty(),
            "these draw a dot rather than an icon, which looks like a bullet on a " +
                "screen that otherwise renders perfectly:\n  " + missing.joinToString("\n  ") +
                "\nEither add the path to SHAPES or use a name that is there: " +
                SHAPES.keys.sorted().joinToString(", "),
        )
    }

    /** And nothing in SHAPES is unused, because a drawing nobody
        asks for is a drawing nobody has looked at since it was
        added. Reported rather than failed: an icon can legitimately
        be here for a manifest to name. */
    @Test fun theShapeListIsNotFullOfGhosts() {
        val text = sources().joinToString("\n") { it.readText() }
        val named = calls.findAll(text).map { it.groupValues[1] }.toSet()
        val spare = SHAPES.keys.filter { it !in named }.sorted()
        println("icons: ${SHAPES.size} shapes, ${named.size} named in source, " +
            "${spare.size} reached only through the manifest: ${spare.joinToString(", ")}")
        assertTrue(SHAPES.isNotEmpty())
    }
}
