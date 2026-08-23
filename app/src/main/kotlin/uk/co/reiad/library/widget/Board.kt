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
import androidx.glance.unit.ColorProvider
import uk.co.reiad.library.core.Accents
import uk.co.reiad.library.core.School
import uk.co.reiad.library.core.Story
import uk.co.reiad.library.data.Reiad

/* ============================================================
   Two more of the board, on the home screen.

   The board inside the app is the reader's arrangement of what
   this site knows about them. These are the two pieces of it
   worth a row of somebody's home screen, which is a much higher
   bar: a home-screen widget is looked at without being opened,
   dozens of times a day, so it earns its place only if it says
   something that CHANGES and that is worth knowing at a glance.

   `Continue.kt` is the third and the oldest. Between them they
   are the three the site cannot do at all: a browser tab cannot
   put a sentence on a launcher.

   ---- what is deliberately NOT here ----

   The schools, the tools and the stock check. All three are a
   list of links, and a list of links on a home screen is the app
   icon with extra steps. A shortcut is the right shape for those
   and `res/xml/shortcuts.xml` already holds them.

   ---- and both read the CACHE, never the network ----

   A widget's process is the launcher's and its work has seconds,
   not a round trip: `provideGlance` blocking on three RSS feeds
   is a widget that shows nothing until it gives up. Both read
   what the app last stored, which is exactly what the app itself
   shows offline, so the widget and the screen behind it can
   never disagree.
   ============================================================ */

/* ---------- how far you are ---------- */

/** The tick count per school, and nothing else.

    Deliberately not a bar, for the reason `ui/Widgets.kt` gives
    at length: how many lessons a school HAS comes down with that
    school's ladder, and a widget cannot fetch one. A bar drawn
    against a guessed denominator is a bar that disagrees with the
    school's own hub two taps away. */
class ProgressWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val reiad = Reiad(context)
        /* Read ONCE and from the cache. `manifest()` goes to the
           network and was being called twice a row here, which in
           a launcher's process is eight round trips to fill in
           four names. */
        val ladders = reiad.cachedManifest()?.ladders.orEmpty()
        val rows = School.entries.map { school ->
            val row = ladders.firstOrNull { it.key == school.id }
            SchoolRow(
                id = school.id,
                name = row?.bn?.takeIf { it.isNotBlank() } ?: school.id,
                href = row?.href?.takeIf { it.isNotBlank() } ?: "/${school.id}",
                done = reiad.ticksNow(school).size,
            )
        }
        provideContent { GlanceTheme { Progress(rows, isNight(context)) } }
    }
}

private data class SchoolRow(
    val id: String,
    val name: String,
    val href: String,
    val done: Int,
)

@Composable
private fun Progress(rows: List<SchoolRow>, dark: Boolean) {
    val c = coloursFor(Accents.GREEN, dark)
    Column(
        GlanceModifier
            .fillMaxSize()
            .background(Color(c.panel))
            .cornerRadius(20.dp)
            .padding(16.dp),
        verticalAlignment = Alignment.Vertical.Top,
    ) {
        Text(
            "কতটা হলো",
            style = TextStyle(
                color = ColorProvider(Color(c.accent)),
                fontSize = 11.pt(),
                fontWeight = FontWeight.Medium,
            ),
        )
        Spacer(GlanceModifier.height(8.dp))
        for (row in rows) {
            /* Each row opens ITS OWN school, through the site's
               own address, so the tap and a shared link cannot
               disagree about where a school is. */
            val ink = coloursFor(Accents.BY_KEY[row.id] ?: Accents.GREEN, dark)
            Row(
                GlanceModifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
                    .clickable(openOn(row.href)),
                verticalAlignment = Alignment.Vertical.CenterVertically,
            ) {
                Text(
                    row.name,
                    maxLines = 1,
                    style = TextStyle(
                        color = ColorProvider(Color(c.ink)),
                        fontSize = 14.pt(),
                    ),
                    modifier = GlanceModifier.defaultWeight(),
                )
                Text(
                    "${row.done} টা",
                    style = TextStyle(
                        color = ColorProvider(
                            Color(if (row.done > 0) ink.accent else c.inkSoft),
                        ),
                        fontSize = 13.pt(),
                        fontWeight = FontWeight.Medium,
                    ),
                )
            }
        }
    }
}

class ProgressReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ProgressWidget()
}

/* ---------- the market board ---------- */

/** Three headlines out of the last feed this app stored.

    **The ranking is the server's.** `/api/news` scores every
    story against a keyword table and dedupes before it sends
    anything, so this takes the first three in the order they
    arrived. A widget that re-sorted would be a second editorial
    judgement, and one that would disagree with the same board in
    the app.

    Bangla where the translation succeeded and English where it
    did not, per story: a headline nobody can read is still better
    than a gap where the news should be. */
class NewsWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val stories = Reiad(context).cachedNews()?.items.orEmpty().take(3)
        provideContent { GlanceTheme { News(stories, isNight(context)) } }
    }
}

@Composable
private fun News(stories: List<Story>, dark: Boolean) {
    val c = coloursFor(Accents.GOLD, dark)
    Column(
        GlanceModifier
            .fillMaxSize()
            .background(Color(c.panel))
            .cornerRadius(20.dp)
            .padding(16.dp),
        verticalAlignment = Alignment.Vertical.Top,
    ) {
        Text(
            "বাজারের খবর",
            style = TextStyle(
                color = ColorProvider(Color(c.accent)),
                fontSize = 11.pt(),
                fontWeight = FontWeight.Medium,
            ),
        )
        Spacer(GlanceModifier.height(8.dp))

        if (stories.isEmpty()) {
            /* Not "no news". A widget that says it has nothing is
               a widget taking a row of somebody's home screen to
               say so; this says what will be here and what it
               needs, which is the same rule the board follows. */
            Text(
                "নেটওয়ার্ক পেলেই তিনটা সূত্র থেকে বাছাই করা শিরোনাম এখানে আসবে।",
                maxLines = 3,
                style = TextStyle(color = ColorProvider(Color(c.inkSoft)), fontSize = 13.pt()),
                modifier = GlanceModifier.clickable(openOn("/insights")),
            )
            return@Column
        }

        for (story in stories) {
            Column(
                GlanceModifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    /* The publisher's own URL, in the reader's own
                       browser. This app does not host The Business
                       Standard and should not look as though it
                       does. */
                    .clickable(openOn(story.url)),
            ) {
                Text(
                    story.headline("bn"),
                    maxLines = 2,
                    style = TextStyle(
                        color = ColorProvider(Color(c.ink)),
                        fontSize = 14.pt(),
                        fontWeight = FontWeight.Medium,
                    ),
                )
                if (story.source.isNotBlank()) {
                    Text(
                        story.source,
                        maxLines = 1,
                        style = TextStyle(
                            color = ColorProvider(Color(c.inkSoft)),
                            fontSize = 11.pt(),
                        ),
                    )
                }
            }
        }
    }
}

class NewsReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NewsWidget()
}

/* ---------- the small bridges ----------

   A widget is drawn in the LAUNCHER's process, so none of the
   app's Compose runs in it and none of its theme is in scope.
   These three are the whole of what has to cross. */

/** The system's own night setting, asked for rather than
    inferred: the app's own theme choice is not in scope here. */
internal fun isNight(context: Context): Boolean =
    (context.resources.configuration.uiMode and
        android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
        android.content.res.Configuration.UI_MODE_NIGHT_YES

/** Four colours as plain ints, out of the app's own palette.

    Through `coloursOf` rather than a second XML theme, so an
    accent retuned in `Palette.kt` is retuned on the home screen
    too. A `RemoteViews` has no canvas, so what does NOT cross is
    the material: six kinds of glass, the grain, the inset edge
    and the light that follows a finger are all `drawWithCache`.
    The widget is the ground, the accent and the words, which is
    what survives the trip honestly rather than an imitation of
    glass drawn in flat colour. */
internal data class WidgetInk(
    val panel: Int,
    val ink: Int,
    val inkSoft: Int,
    val accent: Int,
)

internal fun coloursFor(accent: uk.co.reiad.library.core.Accent, dark: Boolean): WidgetInk {
    val c = uk.co.reiad.library.ui.coloursOf(accent, dark)
    return WidgetInk(
        panel = c.panel.value.toInt(),
        ink = c.ink.value.toInt(),
        inkSoft = c.inkSoft.value.toInt(),
        accent = c.accent.value.toInt(),
    )
}

/** A VIEW on the site's own address, so a widget's tap goes
    through the same `Address.kt` a shared link does. */
internal fun openOn(href: String) = actionStartActivity(
    Intent(Intent.ACTION_VIEW).apply {
        data = android.net.Uri.parse(
            if (href.startsWith("http")) href else "https://reiad.co.uk$href",
        )
        setClassName("uk.co.reiad.library", "uk.co.reiad.library.MainActivity")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    },
)

/** Glance takes a TextUnit and the app's `sp` extension is a
    Compose one, so this is the bridge rather than an import that
    looks right and is a different type. */
internal fun Int.pt() = androidx.compose.ui.unit.TextUnit(
    this.toFloat(),
    androidx.compose.ui.unit.TextUnitType.Sp,
)
