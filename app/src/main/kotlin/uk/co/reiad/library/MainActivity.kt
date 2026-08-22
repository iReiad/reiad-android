package uk.co.reiad.library

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import uk.co.reiad.library.core.Accent
import uk.co.reiad.library.core.Accents
import uk.co.reiad.library.core.BodyParser
import uk.co.reiad.library.core.LadderSchool
import uk.co.reiad.library.core.Lesson
import uk.co.reiad.library.core.LessonPage
import uk.co.reiad.library.core.NavGroup
import uk.co.reiad.library.core.Piece
import uk.co.reiad.library.core.School
import uk.co.reiad.library.core.SiteManifest
import uk.co.reiad.library.core.Prefs
import uk.co.reiad.library.core.Stage
import uk.co.reiad.library.core.Theme
import uk.co.reiad.library.core.bengaliNumber
import uk.co.reiad.library.core.checkpointBases
import uk.co.reiad.library.core.checkpointCount
import uk.co.reiad.library.core.lessonId
import uk.co.reiad.library.data.Reiad
import uk.co.reiad.library.ui.AccentRail
import uk.co.reiad.library.ui.BodyView
import uk.co.reiad.library.ui.Faces
import uk.co.reiad.library.ui.Checkpoints
import uk.co.reiad.library.ui.Chip
import uk.co.reiad.library.ui.Control
import uk.co.reiad.library.ui.Corner
import uk.co.reiad.library.ui.Gap
import uk.co.reiad.library.ui.GoCard
import uk.co.reiad.library.ui.BanglaBody
import uk.co.reiad.library.ui.Groove
import uk.co.reiad.library.ui.Icon
import uk.co.reiad.library.ui.GroupScreen
import uk.co.reiad.library.ui.isBangla
import uk.co.reiad.library.ui.openOnSite
import uk.co.reiad.library.ui.accentOf as tokenAccent
import uk.co.reiad.library.ui.InfoCard
import uk.co.reiad.library.ui.LocalReiad
import uk.co.reiad.library.ui.Pane
import uk.co.reiad.library.ui.PieceScreen
import uk.co.reiad.library.ui.Plate
import uk.co.reiad.library.ui.ReadingHub
import uk.co.reiad.library.ui.ResumeCard
import uk.co.reiad.library.ui.StageState
import uk.co.reiad.library.ui.WorkbookScreen
import uk.co.reiad.library.ui.SchoolHead
import uk.co.reiad.library.ui.ReiadTheme
import uk.co.reiad.library.ui.Rung
import uk.co.reiad.library.ui.SearchScreen
import uk.co.reiad.library.ui.SettingsSheet
import uk.co.reiad.library.ui.Shell
import uk.co.reiad.library.ui.ShellState
import uk.co.reiad.library.ui.Sway
import uk.co.reiad.library.ui.rememberSway

/* ============================================================
   The four schools, on a handset.

   **The list of schools is the site's, not this app's.** The home
   screen draws whatever `/api/site` says has a ladder, with the
   colour the site gives it. Add a fifth school to the one nav
   table on the site and it appears here, in its own colour, with
   no app release. That is the promise `ANDROID.md` makes and
   `check-app-surface.ts` enforces from the other end, and drawing
   the home screen from a hardcoded list would have quietly broken
   it on day one.

   **Opening is not finishing, and each school means it its own
   way.** The money school's tick is a button the reader presses.
   The other three mark a lesson when it is opened, which is what
   `recordVisit` has always done on the site. Same store, same
   keys, different verb, and the difference is stated here rather
   than averaged away.
   ============================================================ */

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { App() }
    }
}

/** How far above the bottom a page has to stop.

    The bar is a floating pill rather than a bar welded to the
    edge, which is the site's own arrangement one level down, so
    nothing insets the content for it and every screen has to
    leave the room itself. */
private val BAR_CLEARANCE = 96.dp

/* ---------- where the reader is ---------- */

private sealed interface Where {
    data object Home : Where
    data class Group(val group: NavGroup) : Where

    /** A reading hub. `section` is `insights`, `cooking` or
        `travel`, which is the value in the one column that says
        where a piece lives. */
    data class Hub(val section: String, val title: String) : Where
    data class Reading2(val section: String, val piece: Piece) : Where

    /** A practice book. One page, returned to thirty times. */
    data class Book(val school: LadderSchool, val stage: Stage) : Where
    data class Ladder(val school: LadderSchool) : Where
    data class Reading(val school: LadderSchool, val stage: Stage, val lesson: Lesson) : Where
}

private class AppModel(private val reiad: Reiad) : ViewModel() {

    private val _site = MutableStateFlow<SiteManifest?>(null)
    val site: StateFlow<SiteManifest?> = _site.asStateFlow()

