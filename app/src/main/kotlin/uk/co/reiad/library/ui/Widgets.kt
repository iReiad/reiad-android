package uk.co.reiad.library.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import uk.co.reiad.library.core.Bookmark
import uk.co.reiad.library.core.LadderSchool
import uk.co.reiad.library.core.NavGroup
import uk.co.reiad.library.core.NavItem
import uk.co.reiad.library.core.Piece
import uk.co.reiad.library.core.WidgetSize
import uk.co.reiad.library.core.stock.inScript
import uk.co.reiad.library.core.SiteManifest
import uk.co.reiad.library.core.Story
import uk.co.reiad.library.core.rowsOf
import uk.co.reiad.library.SchoolCard
import uk.co.reiad.library.accentOf

/* ============================================================
   What each widget draws.

   ---- the contract, in one sentence ----

   `DRAWABLE` is the list of kinds THIS BUILD has a renderer for,
   and it is what `layoutOf()` filters a stored board against. A
   kind in the site's catalogue and not in this set is skipped,
   silently and on purpose: the site ships a new widget the day
   it is written and this app gets it at its next release, and
   in between a reader sees a board one card short rather than a
   blank rectangle with a title on it.

   So ADDING a renderer here is the whole of adding a widget to
   the app. Nothing else changes: the name, the note, the icon
   and whether it is on the default board are all the site's.

   ---- and none of these is a new feature ----

   Every one draws a reading this app already takes, one screen
   deeper. That is the test a new one has to pass: a widget whose
   figure exists nowhere else is a feature wearing a widget's
   clothes, and it should be built as a feature first.
   ============================================================ */

/** The kinds this build can draw.

    Six of the site's twelve. The other six need an account
    fetch this screen does not make yet (`streak`, `diet`,
    `routine`, `target`, `library`) or an endpoint this app has
    no client for (`market`), and every one of them is offered in
    the picker on the site and simply absent here, which is the
    contract above working rather than failing. */
val DRAWABLE: Set<String> = setOf(
    "continue", "progress", "pulse", "market", "schools", "tools", "stock",
    "streak", "routine", "diet", "target", "library",
)

/** Everything one of these renderers might need.

    One bag rather than eleven parameters per widget, because the
    set grows every time a renderer is added and a signature that
    changes on every addition is a signature every call site has
    to be edited for. */
data class BoardData(
    val site: SiteManifest?,
    val ticks: Map<String, Set<String>>,
    val bookmarks: Map<String, Bookmark>,
    val pieces: List<Piece>,
    val sway: Sway,
    val icons: Map<String, String>,
    val lang: String,
    val news: List<Story> = emptyList(),
    /** The `days-active` set, which is local: the year drawing on
        the account page reads the same one. */
    val daysActive: Set<String> = emptySet(),
    /** Today's routine summary, out of the cache the launcher
        widget reads, so the two can never disagree. Null until a
        day has been read once. */
    val routine: uk.co.reiad.library.data.RoutineGlance? = null,
    /** And today's food log, the same way. */
    val diet: uk.co.reiad.library.data.DietGlance? = null,
    val today: String = "",
    /** The reading list and the targets, off the account, so the
        last two of the site's twelve kinds have something to
        draw. Empty signed out, and the widgets say so. */
    val kept: List<uk.co.reiad.library.core.Kept> = emptyList(),
    val targets: List<uk.co.reiad.library.core.Target> = emptyList(),
)

/** What a widget can ask the app to do. */
data class BoardActions(
    val onSchool: (LadderSchool) -> Unit,
    val onItem: (NavItem) -> Unit,
    val onPiece: (Piece) -> Unit,
    val onResume: (String, Bookmark) -> Unit,
    val onStory: (Story) -> Unit = {},
    val onKept: (uk.co.reiad.library.core.Kept) -> Unit = {},
)

/** One widget, drawn.

    Returns whether it drew anything. A widget with nothing to
    SAY yet, as against nothing to draw, still draws: it says
    what it will show and when, which is `DIET.md` section 24's
    rule and the right one everywhere. What returns false is a
    kind with no renderer, which is the version gap above. */
