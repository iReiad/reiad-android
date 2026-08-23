package uk.co.reiad.library.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
/* The APPWIDGET one, which is the overload that takes an
   Intent. `androidx.glance.action.actionStartActivity` takes a
   ComponentName and cannot carry an address, so importing it
   compiles and drops the whole point: the widget would open
   the app's front page rather than the lesson. */
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import kotlinx.coroutines.flow.first
import uk.co.reiad.library.core.Accents
import uk.co.reiad.library.core.Bookmark
import uk.co.reiad.library.core.School
import uk.co.reiad.library.data.Reiad
import uk.co.reiad.library.ui.coloursOf

/* ============================================================
   Where you were, on the home screen.

   This is the clearest thing in the whole app that a website
   cannot do. The site knows the bookmark, it draws a resume card
   on every school hub, and a reader still has to open a browser,
   find the tab and press the card. A widget puts the same
   sentence where the reader already is.

   ---- and it draws nothing when there is nothing ----

   A widget that says "nothing yet" for a reader who has not
   started is a widget taking a row of somebody's home screen to
   say it has no news. So the empty state is the site's own
   invitation instead: the name and one line about what is here.
   Both are two taps from being useful; neither is a placeholder.

   ---- why Glance and not RemoteViews ----

   A widget's process is the launcher's, so none of the app's
   Compose runs in it: `RemoteViews` is the only thing that can be
   sent across. Glance compiles a Compose-shaped description down
   to exactly that, which means the palette and the type scale
   come from the same `coloursOf` the app uses rather than from a
   second XML theme that would drift the first time an accent was
   retuned.

   What does NOT come across is the material. Six kinds of glass,
   a grain, an inset edge and a light that follows a finger are
   all `drawWithCache`, and a `RemoteViews` has no canvas. The
   widget is the ground, the accent, the faces and the words,
   which is what survives the trip honestly rather than an
   imitation of glass drawn in flat colour.
   ============================================================ */

class ContinueWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val reiad = Reiad(context)

        /* The most recent bookmark across every school, which is
           what "continue" means: a reader mid-way through two
           schools wants the one they touched last, and `ts` is
           what says which that was. */
        val marks = School.entries.mapNotNull { school ->
            reiad.bookmark(school).first()?.let { school to it }
        }
        val latest = marks.maxByOrNull { (_, mark) -> mark.ts }
        val site = reiad.manifest().value

        provideContent {
            GlanceTheme {
                Continue(
                    school = latest?.first,
                    mark = latest?.second,
                    name = site?.site?.name ?: "Reiad's Library",
                    dark = isDark(context),
                )
            }
        }
    }
}

/** The system's own night setting, which a widget must ask for
    rather than infer: it is drawn in the launcher's process and
    the app's own theme choice is not in scope there. */
private fun isDark(context: Context): Boolean =
    (context.resources.configuration.uiMode and
        android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
        android.content.res.Configuration.UI_MODE_NIGHT_YES

@Composable
private fun Continue(school: School?, mark: Bookmark?, name: String, dark: Boolean) {
    /* The school's own accent, which is the site's rule one level
       out: a page wears its section's colour, and so does a
       widget pointing at one. */
    val accent = school?.let { Accents.BY_KEY[it.id] } ?: Accents.GREEN
    val c = coloursOf(accent, dark)
    /* A VIEW on the site's own address, so the widget goes
       through the same `Address.kt` every shared link does: the
       tap and the shared link cannot disagree about where a
       lesson is. `actionStartActivity` takes a ComponentName and
       an intent separately at this version, so the class is named
       rather than set on the intent. */
    val open = actionStartActivity(
        Intent(Intent.ACTION_VIEW).apply {
            data = android.net.Uri.parse(mark?.url ?: "https://reiad.co.uk/")
            setClassName("uk.co.reiad.library", "uk.co.reiad.library.MainActivity")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        },
    )

    Column(
        GlanceModifier
            .fillMaxSize()
            .background(Color(c.panel.value))
            .cornerRadius(20.dp)
            .padding(16.dp)
            .clickable(open),
        verticalAlignment = Alignment.Vertical.Top,
    ) {
        Row(GlanceModifier.fillMaxWidth()) {
            Text(
                if (mark == null) "REIAD'S LIBRARY" else "CARRY ON",
                style = TextStyle(
                    color = androidx.glance.unit.ColorProvider(Color(c.accent.value)),
                    fontSize = 11.sp(),
                    fontWeight = FontWeight.Medium,
                ),
            )
        }
        Spacer(GlanceModifier.height(8.dp))
        Text(
            mark?.title?.takeIf { it.isNotBlank() }
                ?: "Six free courses in Bangla, five calculators and a stock model.",
            maxLines = 3,
            style = TextStyle(
                color = androidx.glance.unit.ColorProvider(Color(c.ink.value)),
                fontSize = 16.sp(),
                fontWeight = FontWeight.Medium,
            ),
        )
        if (mark != null && mark.stage.isNotBlank()) {
            Spacer(GlanceModifier.height(6.dp))
            Text(
                mark.stage,
                maxLines = 1,
                style = TextStyle(
                    color = androidx.glance.unit.ColorProvider(Color(c.inkSoft.value)),
                    fontSize = 13.sp(),
                ),
            )
        }
    }
}

/* Glance takes a TextUnit and the app's `sp` extension is a
   Compose one, so this is the small bridge rather than an import
   that looks right and is a different type. */
private fun Int.sp() = androidx.compose.ui.unit.TextUnit(
    this.toFloat(),
    androidx.compose.ui.unit.TextUnitType.Sp,
)

class ContinueReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ContinueWidget()
}