    private val _stale = MutableStateFlow(false)
    val stale: StateFlow<Boolean> = _stale.asStateFlow()

    private val _note = MutableStateFlow<String?>(null)
    val note: StateFlow<String?> = _note.asStateFlow()

    private val _stages = MutableStateFlow<List<Stage>>(emptyList())
    val stages: StateFlow<List<Stage>> = _stages.asStateFlow()

    private val _page = MutableStateFlow<LessonPage?>(null)
    val page: StateFlow<LessonPage?> = _page.asStateFlow()

    private val _ticks = MutableStateFlow<Map<String, Set<String>>>(emptyMap())
    val ticks: StateFlow<Map<String, Set<String>>> = _ticks.asStateFlow()

    init {
        viewModelScope.launch {
            val answer = reiad.manifest()
            _site.value = answer.value
            _stale.value = answer.stale
            if (answer.value == null) {
                _note.value = "Could not reach the site, and nothing is saved yet."
            }
            for (school in School.entries) {
                _ticks.value = _ticks.value + (school.id to reiad.ticksNow(school))
            }
        }
    }

    fun openLadder(school: LadderSchool) {
        _stages.value = emptyList()
        viewModelScope.launch {
            val answer = reiad.ladder(school.key)
            _stages.value = answer.value?.stages.orEmpty()
            _stale.value = answer.stale
            if (answer.value == null) _note.value = "That school is not saved on this device yet."
        }
    }

    /** Opening a lesson marks it read in every school but the
        money one, which is the site's own rule rather than a
        simplification. */
    fun openLesson(school: LadderSchool, stage: Stage, lesson: Lesson) {
        _page.value = null
        viewModelScope.launch {
            val answer = reiad.lesson(school.key, stage.slug, lesson.slug)
            _page.value = answer.value?.lesson
            _stale.value = answer.stale

            val which = School.of(school.key)
            if (which != null && which != School.MONEY && lesson.isWritten) {
                val after = reiad.markRead(which, lessonId(stage.slug, lesson.slug))
                _ticks.value = _ticks.value + (which.id to after)
            }
        }
    }

    fun tick(school: LadderSchool, stage: Stage, lesson: Lesson) {
        val which = School.of(school.key) ?: return
        viewModelScope.launch {
            val after = reiad.toggleTick(which, lessonId(stage.slug, lesson.slug))
            _ticks.value = _ticks.value + (which.id to after)
        }
    }

    fun ticksOf(key: String): Set<String> = _ticks.value[key].orEmpty()

    /* ---------- checkpoints and the bookmark ---------- */

    private val _checks = MutableStateFlow<Map<String, Set<String>>>(emptyMap())
    val checks: StateFlow<Map<String, Set<String>>> = _checks.asStateFlow()

    private val _bookmarks = MutableStateFlow<Map<String, String>>(emptyMap())
    val bookmarks: StateFlow<Map<String, String>> = _bookmarks.asStateFlow()

    fun loadMarks() {
        viewModelScope.launch {
            val checks = mutableMapOf<String, Set<String>>()
            val marks = mutableMapOf<String, String>()
            for (school in School.entries) {
                checks[school.id] = reiad.checkpoints(school).first()
                reiad.bookmark(school).first()?.let { marks[school.id] = it }
            }
            _checks.value = checks
            _bookmarks.value = marks
        }
    }

    fun toggleCheck(school: School, id: String) {
        viewModelScope.launch {
            val after = reiad.toggleCheckpoint(school, id)
            _checks.value = _checks.value + (school.id to after)
        }
    }

    /** A visit moves the bookmark and ticks nothing.

        Opening is not finishing, and the two verbs are separate
        everywhere in this app because they are separate on the
        site: the money school's tick is a button, and the other
        three mark a lesson as READ on opening, which is a
        different fact from where somebody last was. */
    fun visited(school: School, lessonId: String) {
        viewModelScope.launch {
            reiad.remember(school, lessonId)
            _bookmarks.value = _bookmarks.value + (school.id to lessonId)
        }
    }

    /* ---------- the practice books ---------- */

    private val _book = MutableStateFlow<uk.co.reiad.library.core.Book?>(null)
    val book: StateFlow<uk.co.reiad.library.core.Book?> = _book.asStateFlow()

    /** Whether the book asked for could not be read at all.

        Distinct from "not arrived yet", and it has to be: a
        screen that cannot tell them apart shows a spinner for
        ever, which is the worst of the three things it could
        say. */
    private val _bookFailed = MutableStateFlow(false)
    val bookFailed: StateFlow<Boolean> = _bookFailed.asStateFlow()