@Composable
fun Widget(id: String, size: WidgetSize, data: BoardData, act: BoardActions): Boolean {
    /* The SIZE reaches the kinds it genuinely changes. A feed at
       `wide` shows its first story and at `tall` the morning's
       worth; the two link bands show one row of places wide and
       the lot tall. A size that only stretched the same drawing
       would be a stretch wearing a size's name, and the ones with
       one honest drawing ignore the argument rather than fake a
       second. */
    when (id) {
        "continue" -> ContinueWidget(data, act)
        "progress" -> ProgressWidget(data, act, size)
        "pulse" -> PulseWidget(data, act, rows = if (size == WidgetSize.TALL) 4 else 1)
        "market" -> MarketWidget(data, act, rows = if (size == WidgetSize.TALL) 5 else 3)
        "schools" -> SchoolsWidget(data, act)
        "tools" -> ToolsWidget(data, act)
        "stock" -> StockWidget(data, act)
        "streak" -> StreakWidget(data)
        "routine" -> RoutineBoardWidget(data, act)
        "diet" -> DietBoardWidget(data, act)
        "target" -> TargetWidget(data, size)
        "library" -> LibraryWidget(data, act, size)
        else -> return false
    }
    return true
}

/* ---------- the heading a widget wears ---------- */

@Composable
private fun WidgetHead(title: String, note: String? = null) {
    val c = LocalReiad.current
    Column(Modifier.fillMaxWidth().padding(bottom = Gap.s4)) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = c.ink,
            modifier = Modifier.semantics { heading() },
        )
        if (!note.isNullOrBlank()) {
            Spacer(Modifier.height(Gap.s2))
            Text(note, style = MaterialTheme.typography.bodySmall, color = c.inkSoft)
        }
    }
}

/* ---------- where you left off ---------- */

/** The most recent bookmark, across every school.

    One card, not four. A reader is in the middle of one thing at
    a time and a board that says "you were here, and here, and
    here" is asking them to choose rather than answering. */
@Composable
private fun ContinueWidget(data: BoardData, act: BoardActions) {
    val latest = data.bookmarks.entries
        .filter { it.value.title.isNotBlank() }
        .maxByOrNull { it.value.ts }
    val school = latest?.let { entry ->
        data.site?.ladders?.firstOrNull { it.key == entry.key }
    }

    if (latest == null) {
        /* Not a blank, and not a nought. A reader who has read
           nothing has not failed at anything: this says what will
           be here and how it gets here. */
        InfoCard(
            title = if (data.lang == "bn") "এখানে আপনার পাঠ থাকবে" else "Your lesson will be here",
            dek = if (data.lang == "bn") {
                "যে কোনো একটা স্কুলের পাঠ খুললেই, পরের বার এখান থেকেই ধরতে পারবেন।"
            } else {
                "Open a lesson in any school and this is where you pick it back up."
            },
        )
        return
    }

    val card: @Composable () -> Unit = {
        GoCard(
            title = latest.value.title,
            dek = school?.bn,
            chip = if (data.lang == "bn") "যেখানে ছিলেন" else "Where you left off",
            go = "পড়া চালিয়ে যান",
            sway = data.sway,
            art = data.icons[latest.key]?.let { { Icon(it, size = 18.dp) } },
            onOpen = { act.onResume(latest.key, latest.value) },
        )
    }
    /* In the school's own colour where the manifest knows it, and
       in the page's where it does not: a bookmark can outlive a
       school being taken off the ladder table, and a card with no
       accent is better than no card. */
    if (school == null) card()
    else ReiadTheme(accent = accentOf(school), dark = LocalReiad.current.isDark) { card() }
}

/* ---------- how far you are ---------- */

/** How many lessons are ticked, per school.

    **A count, and deliberately not a bar.** A bar needs a
    denominator and the manifest does not carry one: how many
    lessons a school HAS comes down with that school's ladder,
    one fetch deeper, and this screen makes no such fetch. A bar
    drawn against a number this screen guessed would be a bar
    that disagrees with the school's own hub, which is the
    failure at the top of `CLAUDE.md` wearing a progress bar.

    So it says the true thing it can say. The hub two taps away
    has the ring, the ladder and the denominator. */
