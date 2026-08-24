package uk.co.reiad.library.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import uk.co.reiad.library.core.Accents
import uk.co.reiad.library.core.BodyParser
import uk.co.reiad.library.core.Bookmark
import uk.co.reiad.library.core.Course
import uk.co.reiad.library.core.CourseLesson
import uk.co.reiad.library.core.CourseModule
import uk.co.reiad.library.core.CourseRung
import uk.co.reiad.library.core.CourseResponse
import uk.co.reiad.library.core.CourseSummary
import uk.co.reiad.library.core.CourseWhere
import uk.co.reiad.library.core.ProgrammeName
import uk.co.reiad.library.core.ProgrammeSummary
import uk.co.reiad.library.core.QuizQuestion
import uk.co.reiad.library.core.counted
import uk.co.reiad.library.core.courseKindWord
import uk.co.reiad.library.core.courseLessonId
import uk.co.reiad.library.core.courseTotals
import uk.co.reiad.library.core.doneIn
import uk.co.reiad.library.core.driveUrl
import uk.co.reiad.library.core.laddered
import uk.co.reiad.library.core.moduleIds
import uk.co.reiad.library.core.nextUp
import uk.co.reiad.library.courses.Answer
import uk.co.reiad.library.courses.LessonSource
import uk.co.reiad.library.courses.Reel
import uk.co.reiad.library.courses.problem
import uk.co.reiad.library.courses.reelFor

/* ============================================================
   The admin's course section, drawn HERE rather than handed to
   a browser.

   ---- the bug this is the fix for ----

   The gold card on `/skills` opened a Custom Tab at
   `/skills/courses`, and for the one reader the section belongs
   to, nothing was there. Not a wrong address: the address is
   right and the page is right. The site's reader session is a
   bearer token in the BROWSER's own storage and the app's is its
   own, so a tab opened from here arrives with no credential, the
   shell asks the endpoint, the endpoint says sign in, and the
   page says "you are either signed out or this is not your
   library": to somebody signed in on the phone, opening a
   section the app has already ASKED the server about, because
   that answer is what decides whether the card is drawn at all.

   Nothing about a browser hand-off can fix that. So this app asks
   `/api/courses` with the token it holds, and draws the answer.

   ---- five views, one file ----

   A shelf of certificates, one programme, one course, a module
   summary, a lesson. They are five views of one thing and the
   reader moves between them constantly, so they share a file the
   way the website's own player does.

   ---- everything in gold ----

   The site's own colour for this section, which is the colour of
   the card that opens it: gold is what this library uses for
   "yours, and not published".

   ---- and the rules that came with the section ----

   OPENING IS NOT FINISHING. Opening a lesson moves the bookmark
   and ticks nothing. The tick is a button, because the only
   honest signal that a video has been watched is a reader saying
   so, and no player event here is ever read as one.

   A QUIZ MARKS NOTHING. The export carries no answer key, so
   answers are recorded and never scored, and the screen says that
   out loud rather than implying a mark it cannot compute.
   ============================================================ */

/** The section's colour, everywhere in it. */
@Composable
private fun Gold(content: @Composable () -> Unit) {
    ReiadTheme(accent = Accents.GOLD, dark = LocalReiad.current.isDark, content = content)
}

/** A bar with its number beside it, which is the site's
    `.meter-line`: a groove and a mono count.

    The number is written out rather than shown as a percentage,
    for the reason a school's ring is: "12 / 102" is a reader's
    own answer to how far they have got, and 12% is a statistic
    about them. */
@Composable
private fun Meter(done: Int, total: Int, said: String? = null, modifier: Modifier = Modifier) {
    val c = LocalReiad.current
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Groove(
            fraction = if (total > 0) done.toFloat() / total else 0f,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(Gap.s6))
        Text(
            said ?: "$done / $total",
            style = MaterialTheme.typography.labelMedium,
            color = c.inkSoft,
        )
    }
}

/** What went wrong, in the server's own words, with the one
    button that is worth offering for it.

    A refusal and a failure are different: "this is not yours" has
    no retry worth pressing, and offering one would be this screen
    inviting somebody to try the door again. */
@Composable
private fun Refused(answer: Answer<*>, onRetry: () -> Unit, onAccount: () -> Unit) {
    when (answer) {
        is Answer.Got<*> -> Unit
        Answer.SignedOut -> Problem(
            title = "Signed out",
            detail = "Your session has expired. Sign in again to carry on.",
            retryLabel = "Go to the account",
            onRetry = onAccount,
        )
        is Answer.NotYours -> Problem(title = "Not yours", detail = answer.message)
        is Answer.Refused -> Problem(
            title = "That did not load",
            detail = answer.message,
            onRetry = onRetry,
        )
        is Answer.Unreachable -> Problem(
            title = "Could not reach the site",
            detail = answer.message,
            onRetry = onRetry,
        )
    }
}

/* ============================================================
   1. The shelf
   ============================================================ */

/**
 * `/skills/courses`: the certificates.
 *
 * Programmes rather than courses. The eight were always the eight
 * of one certificate, and a flat list had nothing to say about
 * which belonged to which.
 */