    private val _days = MutableStateFlow<Map<String, Set<String>>>(emptyMap())
    val days: StateFlow<Map<String, Set<String>>> = _days.asStateFlow()

    private val _written = MutableStateFlow<Map<String, String>>(emptyMap())
    val written: StateFlow<Map<String, String>> = _written.asStateFlow()

    /** The answers a reader has asked to see, this session only.

        NOT stored. The key is behind a button on the web and it
        is behind a button here, and a key kept on disk beside the
        prompts undoes the reason the endpoint splits them at all.
        Coming back tomorrow means pressing Show again, which is
        what pressing Show means. */
    private val _answers = MutableStateFlow<Map<Int, List<String>>>(emptyMap())
    val answers: StateFlow<Map<Int, List<String>>> = _answers.asStateFlow()

    fun openBook(school: LadderSchool, stage: Stage) {
        _book.value = null
        _bookFailed.value = false
        _answers.value = emptyMap()
        val which = School.of(school.key) ?: return
        viewModelScope.launch {
            val answer = reiad.book(stage.slug)
            _book.value = answer.value?.book
            _bookFailed.value = answer.value == null
            if (answer.stale) _stale.value = true
            _days.value = _days.value + (which.id to reiad.days(which).first())
            _written.value = reiad.writing(which).first()
        }
    }

    fun write(school: School, slot: String, text: String) {
        viewModelScope.launch {
            reiad.write(school, slot, text)
            _written.value = if (text.isBlank()) _written.value - slot
            else _written.value + (slot to text)
        }
    }

    fun tickDay(school: School, id: String) {
        viewModelScope.launch {
            _days.value = _days.value + (school.id to reiad.toggleDay(school, id))
        }
    }

    fun reveal(stage: String, day: Int) {
        viewModelScope.launch {
            val key = reiad.bookKey(stage, day)
            _answers.value = _answers.value + (day to key)
        }
    }

    /* ---------- the pieces ---------- */

    private val _pieces = MutableStateFlow<List<Piece>>(emptyList())
    val pieces: StateFlow<List<Piece>> = _pieces.asStateFlow()

    private val _open = MutableStateFlow<Piece?>(null)
    val open: StateFlow<Piece?> = _open.asStateFlow()

    fun loadPieces() {
        if (_pieces.value.isNotEmpty()) return
        viewModelScope.launch {
            val answer = reiad.pieces()
            answer.value?.let { _pieces.value = it.articles }
            if (answer.stale) _stale.value = true
        }
    }

    /** Opens a piece by slug, with the LIST's copy shown first.

        The list answer carries everything but the body, so the
        title, the byline and the topics can be on screen while
        the body is still arriving. A blank screen with a spinner
        where the app already holds the title is a wait somebody
        did not need to have. */
    fun openPiece(piece: Piece) {
        _open.value = piece
        viewModelScope.launch {
            val answer = reiad.piece(piece.slug)
            answer.value?.article?.let { full ->
                if (_open.value?.slug == full.slug) _open.value = full
            }
        }
    }

    fun closePiece() {
        _open.value = null
    }

    /* ---------- what the reader chose ---------- */

    val prefs: StateFlow<Prefs> = reiad.prefs
        .stateIn(viewModelScope, SharingStarted.Eagerly, Prefs())

    val audience: StateFlow<String?> = reiad.audience
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun changePrefs(change: (Prefs) -> Prefs) {
        viewModelScope.launch { reiad.savePrefs(change) }
    }

    fun chooseAudience(id: String) {
        viewModelScope.launch { reiad.setAudience(id) }
    }
}

/* ---------- the app ---------- */