@Composable
private fun ProgressWidget(data: BoardData, act: BoardActions, size: WidgetSize) {
    val c = LocalReiad.current
    val schools = data.site?.ladders.orEmpty()
    if (schools.isEmpty()) return
    val any = schools.any { data.ticks[it.key].orEmpty().isNotEmpty() }

    Pane(Modifier.fillMaxWidth()) {
        WidgetHead(
            if (data.lang == "bn") "কতটা হলো" else "How far you are",
            if (any || size == WidgetSize.SMALL) {
                null
            } else if (data.lang == "bn") {
                "একটা পাঠ পড়া হলে টিক দিন, এখানে জমতে থাকবে।"
            } else {
                "Tick a lesson when you have read it and it collects here."
            },
        )
        for (school in schools) {
            val done = data.ticks[school.key].orEmpty().size
            Row(
                Modifier.fillMaxWidth().padding(vertical = Gap.s3),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ReiadTheme(accent = accentOf(school), dark = c.isDark) {
                    Icon(
                        data.icons[school.key] ?: "skills",
                        size = 16.dp,
                        tint = LocalReiad.current.accent,
                    )
                }
                Spacer(Modifier.width(Gap.s5))
                Text(
                    school.bn.ifBlank { school.en },
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.ink,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(Gap.s4))
                Text(
                    /* At SMALL, the number alone. Half the row
                       has no width for a truncated school name
                       AND a phrase, and "টাকা ও শে…3 টা পাঠ" is
                       what shipping both looked like: the count
                       ran straight into the ellipsis. */
                    when {
                        size == WidgetSize.SMALL -> inScript(done.toString(), data.lang)
                        data.lang == "bn" -> "${inScript(done.toString(), "bn")} টা পাঠ"
                        else -> "$done read"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (done > 0) c.accent else c.inkSoft,
                    maxLines = 1,
                )
            }
        }
    }
}

/* ---------- the latest writing ---------- */

@Composable
private fun PulseWidget(data: BoardData, act: BoardActions, rows: Int) {
    val live = data.pieces.filter { it.status == "live" }.take(rows)
    if (live.isEmpty()) {
        InfoCard(
            title = if (data.lang == "bn") "নতুন লেখা এখানে আসবে" else "New writing lands here",
            dek = if (data.lang == "bn") {
                "নেটওয়ার্ক পেলেই সবচেয়ে নতুন লেখাগুলো এখানে দেখা যাবে।"
            } else {
                "The newest pieces show here as soon as there is a connection."
            },
        )
        return
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Gap.s4)) {
        WidgetHead(if (data.lang == "bn") "নতুন লেখা" else "Latest writing")
        for (piece in live) {
            RowCard(
                title = piece.title,
                icon = "pen",
                chip = piece.topics.firstOrNull() ?: piece.tag.takeIf { it.isNotBlank() },
                onOpen = { act.onPiece(piece) },
                trailing = if (piece.minutes > 0) {
                    { MinutesTag(piece.minutes, data.lang) }
                } else {
                    null
                },
            )
        }
    }
}

@Composable
private fun MinutesTag(minutes: Int, lang: String) {
    val c = LocalReiad.current
    Text(
        if (lang == "bn") "$minutes মিনিট" else "$minutes min",
        style = MaterialTheme.typography.labelSmall,
        color = c.inkSoft,
    )
}

/* ---------- the market board ---------- */

/** Today's headlines, as the site picked them.

    **The ranking is the server's and this draws it in order.**
    `/api/news` reads three feeds, scores each story against a
    keyword table and dedupes, so what arrives is already the
    shortlist; a client that re-sorted would be a second
    editorial judgement nobody asked for, and one that would
    disagree with the same board on the site.

    Bangla where the translation succeeded and English where it
    did not, per story rather than per board: a headline nobody
    can read is still better than a gap where the news should
    be. */