@Composable
fun CourseShelfScreen(
    answer: Answer<List<ProgrammeSummary>>?,
    read: Set<String>,
    bottomPadding: Dp,
    onOpen: (ProgrammeSummary) -> Unit,
    onRetry: () -> Unit,
    onAccount: () -> Unit,
    modifier: Modifier = Modifier,
) = Gold {
    val programmes = (answer as? Answer.Got)?.value.orEmpty()
    val courses = programmes.sumOf { it.courses.size }

    LazyColumn(
        modifier.fillMaxSize().padding(horizontal = Gap.s8),
        contentPadding = PaddingValues(top = topClearance(), bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(Gap.s6),
    ) {
        item("head") {
            PageHead(
                title = "Third-party courses",
                eyebrow = "কোর্স · COURSES",
                /* The site's own sentence, and the numbers in it
                   are counted from the payload rather than
                   written down: a ninth course is in this line
                   with no release. */
                lede = if (answer is Answer.Got) {
                    "${counted(programmes.size, "programme")}, ${counted(courses, "course")}, " +
                        "kept here for one person's own study. The material is somebody " +
                        "else's and none of it is published: every page in this section " +
                        "is behind the admin check."
                } else {
                    null
                },
            )
            Spacer(Modifier.height(Gap.s5))
        }

        if (answer == null) {
            item("waiting") { Skeleton(label = "Reading the catalogue") }
        }

        if (answer != null && answer !is Answer.Got) {
            item("refused") { Refused(answer, onRetry, onAccount) }
        }

        items(programmes.size, key = { programmes[it].slug }) { at ->
            val programme = programmes[at]
            val done = doneIn(read, programme.courses)
            GoCard(
                title = programme.title,
                dek = courseTotals(programme),
                chip = "PROGRAMME ${programme.n}",
                go = if (done > 0) "Carry on" else "Open the programme",
                done = programme.lessons > 0 && done >= programme.lessons,
                art = { Icon("layers", size = 18.dp) },
                onOpen = { onOpen(programme) },
            ) {
                Spacer(Modifier.height(Gap.s6))
                Meter(done, programme.lessons)
            }
        }

        if (answer is Answer.Got && programmes.isEmpty()) {
            item("empty") {
                InfoCard(
                    title = "Nothing on the shelf",
                    dek = "The catalogue answered, and there is no programme in it yet.",
                )
            }
        }
    }
}

/* ============================================================
   2. One programme
   ============================================================ */

/** `/skills/courses/<programme>`: one certificate, and its
    courses in the order they are meant to be taken.

    Drawn from the shelf's own payload, because there is no
    endpoint for one programme and the whole shelf is smaller than
    one course. */
@Composable
fun CourseProgrammeScreen(
    programme: ProgrammeSummary,
    read: Set<String>,
    bottomPadding: Dp,
    onOpen: (CourseSummary) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) = Gold {
    val done = doneIn(read, programme.courses)

    LazyColumn(
        modifier.fillMaxSize().padding(horizontal = Gap.s8),
        contentPadding = PaddingValues(top = topClearance(), bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(Gap.s6),
    ) {
        item("head") {
            Crumb("Courses", onBack)
            Spacer(Modifier.height(Gap.s7))
            PageHead(
                title = programme.title,
                eyebrow = "PROGRAMME ${programme.n}",
                lede = courseTotals(programme),
            )
            Meter(done, programme.lessons, "$done of ${programme.lessons} lessons done")
            Spacer(Modifier.height(Gap.s5))
        }

        items(programme.courses.size, key = { programme.courses[it].slug }) { at ->
            val course = programme.courses[at]
            val theirs = doneIn(read, listOf(course))
            GoCard(
                title = course.title,
                dek = courseTotals(course),
                chip = "COURSE ${course.n}",
                go = if (theirs > 0) "Carry on" else "Open the course",
                done = course.lessons > 0 && theirs >= course.lessons,
                art = { Icon("scroll", size = 18.dp) },
                onOpen = { onOpen(course) },
            ) {
                Spacer(Modifier.height(Gap.s6))
                Meter(theirs, course.lessons)
            }
        }
    }
}

/* ============================================================
   3. One course
   ============================================================ */

/** `/skills/courses/<programme>/<course>`.

    The deep link is the point of this page. A reader coming back
    to a course wants the lesson they have not done, not a table
    of contents they have to read to find it. */
@Composable
fun CourseScreen(
    programme: String,
    holder: ProgrammeName?,
    course: Course,
    read: Set<String>,
    bookmark: Bookmark?,
    bottomPadding: Dp,
    onOpenLesson: (CourseRung) -> Unit,
    onOpenModule: (CourseModule) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) = Gold {
    val rungs = remember(programme, course) { laddered(programme, course) }
    val done = rungs.count { it.id in read }
    val next = remember(rungs, read, bookmark) { nextUp(rungs, read, bookmark?.id) }

    LazyColumn(
        modifier.fillMaxSize().padding(horizontal = Gap.s8),
        contentPadding = PaddingValues(top = topClearance(), bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(Gap.s6),
    ) {
        item("head") {
            /* The certificate's own name, which came down with the
               course. A title made out of the slug would print a
               second, different name beside the one the shelf
               shows. */
            Crumb(holder?.title?.ifBlank { null } ?: "Programme", onBack)
            Spacer(Modifier.height(Gap.s7))
            PageHead(title = course.title, eyebrow = "COURSE ${course.n}")
            Meter(done, rungs.size, "$done of ${rungs.size} lessons done")
            Spacer(Modifier.height(Gap.s5))
        }

        if (next != null) {
            item("next") {
                GoCard(
                    title = next.title,
                    dek = listOf(next.moduleTitle, next.section)
                        .filter { it.isNotBlank() }
                        .joinToString(" · "),
                    chip = if (done > 0) "CARRY ON WHERE YOU LEFT OFF" else "START HERE",
                    go = "Open this lesson",
                    art = { Icon(iconFor(next.lesson.kind), size = 18.dp) },
                    onOpen = { onOpenLesson(next) },
                )
            }
        } else if (rungs.isNotEmpty()) {
            item("finished") {
                InfoCard(
                    title = "Every lesson in this course is ticked.",
                    dek = "Nothing is locked: any lesson can be opened again, and the " +
                        "tick can be taken off.",
                )
            }
        }

        items(course.modules.size, key = { course.modules[it].slug }) { at ->
            val mod = course.modules[at]
            if (mod.pending) {
                SoonCard(
                    title = mod.title,
                    dek = "This module's lessons have not been imported yet.",
                    note = "MODULE ${mod.n}",
                )
            } else {
                val ids = remember(course, mod) { moduleIds(course.slug, mod) }
                val modDone = ids.count { it in read }
                val first = mod.lessons.firstOrNull { lesson ->
                    courseLessonId(course.slug, mod.slug, lesson.slug) !in read
                } ?: mod.lessons.firstOrNull()

                GoCard(
                    title = mod.title,
                    dek = counted(mod.lessons.size, "lesson"),
                    chip = "MODULE ${mod.n}",
                    go = if (modDone > 0) "Carry on" else "Open the module",
                    done = ids.isNotEmpty() && modDone >= ids.size,
                    art = { Icon("grid", size = 18.dp) },
                    onOpen = {
                        /* Into the module's first unread lesson,
                           which is what a reader pressing a module
                           wants. The summary is the row under it,
                           for somebody who wants the list. */
                        val rung = first?.let { lesson ->
                            rungs.firstOrNull {
                                it.id == courseLessonId(course.slug, mod.slug, lesson.slug)
                            }
                        }
                        if (rung != null) onOpenLesson(rung) else onOpenModule(mod)
                    },
                ) {
                    Spacer(Modifier.height(Gap.s6))
                    Meter(modDone, ids.size)
                    Spacer(Modifier.height(Gap.s5))
                    PillButton("Module summary", { onOpenModule(mod) }, kind = ButtonKind.QUIET)
                }
            }
        }
    }
}

/* ============================================================
   4. A module summary
   ============================================================ */

/** `/skills/courses/<programme>/<course>/<module>`.

    Where the last lesson of a module lands. It is a stopping
    place: what was in the module, what is ticked, and the way on
    to the next one. Finishing a module is a moment, and being
    dropped straight into week four is how a reader loses track of
    what they have done. */
@Composable
fun CourseModuleScreen(
    course: Course,
    mod: CourseModule,
    rungs: List<CourseRung>,
    read: Set<String>,
    bottomPadding: Dp,
    onOpenLesson: (CourseRung) -> Unit,
    onOpenModule: (CourseModule) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) = Gold {
    val ids = remember(course, mod) { moduleIds(course.slug, mod) }
    val done = ids.count { it in read }
    val at = course.modules.indexOfFirst { it.slug == mod.slug }
    val after = course.modules.drop(at + 1).firstOrNull { !it.pending && it.lessons.isNotEmpty() }
    val mine = remember(rungs, mod) { rungs.filter { it.module == mod.slug } }

    LazyColumn(
        modifier.fillMaxSize().padding(horizontal = Gap.s8),
        contentPadding = PaddingValues(top = topClearance(), bottom = bottomPadding),
    ) {
        item("head") {
            Crumb(course.title, onBack)
            Spacer(Modifier.height(Gap.s7))
            PageHead(title = mod.title, eyebrow = "MODULE ${mod.n}")
            Meter(done, ids.size, "$done of ${ids.size} done")
            Spacer(Modifier.height(Gap.s7))
            if (ids.isNotEmpty() && done >= ids.size) {
                InfoCard(title = "Module finished.")
                Spacer(Modifier.height(Gap.s7))
            }
        }

        lessonRows(mine, read, onOpenLesson)

        item("onward") {
            Spacer(Modifier.height(Gap.s8))
            if (after != null) {
                Rung(onClick = { onOpenModule(after) }) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "NEXT MODULE",
                            style = MaterialTheme.typography.labelSmall,
                            color = LocalReiad.current.inkSoft,
                        )
                        Text(
                            after.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = LocalReiad.current.ink,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.width(Gap.s6))
                    Text(
                        "→",
                        style = MaterialTheme.typography.labelLarge,
                        color = LocalReiad.current.accent,
                    )
                }
            }
        }
    }
}

/** The lesson list, with its section headings, shared by the
    module summary and the rail.

    A section heading is drawn when it CHANGES rather than for
    every lesson, which is how a group of four reads as a group
    rather than as four labelled rows. */
private fun androidx.compose.foundation.lazy.LazyListScope.lessonRows(
    rungs: List<CourseRung>,
    read: Set<String>,
    onOpen: (CourseRung) -> Unit,
    here: String? = null,
) {
    var section = ""
    for (rung in rungs) {
        if (rung.section.isNotBlank() && rung.section != section) {
            section = rung.section
            val name = section
            item("section:${rung.id}") {
                Spacer(Modifier.height(Gap.s7))
                Eyebrow(name)
                Spacer(Modifier.height(Gap.s4))
            }
        }
        item(rung.id) { LessonRow(rung, rung.id in read, rung.id == here, onOpen) }
    }
}

@Composable
private fun LessonRow(
    rung: CourseRung,
    done: Boolean,
    here: Boolean,
    onOpen: (CourseRung) -> Unit,
) {
    val c = LocalReiad.current
    Rung(
        onClick = { onOpen(rung) },
        ground = if (here) c.paperSunk else null,
    ) {
        /* The tick is a MARK and not the only thing saying so: the
           kind word beside it changes too, because nothing in this
           app may carry meaning in colour alone. */
        Text(
            if (done) "✓" else "·",
            style = MaterialTheme.typography.labelLarge,
            color = if (done) c.accent else c.inkSoft,
            modifier = Modifier.width(Gap.s9),
        )
        Column(Modifier.weight(1f)) {
            Text(
                rung.title,
                style = MaterialTheme.typography.titleSmall,
                color = if (done) c.inkSoft else c.ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                courseKindWord(rung.lesson.kind) + if (done) " · done" else "",
                style = MaterialTheme.typography.labelSmall,
                color = c.inkSoft,
            )
        }
    }
}

private fun iconFor(kind: String): String = when (kind) {
    "video" -> "play"
    "reading" -> "book"
    "quiz", "exam" -> "question"
    else -> "note"
}

/* ============================================================
   5. A lesson
   ============================================================ */

/**
 * `/skills/courses/<programme>/<course>/<module>/<lesson>`.
 *
 * The video, the reading, the quiz, whatever came with it, the
 * button, and the way on.
 */
@Composable
fun CourseLessonScreen(
    source: LessonSource,
    course: Course,
    mod: CourseModule,
    lesson: CourseLesson,
    rungs: List<CourseRung>,
    read: Set<String>,
    answers: Set<String>,
    bottomPadding: Dp,
    onTick: (String) -> Unit,
    onMarkAndGo: (String, CourseRung?, CourseModule) -> Unit,
    onOpenLesson: (CourseRung) -> Unit,
    onAnswer: (String, Int, Int, Boolean, Boolean) -> Unit,
    onClearAnswers: (String) -> Unit,
    onOpenOriginal: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) = Gold {
    val c = LocalReiad.current
    val touch = rememberTouch()
    val id = remember(course, mod, lesson) {
        courseLessonId(course.slug, mod.slug, lesson.slug)
    }
    val at = rungs.indexOfFirst { it.id == id }
    val previous = rungs.getOrNull(at - 1).takeIf { at > 0 }
    val next = rungs.getOrNull(at + 1)
    /* The last lesson of a module goes to the module's summary
       rather than into the next module's first lesson. */
    val onward = next?.takeIf { it.module == mod.slug }
    val done = id in read

    LazyColumn(
        modifier.fillMaxSize().padding(horizontal = Gap.s8),
        contentPadding = PaddingValues(top = topClearance(), bottom = bottomPadding),
    ) {
        item("head") {
            Crumb(mod.title, onBack)
            Spacer(Modifier.height(Gap.s7))
            Eyebrow(
                listOf(course.title, mod.title, lesson.section)
                    .filter { it.isNotBlank() }
                    .joinToString(" · "),
            )
            Spacer(Modifier.height(Gap.s5))
            Text(lesson.title, style = headlineStyle(lesson.title), color = c.ink)
            Spacer(Modifier.height(Gap.s5))
            Text(
                "${courseKindWord(lesson.kind)} · lesson ${lesson.position} of " +
                    "${mod.lessons.size}",
                style = MaterialTheme.typography.labelMedium,
                color = c.inkSoft,
            )
            Spacer(Modifier.height(Gap.s8))
        }

        lesson.video?.let { drive ->
            item("video") {
                Video(source, drive, lesson.captions)
                Spacer(Modifier.height(Gap.s8))
            }
        }

        lesson.reading?.let { drive ->
            item("reading") {
                Reading(source, drive, "reading", onOpenOriginal)
                Spacer(Modifier.height(Gap.s8))
            }
        }

        /* A quiz and a challenge are the same file shape and
           neither is a reading: every option lives inside a
           `<form>`, which the sanitiser drops whole, contents and
           all. They go through the quiz route instead. */
        for ((kind, drive) in listOf("quiz" to lesson.quiz, "exam" to lesson.exam)) {
            if (drive == null) continue
            item("$kind:$drive") {
                Quiz(source, drive, kind, id, answers, onAnswer, onClearAnswers, onOpenOriginal)
                Spacer(Modifier.height(Gap.s8))
            }
        }

        if (lesson.empty) {
            item("nothing") {
                InfoCard(
                    title = "No file against this lesson",
                    dek = "The catalogue has this lesson but nothing behind it, which " +
                        "usually means the import has not reached it.",
                )
                Spacer(Modifier.height(Gap.s8))
            }
        }

        /* ---- what came with it ---- */

        val extras = buildList {
            lesson.transcript?.let { add(Triple("Transcript", "txt", it)) }
            for (file in lesson.files) add(Triple(file.name, file.ext, file.drive))
        }
        if (extras.isNotEmpty()) {
            item("files") {
                Eyebrow("FILES")
                Spacer(Modifier.height(Gap.s4))
                for ((name, ext, drive) in extras) {
                    Rung(onClick = { onOpenOriginal(driveUrl(drive)) }) {
                        Text(
                            name,
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.titleSmall,
                            color = c.ink,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            ext.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = c.inkSoft,
                        )
                    }
                }
                Spacer(Modifier.height(Gap.s8))
            }
        }

        /* ---- the button ----

           Two of them, and they do different things. "Mark
           complete and continue" only ever ADDS a tick, because
           it is pressed by somebody walking back through a module
           and a toggle there would untick the lesson they just
           re-read. The latch beside it is how a tick comes off. */
        item("actions") {
            PillButton(
                label = if (onward != null) {
                    "Mark complete & continue"
                } else {
                    "Mark complete & finish the module"
                },
                onClick = {
                    touch.tap()
                    onMarkAndGo(id, onward, mod)
                },
                kind = ButtonKind.SOLID,
                wide = true,
            )
            Spacer(Modifier.height(Gap.s5))
            PillButton(
                label = if (done) "Completed" else "Not completed",
                onClick = {
                    touch.latch(!done)
                    onTick(id)
                },
                kind = ButtonKind.SOFT,
                wide = true,
                pressed = done,
                icon = if (done) "check" else null,
            )
            Spacer(Modifier.height(Gap.s9))
        }

        item("prevnext") {
            previous?.let { row ->
                Rung(onClick = { onOpenLesson(row) }) {
                    Text("←", style = MaterialTheme.typography.labelLarge, color = c.accent)
                    Spacer(Modifier.width(Gap.s6))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "PREVIOUS",
                            style = MaterialTheme.typography.labelSmall,
                            color = c.inkSoft,
                        )
                        Text(
                            row.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = c.ink,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            next?.let { row ->
                Rung(onClick = { onOpenLesson(row) }) {
                    Column(Modifier.weight(1f)) {
                        Text("NEXT", style = MaterialTheme.typography.labelSmall, color = c.inkSoft)
                        Text(
                            row.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = c.ink,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.width(Gap.s6))
                    Text("→", style = MaterialTheme.typography.labelLarge, color = c.accent)
                }
            }
        }
    }
}

/* ---------- the three things a lesson can hold ---------- */

@Composable
private fun Video(source: LessonSource, drive: String, captions: String?) {
    val reel by produceState<Reel>(Reel.Waiting, drive, captions) {
        value = reelFor(source, drive, captions)
    }
    when (val now = reel) {
        Reel.Waiting -> Box(Modifier.fillMaxWidth()) { Skeleton(lines = 2, label = "Loading the video") }
        is Reel.Broke -> Problem(title = "That video could not be opened", detail = now.why)
        is Reel.Ready -> Reel(
            reel = now,
            renew = { reelFor(source, drive, captions) as? Reel.Ready },
        )
    }
}

/**
 * A saved page, rendered through this phone's own body parser.
 *
 * The Worker has already run it through the same sanitiser the
 * site's editor runs over an article, which drops script, style
 * and iframe outright. What arrives is words and structure, and
 * they go through `BodyParser` exactly like a lesson of a school
 * does, so a table in a Coursera reading sits on its own piece of
 * glass here for the same reason a table in a German lesson does.
 *
 * It reads plainly rather than beautifully, and that is the
 * honest outcome: this is somebody else's page without its
 * stylesheet, not a recreation of it.
 */
@Composable
private fun Reading(
    source: LessonSource,
    drive: String,
    kind: String,
    onOpenOriginal: (String) -> Unit,
) {
    val answer by produceState<Answer<uk.co.reiad.library.core.ReadingResponse>?>(null, drive) {
        value = source.reading(drive)
    }
    when (val now = answer) {
        null -> Skeleton(label = "Loading the reading")
        is Answer.Got -> {
            val body = remember(now.value.html) { BodyParser.parse(now.value.html) }
            Column {
                BodyView(body.blocks)
                Spacer(Modifier.height(Gap.s6))
                Original(kind, drive, onOpenOriginal)
            }
        }
        else -> Problem(title = "That page could not be opened", detail = now.problem)
    }
}

@Composable
private fun Quiz(
    source: LessonSource,
    drive: String,
    kind: String,
    lessonId: String,
    answers: Set<String>,
    onAnswer: (String, Int, Int, Boolean, Boolean) -> Unit,
    onClear: (String) -> Unit,
    onOpenOriginal: (String) -> Unit,
) {
    val answer by produceState<Answer<uk.co.reiad.library.core.QuizResponse>?>(null, drive) {
        value = source.quiz(drive)
    }
    val c = LocalReiad.current

    when (val now = answer) {
        null -> Skeleton(label = "Loading the ${courseKindWord(kind).lowercase()}")
        !is Answer.Got -> Problem(
            title = "That ${courseKindWord(kind).lowercase()} could not be opened",
            detail = now.problem,
        )
        else -> {
            val quiz = now.value
            /* Not a quiz after all, or one in a shape the parser
               does not know. Render it as a page rather than as
               nothing: unreadable is worse than plain. */
            if (!quiz.parsed) {
                val body = remember(quiz.html) { BodyParser.parse(quiz.html) }
                Column {
                    BodyView(body.blocks)
                    Spacer(Modifier.height(Gap.s6))
                    Original(kind, drive, onOpenOriginal)
                }
                return@Quiz
            }

            Column {
                for (question in quiz.questions) {
                    Question(question, lessonId, answers, onAnswer)
                    Spacer(Modifier.height(Gap.s8))
                }
                /* Said once, at the bottom, rather than beside
                   every question. A reader is owed the truth
                   about what this does and does not do, and the
                   honest version is short. */
                Text(
                    "Your answers save as you go, on this phone and to your account. " +
                        "This course's files carry no answer key, so nothing here is " +
                        "marked right or wrong.",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.inkSoft,
                )
                Spacer(Modifier.height(Gap.s6))
                PillButton("Clear my answers", { onClear(lessonId) }, kind = ButtonKind.QUIET)
                Spacer(Modifier.height(Gap.s6))
                Original(kind, drive, onOpenOriginal)
            }
        }
    }
}

/**
 * One question, with the inputs built HERE.
 *
 * None of Coursera's own markup reaches this screen: their inputs
 * were wired to their server and are meaningless off it, and
 * building our own is what makes an answer something this app can
 * keep.
 *
 * `multiple` is what makes a radio a radio: for a single-answer
 * question, picking one clears the rest of that question, so the
 * stored set can never say a reader picked two things where the
 * screen allowed one.
 */
@Composable
private fun Question(
    question: QuizQuestion,
    lessonId: String,
    answers: Set<String>,
    onAnswer: (String, Int, Int, Boolean, Boolean) -> Unit,
) {
    val c = LocalReiad.current
    val touch = rememberTouch()
    val prompt = remember(question.prompt) { BodyParser.parse(question.prompt) }

    Card {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "QUESTION ${question.n}",
                    style = MaterialTheme.typography.labelSmall,
                    color = c.accent,
                )
                if (question.multiple) {
                    Spacer(Modifier.width(Gap.s5))
                    Text(
                        "select all that apply",
                        style = MaterialTheme.typography.labelSmall,
                        color = c.inkSoft,
                    )
                }
            }
            Spacer(Modifier.height(Gap.s5))
            BodyView(prompt.blocks)
            Spacer(Modifier.height(Gap.s6))

            for ((option, text) in question.options.withIndex()) {
                val id = uk.co.reiad.library.core.courseAnswerId(lessonId, question.n, option)
                val on = id in answers
                Rung(
                    onClick = {
                        touch.latch(!on)
                        onAnswer(lessonId, question.n, option, !on, !question.multiple)
                    },
                ) {
                    Text(
                        if (on) "●" else "○",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (on) c.accent else c.inkSoft,
                        modifier = Modifier.width(Gap.s9),
                    )
                    Text(
                        text,
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.ink,
                    )
                }
            }
        }
    }
}

/** The quiet way back to the file this was built from. */
@Composable
private fun Original(kind: String, drive: String, onOpen: (String) -> Unit) {
    PillButton(
        "Open the original ${courseKindWord(kind).lowercase()} in Drive",
        { onOpen(driveUrl(drive)) },
        kind = ButtonKind.QUIET,
        icon = "link",
    )
}

/* ============================================================
   Which of the five, and everything it needs

   One dispatcher rather than five branches in `MainActivity`,
   for the reason the website's `start()` is one function: they
   are five views of one thing, and a reader moves between them
   constantly. The address decides which, exactly as it does in
   the browser.
   ============================================================ */

@Composable
fun CourseSection(
    at: CourseWhere,
    source: LessonSource?,
    shelf: Answer<List<ProgrammeSummary>>?,
    course: Answer<CourseResponse>?,
    read: Set<String>,
    answers: Set<String>,
    bookmark: Bookmark?,
    bottomPadding: Dp,
    /** Somewhere else in the section. */
    onGo: (CourseWhere) -> Unit,
    /** Out of the section altogether, which is where Back from
        the shelf goes. */
    onLeave: () -> Unit,
    /** A lesson was OPENED, which moves the bookmark and ticks
        nothing. Called from the screen rather than from the tap
        that got there, so a shared link moves it too. */
    onOpened: (Bookmark) -> Unit,
    onTick: (String) -> Unit,
    onMark: (String) -> Unit,
    onAnswer: (String, Int, Int, Boolean, Boolean) -> Unit,
    onClearAnswers: (String) -> Unit,
    onRetry: () -> Unit,
    onAccount: () -> Unit,
    /** Drive's own page, for "open the original". The only link
        in this section that leaves the app, and it leaves it for
        a file rather than for a page this app could have drawn. */
    onOpenOriginal: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    /** Where a rung of the ladder lives, as an address. */
    fun open(programme: String, rung: CourseRung) {
        onGo(CourseWhere.Lesson(programme, rung.course, rung.module, rung.lesson.slug))
    }

    when (at) {
        is CourseWhere.Shelf -> CourseShelfScreen(
            answer = shelf,
            read = read,
            bottomPadding = bottomPadding,
            onOpen = { onGo(CourseWhere.Programme(it.slug)) },
            onRetry = onRetry,
            onAccount = onAccount,
            modifier = modifier,
        )

        is CourseWhere.Programme -> {
            val programmes = (shelf as? Answer.Got)?.value
            val one = programmes?.firstOrNull { it.slug == at.programme }

            /* AN ADDRESS FROM BEFORE THE PROGRAMME SEGMENT.
               `/skills/courses/<course>` was live until a
               certificate got a segment of its own, so every
               course bookmark anybody holds is this shape. The
               shelf names every course under its programme and
               has already arrived, so this is answered rather
               than refused, and with no second request. */
            val holder = programmes?.firstOrNull { row ->
                row.courses.any { it.slug == at.programme }
            }
            LaunchedEffect(one, holder) {
                if (one == null && holder != null) {
                    onGo(CourseWhere.One(holder.slug, at.programme))
                }
            }

            when {
                one != null -> CourseProgrammeScreen(
                    programme = one,
                    read = read,
                    bottomPadding = bottomPadding,
                    onOpen = { onGo(CourseWhere.One(one.slug, it.slug)) },
                    onBack = { onGo(CourseWhere.Shelf) },
                    modifier = modifier,
                )

                holder != null -> Waiting(bottomPadding, "Finding that course", modifier)

                shelf == null -> Waiting(bottomPadding, "Reading the catalogue", modifier)

                shelf !is Answer.Got -> Refusal(shelf, bottomPadding, onRetry, onAccount, modifier)

                else -> Nothing(
                    title = "No such programme",
                    detail = "Nothing in this catalogue is called “${at.programme}”.",
                    bottomPadding = bottomPadding,
                    onBack = { onGo(CourseWhere.Shelf) },
                    modifier = modifier,
                )
            }
        }

        is CourseWhere.One -> WithCourse(course, bottomPadding, onRetry, onAccount, modifier) { got ->
            CourseScreen(
                programme = at.programme,
                holder = got.programme,
                course = got.course,
                read = read,
                bookmark = bookmark,
                bottomPadding = bottomPadding,
                onOpenLesson = { open(at.programme, it) },
                onOpenModule = {
                    onGo(CourseWhere.Module(at.programme, got.course.slug, it.slug))
                },
                onBack = { onGo(CourseWhere.Programme(at.programme)) },
                modifier = modifier,
            )
        }

        is CourseWhere.Module -> WithCourse(course, bottomPadding, onRetry, onAccount, modifier) { got ->
            val mod = got.course.modules.firstOrNull { it.slug == at.module }
            if (mod == null) {
                Nothing(
                    title = "No such module",
                    detail = "${got.course.title} has no module called “${at.module}”.",
                    bottomPadding = bottomPadding,
                    onBack = { onGo(CourseWhere.One(at.programme, at.course)) },
                    modifier = modifier,
                )
            } else {
                CourseModuleScreen(
                    course = got.course,
                    mod = mod,
                    rungs = remember(at.programme, got.course) {
                        laddered(at.programme, got.course)
                    },
                    read = read,
                    bottomPadding = bottomPadding,
                    onOpenLesson = { open(at.programme, it) },
                    onOpenModule = {
                        onGo(CourseWhere.Module(at.programme, got.course.slug, it.slug))
                    },
                    onBack = { onGo(CourseWhere.One(at.programme, at.course)) },
                    modifier = modifier,
                )
            }
        }

        is CourseWhere.Lesson -> WithCourse(course, bottomPadding, onRetry, onAccount, modifier) { got ->
            val mod = got.course.modules.firstOrNull { it.slug == at.module }
            val lesson = mod?.lessons?.firstOrNull { it.slug == at.lesson }
            val rungs = remember(at.programme, got.course) { laddered(at.programme, got.course) }

            when {
                mod == null -> Nothing(
                    title = "No such module",
                    detail = "${got.course.title} has no module called “${at.module}”.",
                    bottomPadding = bottomPadding,
                    onBack = { onGo(CourseWhere.One(at.programme, at.course)) },
                    modifier = modifier,
                )

                lesson == null -> Nothing(
                    title = "No such lesson",
                    detail = "${mod.title} has no lesson called “${at.lesson}”.",
                    bottomPadding = bottomPadding,
                    onBack = {
                        onGo(CourseWhere.Module(at.programme, at.course, at.module))
                    },
                    modifier = modifier,
                )

                source == null -> Nothing(
                    title = "Signed out",
                    detail = "This section is behind the admin check, and there is no " +
                        "session on this phone to ask with.",
                    bottomPadding = bottomPadding,
                    onBack = onAccount,
                    backLabel = "Go to the account",
                    modifier = modifier,
                )

                else -> {
                    val id = courseLessonId(got.course.slug, mod.slug, lesson.slug)

                    /* Opening moves the bookmark and ticks
                       nothing. Here rather than on the tap that
                       got here, so a shared link moves it too. */
                    LaunchedEffect(id) {
                        onOpened(
                            Bookmark(
                                id = id,
                                title = lesson.title,
                                url = uk.co.reiad.library.core.courseLessonPath(
                                    at.programme, got.course.slug, mod.slug, lesson.slug,
                                ),
                            ),
                        )
                    }

                    CourseLessonScreen(
                        source = source,
                        course = got.course,
                        mod = mod,
                        lesson = lesson,
                        rungs = rungs,
                        read = read,
                        answers = answers,
                        bottomPadding = bottomPadding,
                        onTick = onTick,
                        onMarkAndGo = { lessonId, onward, module ->
                            onMark(lessonId)
                            if (onward != null) open(at.programme, onward)
                            else onGo(
                                CourseWhere.Module(at.programme, got.course.slug, module.slug),
                            )
                        },
                        onOpenLesson = { open(at.programme, it) },
                        onAnswer = onAnswer,
                        onClearAnswers = onClearAnswers,
                        onOpenOriginal = onOpenOriginal,
                        onBack = {
                            onGo(CourseWhere.Module(at.programme, got.course.slug, mod.slug))
                        },
                        modifier = modifier,
                    )
                }
            }
        }
    }

    /* Back out of the section from the shelf, and up one level of
       the address from everywhere else. The crumb at the top of
       each screen says the same thing, which is the point: a back
       gesture and the visible way back must not disagree. */
    androidx.activity.compose.BackHandler {
        when (at) {
            is CourseWhere.Shelf -> onLeave()
            is CourseWhere.Programme -> onGo(CourseWhere.Shelf)
            is CourseWhere.One -> onGo(CourseWhere.Programme(at.programme))
            is CourseWhere.Module -> onGo(CourseWhere.One(at.programme, at.course))
            is CourseWhere.Lesson ->
                onGo(CourseWhere.Module(at.programme, at.course, at.module))
        }
    }
}

/** The three states a course fetch has, so the three screens
    below it do not each write them out. */
@Composable
private fun WithCourse(
    answer: Answer<CourseResponse>?,
    bottomPadding: Dp,
    onRetry: () -> Unit,
    onAccount: () -> Unit,
    modifier: Modifier,
    content: @Composable (CourseResponse) -> Unit,
) {
    when {
        answer == null -> Waiting(bottomPadding, "Reading the course", modifier)
        answer is Answer.Got -> content(answer.value)
        else -> Refusal(answer, bottomPadding, onRetry, onAccount, modifier)
    }
}

@Composable
private fun Waiting(bottomPadding: Dp, label: String, modifier: Modifier = Modifier) = Gold {
    Column(
        modifier
            .fillMaxSize()
            .padding(horizontal = Gap.s8)
            .padding(top = topClearance(), bottom = bottomPadding),
    ) {
        Skeleton(label = label)
    }
}

@Composable
private fun Refusal(
    answer: Answer<*>,
    bottomPadding: Dp,
    onRetry: () -> Unit,
    onAccount: () -> Unit,
    modifier: Modifier = Modifier,
) = Gold {
    Column(
        modifier
            .fillMaxSize()
            .padding(horizontal = Gap.s8)
            .padding(top = topClearance(), bottom = bottomPadding),
    ) {
        Refused(answer, onRetry, onAccount)
    }
}

/** An address in this section that names nothing in it.

    Said plainly, with the way back, rather than by drawing the
    nearest thing that does exist: a bookmark to a lesson that has
    been re-imported under another slug should say so, not open
    somebody else's lesson. */
@Composable
private fun Nothing(
    title: String,
    detail: String,
    bottomPadding: Dp,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    backLabel: String = "Back",
) = Gold {
    Column(
        modifier
            .fillMaxSize()
            .padding(horizontal = Gap.s8)
            .padding(top = topClearance(), bottom = bottomPadding),
    ) {
        Problem(title = title, detail = detail, retryLabel = backLabel, onRetry = onBack)
    }
}