@Composable
fun App() {
    val context = LocalContext.current
    val reiad = remember { Reiad(context.applicationContext) }
    val model = remember { AppModel(reiad) }

    var where by remember { mutableStateOf<Where>(Where.Home) }
    var drawer by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    var settings by remember { mutableStateOf(false) }

    val site by model.site.collectAsState()
    val stages by model.stages.collectAsState()
    val page by model.page.collectAsState()
    val ticks by model.ticks.collectAsState()
    val stale by model.stale.collectAsState()
    val note by model.note.collectAsState()
    val prefs by model.prefs.collectAsState()
    val audience by model.audience.collectAsState()
    val pieces by model.pieces.collectAsState()
    val openPiece by model.open.collectAsState()
    val checks by model.checks.collectAsState()
    val bookmarks by model.bookmarks.collectAsState()
    val book by model.book.collectAsState()
    val bookFailed by model.bookFailed.collectAsState()
    val bookDays by model.days.collectAsState()
    val written by model.written.collectAsState()
    val answers by model.answers.collectAsState()

    /* The pieces are fetched once, on first composition, rather
       than when a reading hub opens: the list is six rows without
       bodies and having it already means search and the hubs are
       instant. */
    LaunchedEffect(Unit) {
        model.loadPieces()
        model.loadMarks()
    }

    val accent: Accent = when (val here = where) {
        is Where.Book -> accentOf(here.school)
        is Where.Ladder -> accentOf(here.school)
        is Where.Reading -> accentOf(here.school)
        is Where.Hub -> tokenAccent(site?.accents?.get(here.section))
        is Where.Reading2 -> tokenAccent(site?.accents?.get(here.section))
        is Where.Group -> accentOfGroup(here.group)
        Where.Home -> Accents.GREEN
    }

    /* The reader's choice wins over the system's, and "system" is
       a real third answer rather than the absence of one: it is
       what a reader who has never opened the settings has, and it
       has to keep following the system afterwards. */
    val dark = when (prefs.themeChoice) {
        Theme.LIGHT -> false
        Theme.DARK -> true
        Theme.SYSTEM -> isSystemInDarkTheme()
    }

    /** Where the reader is, in the site's own vocabulary, so the
        rail and the bar can mark it. */
    val current = when (val here = where) {
        is Where.Book -> here.school.key
        is Where.Ladder -> here.school.key
        is Where.Reading -> here.school.key
        is Where.Hub -> here.section
        is Where.Reading2 -> here.section
        is Where.Group -> here.group.items.firstOrNull()?.key
        Where.Home -> null
    }

    ReiadTheme(accent = accent, dark = dark) {
        val colours = LocalReiad.current
        Surface(Modifier.fillMaxSize(), color = colours.paper) {
            Shell(
                state = ShellState(site, current, audience, drawer),
                /* A tab opens its GROUP, not its first item.

                   Sending each tab to the first thing in it looked
                   fine for the learning group, whose first item is
                   a school this app opens, and made the other four
                   tabs land on Home: four targets that appear to
                   do nothing, which is worse than not having them.
                   A group screen lists what is in the group, and
                   every row opens: here if this app can, on the
                   site in the reader's own browser if it cannot. */
                onHome = { where = Where.Home },
                onGroup = { group -> where = Where.Group(group) },
                onItem = { item ->
                    val school = site?.ladders?.firstOrNull { it.key == item.key }
                    if (school != null) {
                        model.openLadder(school)
                        where = Where.Ladder(school)
                    }
                },
                onDrawer = { drawer = it },
                onSearch = { searching = true },
                onSettings = { settings = true },
                onAudience = { model.chooseAudience(it) },
            ) {
            when (val here = where) {
                is Where.Hub -> {
                    BackHandler { where = Where.Home }
                    ReadingHub(
                        title = here.title,
                        pieces = pieces.filter { it.section == here.section },
                        stale = stale,
                        bottomPadding = BAR_CLEARANCE,
                        onOpen = { piece ->
                            model.openPiece(piece)
                            where = Where.Reading2(here.section, piece)
                        },
                    )
                }

                is Where.Reading2 -> {
                    BackHandler {
                        model.closePiece()
                        where = Where.Hub(here.section, sectionTitle(site, here.section))
                    }
                    val shown = openPiece ?: here.piece
                    val siblings = pieces.filter { it.section == here.section }
                    val at = siblings.indexOfFirst { it.slug == shown.slug }
                    PieceScreen(
                        piece = shown,
                        previous = siblings.getOrNull(at - 1),
                        next = siblings.getOrNull(at + 1),
                        stale = stale,
                        bottomPadding = BAR_CLEARANCE,
                        onOpen = { piece ->
                            model.openPiece(piece)
                            where = Where.Reading2(here.section, piece)
                        },
                        onBack = {
                            model.closePiece()
                            where = Where.Hub(here.section, sectionTitle(site, here.section))
                        },
                    )
                }

                is Where.Book -> {
                    BackHandler { where = Where.Ladder(here.school) }
                    val which = School.of(here.school.key)
                    WorkbookScreen(
                        stage = here.stage.slug,
                        stageName = here.stage.bn,
                        school = which ?: School.DEUTSCH,
                        book = book,
                        failed = bookFailed,
                        onOpenOnSite = {
                            openOnSite(
                                context,
                                "/${here.school.key}/${here.stage.slug}/${here.stage.workbook?.slug ?: "arbeitsbuch"}",
                                colours,
                            )
                        },
                        days = which?.let { bookDays[it.id] }.orEmpty(),
                        written = written,
                        answers = answers,
                        bottomPadding = BAR_CLEARANCE,
                        onWrite = { slot, text -> which?.let { model.write(it, slot, text) } },
                        onTickDay = { id -> which?.let { model.tickDay(it, id) } },
                        onReveal = { day -> model.reveal(here.stage.slug, day) },
                        onBack = { where = Where.Ladder(here.school) },
                    )
                }

                is Where.Group -> {
                    BackHandler { where = Where.Home }
                    GroupScreen(
                        group = here.group,
                        accents = site?.accents.orEmpty(),
                        bottomPadding = BAR_CLEARANCE,
                        canOpenHere = { item ->
                            site?.ladders?.any { it.key == item.key } == true ||
                                readingSection(site, item.key) != null
                        },
                        onOpenHere = { item ->
                            val school = site?.ladders?.firstOrNull { it.key == item.key }
                            val section = readingSection(site, item.key)
                            when {
                                school != null -> {
                                    model.openLadder(school)
                                    where = Where.Ladder(school)
                                }
                                section != null ->
                                    where = Where.Hub(section, sectionTitle(site, section))
                            }
                        },
                    )
                }

                Where.Home -> Home(
                    site = site,
                    stale = stale,
                    note = note,
                    ticks = ticks,
                    onOpen = { school ->
                        model.openLadder(school)
                        where = Where.Ladder(school)
                    },
                )

                is Where.Ladder -> {
                    BackHandler { where = Where.Home }
                    Ladder(
                        school = here.school,
                        stages = stages,
                        ticks = ticks[here.school.key].orEmpty(),
                        stale = stale,
                        bookmark = bookmarks[here.school.key],
                        onBack = { where = Where.Home },
                        onOpen = { stage, lesson ->
                            model.openLesson(here.school, stage, lesson)
                            where = Where.Reading(here.school, stage, lesson)
                        },
                        onOpenBook = { stage ->
                            model.openBook(here.school, stage)
                            where = Where.Book(here.school, stage)
                        },
                    )
                }

                is Where.Reading -> {
                    BackHandler { where = Where.Ladder(here.school) }
                    val id = lessonId(here.stage.slug, here.lesson.slug)
                    val which = School.of(here.school.key)
                    /* A visit moves the bookmark and ticks
                       nothing. Keyed on the lesson so coming back
                       to the same one does not write again. */
                    LaunchedEffect(id, which) {
                        if (which != null) model.visited(which, id)
                    }
                    Reading(
                        school = here.school,
                        stage = here.stage,
                        lesson = here.lesson,
                        page = page,
                        ticked = id in ticks[here.school.key].orEmpty(),
                        isMoney = here.school.key == School.MONEY.id,
                        onBack = { where = Where.Ladder(here.school) },
                        onTick = { model.tick(here.school, here.stage, here.lesson) },
                        checks = which?.let { checks[it.id].orEmpty() }.orEmpty(),
                        onCheck = { mark -> which?.let { model.toggleCheck(it, mark) } },
                        lessonKey = id,
                    )
                }
            }
            }

            if (searching) {
                SearchScreen(
                    site = site,
                    pieces = pieces,
                    onOpen = { found ->
                        searching = false
                        /* A result opens the thing it names. By
                           ADDRESS first, because that is what a
                           result is: a piece and a school can
                           both belong to the same group, and
                           matching on the group alone sent every
                           travel piece to the travel hub. */
                        val piece = pieces.firstOrNull { it.url == found.url }
                        val school = site?.ladders?.firstOrNull { it.key == found.group }
                        val section = readingSection(site, found.group)
                        when {
                            piece != null -> {
                                model.openPiece(piece)
                                where = Where.Reading2(piece.section, piece)
                            }
                            school != null -> {
                                model.openLadder(school)
                                where = Where.Ladder(school)
                            }
                            section != null ->
                                where = Where.Hub(section, sectionTitle(site, section))
                            /* Everything else lives on the site
                               and not here yet, so it opens
                               there rather than doing nothing. */
                            else -> openOnSite(context, found.url, colours)
                        }
                    },
                    onClose = { searching = false },
                )
            }

            if (settings) {
                SettingsSheet(
                    prefs = prefs,
                    onChange = { change -> model.changePrefs(change) },
                    onClose = { settings = false },
                )
            }
        }
    }
}