@Composable
private fun MarketWidget(data: BoardData, act: BoardActions, rows: Int) {
    val c = LocalReiad.current
    val stories = data.news.take(rows)
    if (stories.isEmpty()) {
        InfoCard(
            title = if (data.lang == "bn") "বাজারের খবর এখানে আসবে" else "Market pulse lands here",
            dek = if (data.lang == "bn") {
                "তিনটা সূত্র থেকে বাছাই করা শিরোনাম, নেটওয়ার্ক পেলেই।"
            } else {
                "Headlines picked from three feeds, as soon as there is a connection."
            },
        )
        return
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Gap.s4)) {
        WidgetHead(if (data.lang == "bn") "বাজারের খবর" else "Market pulse")
        for (story in stories) {
            RowCard(
                title = story.headline(data.lang),
                icon = "spark",
                /* The site's own label for the feed, not the
                   publisher's: a masthead that renames itself
                   cannot rename a region here. */
                chip = story.region.takeIf { it.isNotBlank() },
                onOpen = { act.onStory(story) },
                trailing = { SourceTag(story.source) },
            )
        }
    }
}

@Composable
private fun SourceTag(source: String) {
    val c = LocalReiad.current
    Text(
        source,
        style = MaterialTheme.typography.labelSmall,
        color = c.inkSoft,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.width(88.dp),
    )
}

/* ---------- the schools ---------- */

@Composable
private fun SchoolsWidget(data: BoardData, act: BoardActions) {
    val schools = data.site?.ladders.orEmpty()
    if (schools.isEmpty()) return
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Gap.s6)) {
        WidgetHead(if (data.lang == "bn") "যা যা শেখানো হয়" else "The schools")
        for (school in schools) {
            SchoolCard(
                school = school,
                done = data.ticks[school.key].orEmpty().size,
                sway = data.sway,
                icon = data.icons[school.key],
                onOpen = act.onSchool,
            )
        }
    }
}

/* ---------- the tools ---------- */

@Composable
private fun ToolsWidget(data: BoardData, act: BoardActions) {
    val group: NavGroup = data.site?.nav.orEmpty().firstOrNull { it.id == "make" } ?: return
    val rows = rowsOf(group)
    if (rows.isEmpty()) return
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Gap.s4)) {
        WidgetHead(if (data.lang == "bn") "যন্ত্রপাতি" else "The tools")
        for (item in rows) {
            RowCard(
                title = item.sub?.ifBlank { null } ?: item.label,
                icon = item.icon,
                chip = item.kind?.ifBlank { null },
                onOpen = { act.onItem(item) },
            )
        }
    }
}

/* ---------- the stock check, as a door rather than a form ---------- */

/** A card that opens the stock check, not a second copy of it.

    The check itself is 44 ratios and a verdict, and putting an
    input on the board would be putting the first field of a
    seven-field form on the front page: a reader types a ticker,
    presses go, and lands on a screen with an empty field.

    So this is the door, and what makes it worth a slot rather
    than a link is that it says what is behind it. */
@Composable
private fun StockWidget(data: BoardData, act: BoardActions) {
    val item = data.site?.nav.orEmpty()
        .flatMap { it.items }.firstOrNull { it.key == "stock" } ?: return
    GoCard(
        title = item.sub?.ifBlank { null } ?: item.label,
        dek = if (data.lang == "bn") {
            "একটা টিকার লিখুন, ৪৪টা অনুপাত আর একটা রায়।"
        } else {
            "Type a ticker: 44 ratios and one verdict."
        },
        chip = if (data.lang == "bn") "যন্ত্র" else "Tool",
        go = if (data.lang == "bn") "যাচাই করুন" else "Check one",
        sway = data.sway,
        art = { Icon(item.icon, size = 18.dp) },
        onOpen = { act.onItem(item) },
    )
}

/* ---------- what a half-width widget gets on a phone ---------- */

/** A `half` runs the full width on a handset.

    160dp of glass with a number in it is not legible, and
    pretending otherwise is how a dashboard becomes decoration.
    The size is still STORED, because the same board is read on a
    laptop where half means half. */
@Composable
fun Halved(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth()) { content() }
}


/* ---------- the days, and today's routine ---------- */

