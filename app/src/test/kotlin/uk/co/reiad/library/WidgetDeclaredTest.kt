package uk.co.reiad.library

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/* ============================================================
   A home-screen widget nobody can add.

   Three things have to agree or a widget is written, compiled,
   shipped and absent from the picker:

     1. a `GlanceAppWidgetReceiver` in the source,
     2. a `<receiver>` for it in the manifest, with an
        `APPWIDGET_UPDATE` filter and a provider resource,
     3. that `res/xml/widget_*.xml`, with a description string.

   Miss the second and the class is dead code. Miss the third and
   Android drops the receiver at install with a log line nobody
   reads. Miss the description and the widget appears in the
   picker with a blank line under its name.

   Nothing else here can see any of it. The app builds, every
   test passes, every screen renders, and the feature is missing
   in the one place it is meant to be visible: somebody else's
   home screen.
   ============================================================ */
class WidgetDeclaredTest {

    private val manifest = File("src/main/AndroidManifest.xml").readText()
    private val strings = File("src/main/res/values/strings.xml").readText()

    private fun receivers(): List<String> =
        File("src/main/kotlin/uk/co/reiad/library/widget").walkTopDown()
            .filter { it.extension == "kt" }
            .flatMap { file ->
                Regex("""class\s+(\w+)\s*:\s*GlanceAppWidgetReceiver""")
                    .findAll(file.readText()).map { it.groupValues[1] }
            }
            .toList()

    @Test fun everyReceiverIsDeclared() {
        val found = receivers()
        assertTrue(found.size >= 3, "found ${found.size} widget receivers; the walk is wrong")

        for (name in found) {
            assertTrue(
                manifest.contains("\".widget.$name\""),
                "$name is a widget receiver with no <receiver> in the manifest, so the " +
                    "class is dead code and the widget is absent from the picker",
            )
        }
    }

    @Test fun everyDeclaredReceiverHasAProviderAndADescription() {
        /* Each `<receiver>` block, so the resource is matched to
           the receiver that names it rather than to any resource
           mentioned anywhere in the file. */
        val blocks = Regex("""<receiver[\s\S]*?</receiver>""").findAll(manifest).toList()
        val widgets = blocks.filter { it.value.contains("APPWIDGET_UPDATE") }
        assertTrue(widgets.size >= 3, "found ${widgets.size} widget receivers in the manifest")

        for (block in widgets) {
            val name = Regex("""android:name="\.widget\.(\w+)"""")
                .find(block.value)?.groupValues?.get(1) ?: "?"
            val resource = Regex("""android:resource="@xml/(\w+)"""")
                .find(block.value)?.groupValues?.get(1)
            assertTrue(
                resource != null,
                "$name has an APPWIDGET_UPDATE filter and names no provider resource, " +
                    "so Android drops it at install",
            )
            val xml = File("src/main/res/xml/$resource.xml")
            assertTrue(xml.exists(), "$name names @xml/$resource, which does not exist")

            val text = xml.readText()
            val described = Regex("""android:description="@string/(\w+)"""")
                .find(text)?.groupValues?.get(1)
            assertTrue(
                described != null,
                "$resource.xml has no description, so the widget shows a blank line " +
                    "under its name in the picker",
            )
            assertTrue(
                strings.contains("\"$described\""),
                "$resource.xml names @string/$described, which strings.xml does not hold",
            )
            /* Nothing polls, and it is worth asserting rather than
               remembering: a widget that woke on a schedule would
               wake the app to redraw a sentence that had not
               changed. Each is redrawn when the app writes the
               thing it shows. */
            assertTrue(
                text.contains("""android:updatePeriodMillis="0""""),
                "$resource.xml polls. Every widget here is redrawn when its answer " +
                    "changes, which is a push rather than a wake-up.",
            )
        }
    }
}