/** The colour the SITE says this school owns, falling back to the
    computed table only when the manifest has never arrived. */
private fun accentOf(school: LadderSchool): Accent =
    Accents.byToken(school.accent) ?: Accents.BY_KEY[school.key] ?: Accents.GREEN

private fun accentOfGroup(group: NavGroup): Accent = tokenAccent(group.accent)

/** Which reading section a nav key is, or null.

    Asked of the MANIFEST rather than of a list here, so a fourth
    reading section added to the site turns up with no release.
    `sections` is the site's own table of them, and its `id` is
    the same string a piece carries in its `section` column. */
private fun readingSection(site: SiteManifest?, key: String?): String? =
    site?.sections?.firstOrNull { it.id == key }?.id

private fun sectionTitle(site: SiteManifest?, section: String): String =
    site?.sections?.firstOrNull { it.id == section }
        ?.let { it.bn.ifBlank { it.en } }
        ?: section.replaceFirstChar { it.uppercase() }

/* ---------- home ---------- */

@Composable
private fun Home(
    site: SiteManifest?,
    stale: Boolean,
    note: String?,
    ticks: Map<String, Set<String>>,
    onOpen: (LadderSchool) -> Unit,
) {
    val c = LocalReiad.current
    /* One sway for the whole screen, not one per card. Every card
       on a page leans by the same amount, because they are all on
       the same handset: a lean per card would be twelve sensor
       listeners answering one movement. */
    val sway = rememberSway()
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = Gap.s8),
        /* The bar FLOATS over the page rather than pushing it, so
           the page has to end above it or the last card sits under
           the bar and looks like the list has been cut off. */
        contentPadding = PaddingValues(top = Gap.s11, bottom = BAR_CLEARANCE),
    ) {
        item {
            Text(
                site?.site?.name ?: "Reiad's Library",
                style = MaterialTheme.typography.displaySmall,
                color = c.ink,
            )
            Text(
                site?.site?.tagline ?: "Finance and Bangladesh markets",
                style = MaterialTheme.typography.bodyMedium,
                color = c.inkSoft,
            )
            if (stale) {
                Spacer(Modifier.height(Gap.s6))
                Chip("SAVED COPY")
            }
            Spacer(Modifier.height(Gap.s9))
        }

        if (note != null && site == null) {
            item {
                /* An InfoCard, deliberately: this is the end of
                   the road. It reports what happened and there is
                   nothing to press, so it gets no rail, no arrow
                   and no light. */
                InfoCard(title = "Nothing saved yet", dek = note)
            }
        }

        if (site == null && note == null) {
            item { Text("Reading the site…", color = c.inkSoft) }
        }

        items(site?.ladders.orEmpty()) { school ->
            SchoolCard(school, ticks[school.key].orEmpty().size, sway, onOpen)
            Spacer(Modifier.height(Gap.s7))
        }

        site?.counts?.let { counts ->
            item {
                Spacer(Modifier.height(Gap.s7))
                Plate {
                    Text("WHAT IS HERE", style = MaterialTheme.typography.labelSmall, color = c.accent)
                    Spacer(Modifier.height(Gap.s4))
                    Text(
                        listOfNotNull(
                            counts["lessons"]?.let { "$it lessons" },
                            counts["calculators"]?.let { "$it calculators" },
                            counts["caseStudies"]?.let { "$it case studies" },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.inkSoft,
                    )
                }
            }
        }
    }
}