/** A year of days, the account page's own drawing on the board.

    No flame, nothing red, nothing counting down: the site's rule
    for this reading, kept on the front page too. Signed out the
    set is simply empty, and an empty year is an honest one. */
@Composable
private fun StreakWidget(data: BoardData) {
    Pane(Modifier.fillMaxWidth()) {
        WidgetHead(if (data.lang == "bn") "যে দিনগুলো এসেছেন" else "A year of days")
        YearOfDays(data.daysActive)
    }
}

/** Today's routine on the BOARD: the same summary the launcher
    widget draws, from the same cache, so the three places that
    say "today" (the day page, the home-screen widget, this)
    cannot disagree.

    The same two refusals as the launcher's: yesterday's summary
    is the invitation, and an unmarked day is never a nought. */
@Composable
private fun RoutineBoardWidget(data: BoardData, act: BoardActions) {
    val c = LocalReiad.current
    val glance = data.routine?.takeIf { it.date == data.today && it.of > 0 }
    val item = data.site?.nav.orEmpty()
        .flatMap { it.items }.firstOrNull { it.key == "routine" } ?: return

    Pane(Modifier.fillMaxWidth()) {
        WidgetHead(if (data.lang == "bn") "আজকের রুটিন" else "Today's routine")
        Tap(onClick = { act.onItem(item) }, label = item.label) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = Gap.s3),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (glance == null || glance.marked == 0) {
                    Text(
                        if (data.lang == "bn") {
                            "আজ এখনো খালি। একটা টিক দিয়ে শুরু করুন।"
                        } else {
                            "Today is still empty. Start with one tick."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.inkSoft,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Text(
                        if (data.lang == "bn") {
                            "${inScript(glance.marked.toString(), "bn")} / " +
                                inScript(glance.of.toString(), "bn")
                        } else {
                            "${glance.marked} / ${glance.of}"
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        color = c.accent,
                    )
                    Spacer(Modifier.width(Gap.s5))
                    Text(
                        if (data.lang == "bn") "টিক পড়েছে" else "ticked",
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.inkSoft,
                        modifier = Modifier.weight(1f),
                    )
                }
                Icon("chevron", size = 14.dp, tint = c.inkSoft)
            }
        }
    }
}


/** Today's food log on the board: the day's total, against the
    target where one is set.

    THE SENTENCES ARE THREE AND THEY ARE DIFFERENT. Nothing
    logged is an invitation; a total with no target is the total
    alone, because a bar against an invented denominator is a
    decoration; a total with a target says both numbers and
    draws the groove. A nought is never printed. */
@Composable
private fun DietBoardWidget(data: BoardData, act: BoardActions) {
    val c = LocalReiad.current
    val glance = data.diet?.takeIf { it.date == data.today }
    val item = data.site?.nav.orEmpty()
        .flatMap { it.items }.firstOrNull { it.key == "diet" } ?: return

    Pane(Modifier.fillMaxWidth()) {
        WidgetHead(if (data.lang == "bn") "আজকের খাওয়া" else "Today's log")
        Tap(onClick = { act.onItem(item) }, label = item.label) {
            Column(Modifier.fillMaxWidth().padding(vertical = Gap.s3)) {
                if (glance == null || glance.entries == 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (data.lang == "bn") {
                                "আজ এখনো কিছু লেখা হয়নি।"
                            } else {
                                "Nothing logged yet today."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = c.inkSoft,
                            modifier = Modifier.weight(1f),
                        )
                        Icon("chevron", size = 14.dp, tint = c.inkSoft)
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            inScript(glance.kcal.toString(), data.lang),
                            style = MaterialTheme.typography.headlineSmall,
                            color = c.accent,
                        )
                        Spacer(Modifier.width(Gap.s4))
                        Text(
                            if (glance.target > 0) {
                                if (data.lang == "bn") {
                                    "/ ${inScript(glance.target.toString(), "bn")} kcal"
                                } else {
                                    "of ${glance.target} kcal"
                                }
                            } else {
                                "kcal"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = c.inkSoft,
                            modifier = Modifier.weight(1f),
                        )
                        Icon("chevron", size = 14.dp, tint = c.inkSoft)
                    }
                    if (glance.target > 0) {
                        Spacer(Modifier.height(Gap.s4))
                        Groove((glance.kcal.toFloat() / glance.target).coerceIn(0f, 1f))
                    }
                }
            }
        }
    }
}


/* ---------- the last two of the site's twelve ---------- */

/** A target, on the board.

    Only a METRIC gets a bar, which is the account page's own
    rule said again: a metric is the one kind whose number is
    stored, so the bar here cannot go stale. A course or a habit
    names itself and points at the page that computes it, rather
    than drawing a bar this widget would have to guess. */
@Composable
private fun TargetWidget(data: BoardData, size: WidgetSize) {
    val c = LocalReiad.current
    Pane(Modifier.fillMaxWidth()) {
        WidgetHead(if (data.lang == "bn") "লক্ষ্য" else "A target")
        val rows = data.targets.filter { it.doneAt == null }
        if (rows.isEmpty()) {
            Text(
                if (data.lang == "bn") {
                    "অ্যাকাউন্ট পাতায় একটা লক্ষ্য ঠিক করলে এখানে দেখা যাবে।"
                } else {
                    "Set a target on the account page and it shows here."
                },
                style = bodySmallFor(data.lang),
                color = c.inkSoft,
            )
            return@Pane
        }
        val shown = rows.take(if (size == WidgetSize.SMALL) 1 else 2)
        shown.forEachIndexed { i, target ->
            if (i > 0) Spacer(Modifier.height(Gap.s5))
            Text(
                target.label.ifBlank { target.subject },
                style = bodyStyle(target.label.ifBlank { target.subject }),
                color = c.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (target.kind == "metric" && target.target > 0) {
                Spacer(Modifier.height(Gap.s3))
                Groove((target.reached / target.target).toFloat(), height = 5.dp)
                Spacer(Modifier.height(Gap.s2))
                Text(
                    "${trim(target.reached)} / ${trim(target.target)} ${target.unit}".trim(),
                    style = MaterialTheme.typography.labelMedium,
                    color = c.inkSoft,
                )
            } else {
                Spacer(Modifier.height(Gap.s2))
                Text(
                    if (data.lang == "bn") "অ্যাকাউন্ট পাতায় হিসাবটা চলে" else "Counted on the account page",
                    style = MaterialTheme.typography.labelMedium,
                    color = c.inkSoft,
                )
            }
        }
    }
}

private fun trim(n: Double): String =
    if (n == n.toLong().toDouble()) n.toLong().toString() else "%.1f".format(n)

/** The reading list, on the board: the saved pieces waiting,
    newest first, each opening where it lives. */
@Composable
private fun LibraryWidget(data: BoardData, act: BoardActions, size: WidgetSize) {
    val c = LocalReiad.current
    Pane(Modifier.fillMaxWidth()) {
        WidgetHead(if (data.lang == "bn") "পরে পড়ব" else "Saved to read")
        val saved = data.kept.filter { it.saved == true }
        if (saved.isEmpty()) {
            Text(
                if (data.lang == "bn") {
                    "কোনো লেখায় সেভ চাপলে সেটা এখানে অপেক্ষা করবে।"
                } else {
                    "Save a piece and it waits for you here."
                },
                style = bodySmallFor(data.lang),
                color = c.inkSoft,
            )
            return@Pane
        }
        for (row in saved.take(if (size == WidgetSize.TALL) 5 else 2)) {
            Rung(Modifier.clickable(role = Role.Button) { act.onKept(row) }) {
                Icon("keep", size = 16.dp, tint = c.accent)
                Spacer(Modifier.width(Gap.s5))
                Text(
                    row.title.ifBlank { row.url },
                    style = bodyStyle(row.title.ifBlank { row.url }).copy(
                        fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                    ),
                    color = c.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** The small print in the reader's own language. */
@Composable
private fun bodySmallFor(lang: String) =
    if (lang == "bn") BanglaBody.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize)
    else MaterialTheme.typography.bodySmall