/** Each school in its own colour, which is the site's rule that a
    page wears its section's colour, one level up.

    A `GoCard` rather than a card: pressing it takes you into a
    ladder, so it gets the rail, the arrow, the action written out
    and the light. What a school card is NOT is an `InfoCard`, and
    the two being separate components is what makes that a fact
    rather than an intention. */
@Composable
private fun SchoolCard(
    school: LadderSchool,
    done: Int,
    sway: Sway,
    onOpen: (LadderSchool) -> Unit,
) {
    ReiadTheme(accent = accentOf(school), dark = LocalReiad.current.isDark) {
        GoCard(
            title = school.bn,
            dek = school.blurb.takeIf { it.isNotBlank() },
            chip = school.en,
            go = "পড়া শুরু",
            done = done > 0,
            sway = sway,
            onOpen = { onOpen(school) },
        )
    }
}

/* ---------- a ladder ---------- */

@Composable
private fun Ladder(
    school: LadderSchool,
    stages: List<Stage>,
    ticks: Set<String>,
    stale: Boolean,
    onBack: () -> Unit,
    onOpen: (Stage, Lesson) -> Unit,
    onOpenBook: (Stage) -> Unit,
    bookmark: String? = null,
) {
    val c = LocalReiad.current
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = Gap.s8),
        contentPadding = PaddingValues(top = Gap.s11, bottom = BAR_CLEARANCE),
    ) {
        item {
            Text(
                "← Home",
                style = MaterialTheme.typography.labelLarge,
                color = c.accent,
                modifier = Modifier.clickable { onBack() },
            )
            Spacer(Modifier.height(Gap.s7))

            /* Lessons, and only lessons. A ring that counted
               checkpoints or practice-book days would still look
               plausible, which is what makes it the one way to
               get this wrong. */
            val lessons = stages.sumOf { it.lessons.size }
            val read = stages.sumOf { stage ->
                stage.lessons.count { lessonId(stage.slug, it.slug) in ticks }
            }
            SchoolHead(school.bn, school.en, read, lessons)

            if (stale) {
                Spacer(Modifier.height(Gap.s5))
                Chip("SAVED COPY")
            }

            /* Where they were, not where they got to. */
            val at = bookmark?.let { id ->
                stages.firstNotNullOfOrNull { stage ->
                    stage.lessons.firstOrNull { lessonId(stage.slug, it.slug) == id }
                        ?.let { stage to it }
                }
            }
            if (at != null) {
                Spacer(Modifier.height(Gap.s8))
                ResumeCard(at.first, at.second, onOpen)
            }

            Spacer(Modifier.height(Gap.s9))
        }

        if (stages.isEmpty()) {
            item { Text("Reading the ladder…", color = c.inkSoft) }
        }

        items(stages) { stage ->
            StageCard(stage, stages, ticks, onOpen, onOpenBook)
            Spacer(Modifier.height(Gap.s8))
        }
    }
}

@Composable
private fun StageCard(
    stage: Stage,
    stages: List<Stage>,
    ticks: Set<String>,
    onOpen: (Stage, Lesson) -> Unit,
    onOpenBook: (Stage) -> Unit = {},
) {
    val c = LocalReiad.current
    val lessons = stage.lessons
    val done = lessons.count { lessonId(stage.slug, it.slug) in ticks }

    /* Where the ground under this stage was laid, and only where
       the reader has not already been there.

       A SUGGESTION and never a lock. Nothing on this site is
       gated: a reader who wants stage six on their first day gets
       stage six, and there has never been a padlock on it. What
       this earns is one quiet line, which is the difference
       between a ladder and a corridor.

       `needs` comes down from the school's own curriculum and is
       read by nothing on the site itself, which is why it took a
       surface check to notice it was being sent. */
    val after = stage.needs
        .mapNotNull { slug -> stages.firstOrNull { it.slug == slug } }
        .filter { earlier -> earlier.lessons.none { lessonId(earlier.slug, it.slug) in ticks } }

    /* A PANE, because it holds other things. Pressing it does
       nothing; pressing a rung inside it does. A card here would
       be a surface that looks like it takes you somewhere and
       does not, which is the confusion the deck exists to end. */
    Pane {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AccentRail(Modifier.height(34.dp))
            Spacer(Modifier.width(Gap.s6))
            Column(Modifier.weight(1f)) {
                stage.kicker?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = c.accent)
                }
                Text(stage.bn, style = MaterialTheme.typography.titleMedium, color = c.ink)
                stage.en?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = c.inkSoft)
                }
            }
            Text(
                "$done/${lessons.size}",
                style = MaterialTheme.typography.labelMedium,
                color = if (done >= lessons.size && lessons.isNotEmpty()) c.accent else c.inkSoft,
            )
        }

        Spacer(Modifier.height(Gap.s5))
        StageState(done, lessons.size, after.map { it.bn })

        stage.can?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(Gap.s5))
            Text(it, style = BanglaBody.copy(
                fontSize = MaterialTheme.typography.bodySmall.fontSize,
                lineHeight = MaterialTheme.typography.bodySmall.fontSize * 1.7f,
            ), color = c.inkSoft)
        }

        Spacer(Modifier.height(Gap.s6))
        Groove(if (lessons.isEmpty()) 0f else done.toFloat() / lessons.size)
        Spacer(Modifier.height(Gap.s5))

        /* The practice book, where there is one, and what the
           stage says INSTEAD where there is not.

           Two schools have books and two do not, and the German
           school's fourth stage has neither: at B2 the exercise
           stops being a page you fill in and becomes the news you
           read, which is what `uebung` says. A stage with neither
           shows nothing rather than an empty slot. */
        stage.workbook?.let { workbook ->
            Rung(Modifier.clickable(role = Role.Button) { onOpenBook(stage) }) {
                Icon("pen", size = 18.dp, tint = c.accent)
                Spacer(Modifier.width(Gap.s6))
                Text(
                    "${bengaliNumber(workbook.days)} দিনের অনুশীলন খাতা",
                    style = BanglaBody,
                    color = c.ink,
                    modifier = Modifier.weight(1f),
                )
                Text("→", style = MaterialTheme.typography.labelLarge, color = c.accent)
            }
            Spacer(Modifier.height(Gap.s5))
        } ?: stage.uebung?.takeIf { it.isNotBlank() }?.let {
            Plate(Modifier.fillMaxWidth()) {
                Text(it, style = BanglaBody.copy(
                    fontSize = MaterialTheme.typography.bodySmall.fontSize,
                    lineHeight = MaterialTheme.typography.bodySmall.fontSize * 1.7f,
                ), color = c.inkSoft)
            }
            Spacer(Modifier.height(Gap.s5))
        }

        for (lesson in lessons) {
            val id = lessonId(stage.slug, lesson.slug)
            Rung(Modifier.clickable(enabled = lesson.isWritten) { onOpen(stage, lesson) }) {
                Box(
                    Modifier
                        .width(16.dp)
                        .height(16.dp)
                        .background(
                            if (id in ticks) c.accent else c.hairline,
                            RoundedCornerShape(Corner.pill),
                        )
                )
                Spacer(Modifier.width(Gap.s6))
                Column(Modifier.weight(1f)) {
                    Text(
                        lesson.bn,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (lesson.isWritten) c.ink else c.inkSoft,
                    )
                    (lesson.en ?: lesson.de ?: lesson.ar)?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = c.inkSoft)
                    }
                }
                if (!lesson.isWritten) Chip("আসছে")
                else if (lesson.minutes > 0) {
                    Text(
                        "${lesson.minutes}m",
                        style = MaterialTheme.typography.labelSmall,
                        color = c.inkSoft,
                    )
                }
            }
        }
    }
}

/* ---------- a lesson ---------- */

@Composable
private fun Reading(
    school: LadderSchool,
    stage: Stage,
    lesson: Lesson,
    page: LessonPage?,
    ticked: Boolean,
    isMoney: Boolean,
    onBack: () -> Unit,
    onTick: () -> Unit,
    checks: Set<String>,
    onCheck: (String) -> Unit,
    lessonKey: String,
) {
    val c = LocalReiad.current
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Gap.s8)
            .padding(top = Gap.s11, bottom = BAR_CLEARANCE)
    ) {
        Text(
            "← ${stage.bn}",
            style = MaterialTheme.typography.labelLarge,
            color = c.accent,
            modifier = Modifier.clickable { onBack() },
        )
        Spacer(Modifier.height(Gap.s7))

        Text(lesson.bn, style = MaterialTheme.typography.headlineMedium, color = c.ink)
        lesson.blurb?.let {
            Spacer(Modifier.height(Gap.s4))
            Text(it, style = MaterialTheme.typography.bodyMedium, color = c.inkSoft)
        }
        Spacer(Modifier.height(Gap.s9))

        when {
            page == null -> Text("Opening…", color = c.inkSoft)
            page.body.isBlank() -> Text(
                "This one is promised and not written yet.",
                style = MaterialTheme.typography.bodyLarge,
                color = c.inkSoft,
            )
            else -> {
                val blocks = remember(page.body) { BodyParser.parse(page.body).blocks }
                /* Every checklist in a school lesson IS a set of
                   checkpoints. The numbering is computed once,
                   across the whole lesson, because that is how
                   the site files them and the ids are in real
                   accounts. */
                val bases = remember(blocks) { checkpointBases(blocks) }
                BodyView(
                    blocks,
                    checkpoints = Checkpoints(
                        lessonId = lessonKey,
                        done = checks,
                        bases = bases,
                        onToggle = onCheck,
                    ),
                )

                val total = remember(blocks) { checkpointCount(blocks) }
                if (total > 0) {
                    val done = checks.count { it.startsWith("$lessonKey#") }
                    Spacer(Modifier.height(Gap.s7))
                    Plate(Modifier.fillMaxWidth()) {
                        Text(
                            "$done of $total checkpoints",
                            style = MaterialTheme.typography.labelMedium,
                            color = c.accent,
                        )
                        Spacer(Modifier.height(Gap.s3))
                        Text(
                            /* Said out loud, because the number
                               above sits next to a ladder that
                               deliberately ignores it. */
                            "These are yours to work through. They count towards no ladder.",
                            style = MaterialTheme.typography.bodySmall,
                            color = c.inkSoft,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(Gap.s10))

        /* The money school's tick is a button. The other three
           marked this lesson when it opened, so what they get is
           a statement rather than a control. */
        if (isMoney) {
            Control(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button, onClick = onTick),
                ground = if (ticked) c.accent else c.panel,
            ) {
                Text(
                    if (ticked) "পড়া হয়েছে ✓" else "পড়া হয়েছে",
                    style = MaterialTheme.typography.labelLarge.copy(fontFamily = Faces.bengali),
                    color = if (ticked) c.paper else c.accent,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            }
        } else if (ticked) {
            Plate(Modifier.fillMaxWidth()) {
                Text("পড়া হয়েছে ✓", style = MaterialTheme.typography.labelLarge, color = c.accent)
            }
        }
        Spacer(Modifier.height(Gap.s10))
    }
}
