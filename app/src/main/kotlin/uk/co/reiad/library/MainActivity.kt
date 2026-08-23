package uk.co.reiad.library

import android.content.Intent
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
import kotlinx.coroutines.flow.StateFlow as KStateFlow
import kotlinx.coroutines.launch
import uk.co.reiad.library.core.Accent
import uk.co.reiad.library.core.Accents
import uk.co.reiad.library.core.BodyParser
import uk.co.reiad.library.core.Bookmark
import uk.co.reiad.library.core.LadderSchool
import uk.co.reiad.library.core.Lesson
import uk.co.reiad.library.core.LessonPage
import uk.co.reiad.library.core.NavGroup
import uk.co.reiad.library.core.Piece
import uk.co.reiad.library.core.School
import uk.co.reiad.library.core.NavItem
import uk.co.reiad.library.core.SiteManifest
import uk.co.reiad.library.core.Prefs
import uk.co.reiad.library.core.Stage
import uk.co.reiad.library.core.Theme
import uk.co.reiad.library.core.bengaliNumber
import uk.co.reiad.library.core.checkpointBases
import uk.co.reiad.library.core.checkpointCount
import uk.co.reiad.library.core.lessonId
import uk.co.reiad.library.core.lessonUrl
import uk.co.reiad.library.account.Account
import uk.co.reiad.library.account.Library
import uk.co.reiad.library.account.Sync
import uk.co.reiad.library.account.SyncWorker
import uk.co.reiad.library.account.exportAll
import uk.co.reiad.library.core.Arrival
import uk.co.reiad.library.core.Kept
import uk.co.reiad.library.core.Reader
import uk.co.reiad.library.core.Target
import uk.co.reiad.library.data.Reiad
import uk.co.reiad.library.core.stock.Keys
import uk.co.reiad.library.core.stock.ToolWords
import uk.co.reiad.library.core.stock.analyse
import uk.co.reiad.library.core.stock.shareLink
import uk.co.reiad.library.core.stock.toCsv
import uk.co.reiad.library.ui.StockScreen
import uk.co.reiad.library.ui.ToolsWaiting
import uk.co.reiad.library.ui.Skeleton
import uk.co.reiad.library.ui.Problem
import uk.co.reiad.library.ui.RowCard
import uk.co.reiad.library.ui.Crumb
import uk.co.reiad.library.ui.Door
import uk.co.reiad.library.ui.PageHead
import uk.co.reiad.library.ui.Fact
import uk.co.reiad.library.ui.StockState
import uk.co.reiad.library.ui.CalcState
import uk.co.reiad.library.ui.CalculatorsScreen
import uk.co.reiad.library.ui.LiveScreen
import uk.co.reiad.library.ui.LiveState
import uk.co.reiad.library.ui.RoutineScreen
import uk.co.reiad.library.ui.RoutineState
import uk.co.reiad.library.routine.Days
import uk.co.reiad.library.routine.RoutineRow
import uk.co.reiad.library.core.routine.consistency
import uk.co.reiad.library.core.routine.dayBefore
import uk.co.reiad.library.core.routine.echo
import uk.co.reiad.library.core.routine.greeting
import uk.co.reiad.library.core.routine.heat
import uk.co.reiad.library.core.routine.momentum
import uk.co.reiad.library.core.routine.neverMarked
import uk.co.reiad.library.core.routine.runs
import uk.co.reiad.library.core.routine.seasonOf
import uk.co.reiad.library.broker.Answer
import uk.co.reiad.library.broker.Broker
import uk.co.reiad.library.core.broker.dividendMonths
import uk.co.reiad.library.core.broker.holdingsOf
import uk.co.reiad.library.core.broker.totalsOf
import uk.co.reiad.library.ui.AccentRail
import uk.co.reiad.library.ui.AccountScreen
import uk.co.reiad.library.ui.BodyView
import uk.co.reiad.library.ui.Faces
import uk.co.reiad.library.ui.Checkpoints
import uk.co.reiad.library.ui.BAR_CLEARANCE
import uk.co.reiad.library.ui.TOP_CLEARANCE
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

    /** The URI a sign-in came back on, or null.

        Held as state rather than read from the intent inside the
        composition, because `onNewIntent` is how a redirect
        arrives when the app is ALREADY open, which is the usual
        case: the Custom Tab is on top of a running activity. An
        app that only read `onCreate`'s intent would sign a reader
        in on a cold start and do nothing on a warm one. */
    private val arrival = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        arrival.value = intent?.data?.toString()
        setContent { App(arrival) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        arrival.value = intent.data?.toString()
    }
}

/** How far above the bottom a page has to stop.

    The bar is a floating pill rather than a bar welded to the
    edge, which is the site's own arrangement one level down, so
    nothing insets the content for it and every screen has to
    leave the room itself. */

/** The stock check's key in `shared/nav.ts`, which is the one
    place that table is said. Named rather than typed at the two
    call sites, because a nav key that stops matching is a card
    that silently starts opening the site in a browser instead. */
private const val STOCK_KEY = "stock"

/** And the other five, which share one nav entry. */
private const val TOOLS_KEY = "tools"

/** The live portfolio. */
private const val LIVE_KEY = "live"

/** And the routine. */
private const val ROUTINE_KEY = "routine"

/* ---------- where the reader is ---------- */

internal sealed interface Where {
    data object Home : Where
    data class Group(val group: NavGroup) : Where

    /** A reading hub. `section` is `insights`, `cooking` or
        `travel`, which is the value in the one column that says
        where a piece lives. */
    data class Hub(val section: String, val title: String) : Where
    data class Reading2(val section: String, val piece: Piece) : Where

    /** A practice book. One page, returned to thirty times. */
    data class Book(val school: LadderSchool, val stage: Stage) : Where
    data object Account : Where
    data class Ladder(val school: LadderSchool) : Where
    data class Reading(val school: LadderSchool, val stage: Stage, val lesson: Lesson) : Where

    /** The stock check. One screen, and the only one here whose
        content is computed rather than fetched: the model is in
        `core` and its words come down from `/api/tools`. */
    data object Stock : Where

    /** The other five calculators, which share a screen and a
        model for the same reason. */
    data object Calculators : Where

    /** One real portfolio, live. The only screen here that
        cannot be read offline, and it says so rather than
        showing a cached balance: a live portfolio that is not
        live is a screenshot. */
    data object Live : Where

    /** The routine. It belongs to an ACCOUNT rather than to this
        phone, which is the one thing about it worth saying twice:
        there is nothing to show signed out, and that is not an
        error. */
    data object Routine : Where
}

internal class AppModel(private val reiad: Reiad) : ViewModel() {

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

    /* ---------- the calculators ----------

       Their WORDS, which are data and arrive from the site, and
       the reader's current check, which is this session's and
       deliberately not stored: a half-typed company is not a
       thing to restore three days later, and a check worth
       keeping is a saved scenario with a name on it. */

    private val _words = MutableStateFlow<ToolWords?>(null)
    val words: StateFlow<ToolWords?> = _words.asStateFlow()

    /** Why the words did not arrive, when they did not.

        Kept rather than discarded, and that is the whole of the
        black page: `Cached` reported a 404 and `openTools` threw
        the report away, so every calculator waited for ever on an
        endpoint that could not answer. A fetch has three outcomes
        and this is the second one. */
    private val _wordsProblem = MutableStateFlow<String?>(null)
    val wordsProblem: StateFlow<String?> = _wordsProblem.asStateFlow()

    private val _stock = MutableStateFlow(StockState())
    val stock: StateFlow<StockState> = _stock.asStateFlow()

    private val _calc = MutableStateFlow(CalcState())
    val calc: StateFlow<CalcState> = _calc.asStateFlow()

    private val _live = MutableStateFlow(LiveState())
    val live: StateFlow<LiveState> = _live.asStateFlow()

    private val _routine = MutableStateFlow(RoutineState())
    val routine: StateFlow<RoutineState> = _routine.asStateFlow()

    private var routineStore: Days? = null

    private val _toolNote = MutableStateFlow<String?>(null)
    val toolNote: StateFlow<String?> = _toolNote.asStateFlow()

    /** Asked for once, when a calculator is first opened, rather
        than at start-up: 22KB is not much and it is not nothing
        on a phone that may never open one. */
    fun openTools() {
        if (_words.value != null) return
        viewModelScope.launch {
            _wordsProblem.value = null
            val answer = reiad.toolWords()
            _words.value = answer.value
            _wordsProblem.value = answer.problem
        }
    }

    /** And again, after a reader presses the button that says so. */
    fun retryTools() {
        _words.value = null
        _wordsProblem.value = null
        openTools()
    }

    fun setStock(next: StockState) { _stock.value = next }

    fun setCalc(next: CalcState) { _calc.value = next }

    /** Ask for everything the live page shows, in one go.

        The public portfolio ALWAYS, because it is what a reader
        with no account sees and what a reader with one compares
        against. The reader's own only where a key is saved, and
        `no-key` back from the Worker is an invitation rather than
        a failure.

        Re-asked on every visit rather than cached: the numbers
        are the point, the Worker already meters them at a minute,
        and a cached balance shown as live is a screenshot. */
    fun openLive(context: android.content.Context) {
        val broker = broker(context)
        _live.value = LiveState(loading = true)
        viewModelScope.launch {
            val site = broker.public()
            _live.value = _live.value.copy(
                site = (site as? Answer.Got)?.value,
                trouble = (site as? Answer.Failed)?.trouble,
            )

            val standing = (broker.me() as? Answer.Got)?.value
            _live.value = _live.value.copy(standing = standing)

            /* Nothing further for a reader with no key. Asking
               anyway would spend one of the broker's six calls a
               minute to be told so. */
            if (standing?.savedLabel == null) {
                _live.value = _live.value.copy(loading = false)
                return@launch
            }

            when (val own = broker.live()) {
                is Answer.Got -> {
                    val totals = totalsOf(own.value.summary)
                    _live.value = _live.value.copy(
                        own = own.value,
                        ownTotals = totals,
                        ownHoldings = holdingsOf(own.value.positions, totals.invested),
                    )
                    /* Dividends are a second call against a
                       second limit, so they come after the
                       numbers rather than beside them: a reader
                       should see their balance while this is
                       still in the air. */
                    (broker.history() as? Answer.Got)?.value?.let { history ->
                        val now = java.time.LocalDate.now()
                        _live.value = _live.value.copy(
                            dividends = dividendMonths(
                                history["dividends"], now.year, now.monthValue,
                            ),
                        )
                    }
                }
                is Answer.Failed -> _live.value = _live.value.copy(trouble = own.trouble)
            }
            _live.value = _live.value.copy(loading = false)
        }
    }

    /** The routine, and everything read off it.

        A YEAR of days is fetched, not a week: `runs` looks back
        365 and the heatmap twelve weeks, and a screen that asked
        for less would quietly draw a shorter history as a worse
        one. It is one request and a year of days is a few
        kilobytes. */
    fun openRoutine(context: android.content.Context) {
        val store = routineStore ?: Days(account(context)).also { routineStore = it }
        _routine.value = RoutineState(loading = true)
        viewModelScope.launch {
            if (account(context).token() == null) {
                _routine.value = RoutineState(loading = false, signedOut = true)
                return@launch
            }
            val today = java.time.LocalDate.now().toString()
            val row = store.routine()
            if (row == null) {
                _routine.value = RoutineState(loading = false, today = today, greeting = hello())
                return@launch
            }
            val entries = store.entries(dayBefore(today, 365))
            _routine.value = readRoutine(row, entries, today)
        }
    }

    /** Everything the screen draws, derived in one place so a
        mark and a chart cannot disagree about what today is. */
    private fun readRoutine(
        row: RoutineRow,
        entries: List<uk.co.reiad.library.core.routine.Entry>,
        today: String,
    ): RoutineState {
        val shape = row.shape
        return RoutineState(
            routineId = row.id,
            shape = shape,
            today = today,
            entry = entries.firstOrNull { it.date == today },
            entries = entries,
            heat = heat(shape, entries, today, 12),
            consistency = consistency(shape, entries, today, 28),
            neverMarked = neverMarked(shape, entries),
            momentum = momentum(shape, entries, today, 28),
            runs = runs(entries, today, 365),
            echo = echo(entries, today),
            season = seasonOf(today),
            greeting = hello(),
            loading = false,
        )
    }

    private fun hello(): String = greeting(java.time.LocalTime.now().hour).first

    /** One mark, saved.

        UNTICKING REMOVES THE KEY rather than writing a zero. An
        absent key is a day with nothing to say about that task
        and a zero is a judgement wearing a number's clothes;
        every piece of arithmetic in `core/routine` reads that
        distinction. */
    fun mark(taskId: String, value: Double) {
        val now = _routine.value
        val id = now.routineId ?: return
        val marks = now.entry?.marks.orEmpty().toMutableMap()
        if (value > 0) marks[taskId] = value else marks.remove(taskId)
        writeDay(id, (now.entry ?: emptyDay(now.today)).copy(marks = marks))
    }

    fun mood(id: String?) {
        val now = _routine.value
        val routineId = now.routineId ?: return
        writeDay(routineId, (now.entry ?: emptyDay(now.today)).copy(mood = id))
    }

    fun note(text: String) {
        val now = _routine.value
        val routineId = now.routineId ?: return
        writeDay(routineId, (now.entry ?: emptyDay(now.today)).copy(note = text))
    }

    private fun emptyDay(date: String) =
        uk.co.reiad.library.core.routine.Entry(date = date)

    /** Written through at once, and every derived figure with it.

        The screen redraws from the SAME derivation the fetch
        used, rather than nudging a number: a tick that moved the
        percentage without moving the heatmap would be two
        answers about one day. */
    private fun writeDay(routineId: String, entry: uk.co.reiad.library.core.routine.Entry) {
        val now = _routine.value
        val entries = now.entries.filter { it.date != entry.date } + entry
        _routine.value = readRoutine(
            RoutineRow(id = routineId, bands = now.shape.bands, tasks = now.shape.tasks),
            entries.sortedByDescending { it.date },
            now.today,
        ).copy(saving = true)

        viewModelScope.launch {
            routineStore?.save(routineId, entry)
            _routine.value = _routine.value.copy(saving = false)
        }
    }

    private var brokerClient: Broker? = null
    private fun broker(context: android.content.Context): Broker =
        brokerClient ?: Broker(account(context)).also { brokerClient = it }

    /** Which language the calculators open in.

        `tool-lang` on the site, saved into `reader-prefs` beside
        it, which is what carries it to the reader's other
        devices. One choice, one key. */
    fun chooseToolLang(lang: String) {
        viewModelScope.launch { reiad.savePrefs { it.copy(lang = lang) } }
        _toolNote.value = null
    }

    /** The link a reader sends somebody, on the clipboard.

        The site's own address rather than a deep link into this
        app, because whoever receives it may not have the app and
        a link that only opens on one device is not a shared
        analysis. */
    fun copyCheck(context: android.content.Context) {
        val link = shareLink(
            _stock.value.inputs,
            _stock.value.weights,
            style = _stock.value.style.takeIf { it != "custom" },
            lang = _stock.value.lang.takeIf { it != "en" },
        )
        val clip = context.getSystemService(android.content.ClipboardManager::class.java)
        clip?.setPrimaryClip(android.content.ClipData.newPlainText("Stock check", link))
        _toolNote.value = _words.value?.t(Keys.COPIED, _stock.value.lang) ?: link
    }

    /** The whole analysis as a spreadsheet, shared the way the
        account export is: the app's own cache and a content URI,
        never a file path. */
    fun exportCheck(context: android.content.Context) {
        val words = _words.value ?: return
        val state = _stock.value
        val text = toCsv(
            analyse(state.inputs, state.weights), words, state.weights, state.style,
        )
        _toolNote.value = if (shareCsv(context, text)) {
            "Sent to whatever you chose."
        } else {
            "Could not open the share sheet on this device."
        }
    }

    private fun shareCsv(context: android.content.Context, text: String): Boolean = runCatching {
        val dir = java.io.File(context.cacheDir, "exports").apply { mkdirs() }
        val file = java.io.File(dir, "stock-check.csv")
        file.writeText(text)
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.files",
            file,
        )
        context.startActivity(
            android.content.Intent.createChooser(
                android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "text/csv"
                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                },
                "Stock check",
            ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        true
    }.getOrDefault(false)

    init { refresh() }

    /** Ask the site what it holds, and say so if it will not.

        Public and re-callable, because the front page's failure
        state has a button on it: an app that reports a problem
        and offers no way to try again has told a reader something
        they can do nothing with. */
    fun refresh() {
        viewModelScope.launch {
            val answer = reiad.manifest()
            _site.value = answer.value
            _stale.value = answer.stale
            _note.value = answer.problem
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

    /** A tick, and a request that it reach the account.

        `SyncWorker.soon` is queued rather than an exchange being
        run: a reader on a train ticks four lessons and the work
        runs once, when there is a network, whether or not the app
        is still open. That is the half a browser cannot have,
        where a tick made offline waits for the next page load. */
    fun tick(school: LadderSchool, stage: Stage, lesson: Lesson) {
        val which = School.of(school.key) ?: return
        viewModelScope.launch {
            val after = reiad.toggleTick(which, lessonId(stage.slug, lesson.slug))
            _ticks.value = _ticks.value + (which.id to after)
            queueSync()
        }
    }

    /** Where the app is running, for the worker. Null until the
        account has been asked for once, which every launch does. */
    private var host: android.content.Context? = null

    private fun queueSync() {
        host?.let { if (account != null) SyncWorker.soon(it) }
    }

    fun ticksOf(key: String): Set<String> = _ticks.value[key].orEmpty()

    /* ---------- checkpoints and the bookmark ---------- */

    private val _checks = MutableStateFlow<Map<String, Set<String>>>(emptyMap())
    val checks: StateFlow<Map<String, Set<String>>> = _checks.asStateFlow()

    private val _bookmarks = MutableStateFlow<Map<String, Bookmark>>(emptyMap())
    val bookmarks: StateFlow<Map<String, Bookmark>> = _bookmarks.asStateFlow()

    fun loadMarks() {
        viewModelScope.launch {
            val checks = mutableMapOf<String, Set<String>>()
            val marks = mutableMapOf<String, Bookmark>()
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
            queueSync()
        }
    }

    /** A visit moves the bookmark and ticks nothing.

        Opening is not finishing, and the two verbs are separate
        everywhere in this app because they are separate on the
        site: the money school's tick is a button, and the other
        three mark a lesson as READ on opening, which is a
        different fact from where somebody last was. */
    fun visited(school: School, mark: Bookmark) {
        viewModelScope.launch {
            reiad.remember(school, mark)
            _bookmarks.value = _bookmarks.value + (school.id to mark)
            queueSync()
        }
    }

    /* ---------- the account ---------- */

    private val _reader = MutableStateFlow<Reader?>(null)
    val reader: StateFlow<Reader?> = _reader.asStateFlow()

    private val _authProblem = MutableStateFlow<String?>(null)
    val authProblem: StateFlow<String?> = _authProblem.asStateFlow()

    private val _linkSent = MutableStateFlow(false)
    val linkSent: StateFlow<Boolean> = _linkSent.asStateFlow()

    private var account: Account? = null
    private var sync: Sync? = null
    private var library: Library? = null

    private val _kept = MutableStateFlow<List<Kept>>(emptyList())
    val kept: StateFlow<List<Kept>> = _kept.asStateFlow()

    private val _targets = MutableStateFlow<List<Target>>(emptyList())
    val targets: StateFlow<List<Target>> = _targets.asStateFlow()

    private val _daysActive = MutableStateFlow<Set<String>>(emptySet())
    val daysActive: StateFlow<Set<String>> = _daysActive.asStateFlow()

    private val _exported = MutableStateFlow<String?>(null)
    val exported: StateFlow<String?> = _exported.asStateFlow()

    private val _erasing = MutableStateFlow<String?>(null)
    val erasing: StateFlow<String?> = _erasing.asStateFlow()

    fun account(context: android.content.Context): Account {
        account?.let { return it }
        val made = Account(context.applicationContext)
        account = made
        library = Library(made)
        host = context.applicationContext
        sync = Sync(context.applicationContext, made, reiad.store)
        viewModelScope.launch {
            _reader.value = made.reader.first()
            if (_reader.value != null) loadAccount()
        }
        return made
    }

    /** A sign-in came back. */
    fun arrived(context: android.content.Context, uri: String) {
        viewModelScope.launch {
            when (val answer = account(context).arrived(uri)) {
                is Arrival.SignedIn -> {
                    _authProblem.value = null
                    _linkSent.value = false
                    _reader.value = answer.session.reader
                    /* The account's rows come down straight away,
                       because a reader who has just signed in
                       expects to see their reading, and the first
                       exchange of a session ADOPTS. */
                    sync?.exchange()
                    loadMarks()
                    loadAccount()
                    SyncWorker.soon(context)
                }
                is Arrival.Failed -> _authProblem.value = answer.reason
                Arrival.NotAnArrival -> Unit
            }
        }
    }

    /** What the ACCOUNT holds, as opposed to what the phone
        holds. Neither the reading list nor the targets has a
        local copy, so this is the only place they come from. */
    fun loadAccount() {
        val shelf = library ?: return
        viewModelScope.launch {
            _kept.value = shelf.kept()
            _targets.value = shelf.targets()
            _daysActive.value = reiad.daysActive()
        }
    }

    fun keep(url: String, title: String, kind: String, saved: Boolean? = null, note: String? = null) {
        val shelf = library ?: return
        viewModelScope.launch {
            /* Shown immediately and confirmed after, because a
               Save that waits for a round trip is a Save a reader
               presses twice. */
            _kept.value = _kept.value.map {
                if (it.url != url) it
                else it.copy(saved = saved ?: it.saved, note = note ?: it.note)
            }.ifEmpty {
                listOf(Kept(url = url, title = title, kind = kind,
                    saved = saved ?: false, note = note.orEmpty()))
            }
            shelf.keep(url, title, kind, saved, note)
            _kept.value = shelf.kept()
        }
    }

    fun removeTarget(id: String) {
        val shelf = library ?: return
        viewModelScope.launch {
            shelf.removeTarget(id)
            _targets.value = shelf.targets()
        }
    }

    /** One JSON file with everything in it, handed to the share
        sheet so the reader can put it wherever they keep things.

        Built from what the ACCOUNT answers rather than from what
        this phone holds, because a copy of a mirror is not a copy
        of the record: a device that had never exchanged would
        otherwise export an empty file and call it everything. */
    fun export(context: android.content.Context) {
        val shelf = library ?: return
        viewModelScope.launch {
            _exported.value = "Preparing…"
            val file = runCatching { shelf.exportAll(_reader.value, reiad.everything()) }
                .getOrNull()
            if (file == null) {
                _exported.value = "That did not work. Try again with a connection."
                return@launch
            }
            _exported.value = if (share(context, file)) {
                "Sent to whatever you chose. ${file.length} characters."
            } else {
                "Could not open the share sheet on this device."
            }
        }
    }

    /** Written to the app's own cache and shared by a content
        URI, never by a file path.

        A `file://` URI has been refused since Android 7, and the
        cache rather than downloads because this file is a copy of
        somebody's whole account: it should live exactly as long
        as it takes them to put it where they want it. */
    private fun share(context: android.content.Context, text: String): Boolean = runCatching {
        val dir = java.io.File(context.cacheDir, "exports").apply { mkdirs() }
        val file = java.io.File(dir, "reiad-account.json")
        file.writeText(text)
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.files",
            file,
        )
        context.startActivity(
            android.content.Intent.createChooser(
                android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "application/json"
                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                },
                "Your account",
            ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        true
    }.getOrDefault(false)

    fun erase() {
        val shelf = library ?: return
        viewModelScope.launch {
            _erasing.value = "Erasing…"
            val gone = shelf.eraseAll()
            /* The mirror comes off either way. Leaving a phone
               full of rows the account no longer has would put
               every one of them straight back on the next
               exchange. */
            sync?.forget()
            _kept.value = emptyList()
            _targets.value = emptyList()
            _daysActive.value = emptySet()
            loadMarks()
            _erasing.value = if (gone) {
                "Erased. Nothing of yours is on this account or on this phone."
            } else {
                "Some of that did not work. Try again with a connection."
            }
        }
    }

    fun sendLink(context: android.content.Context, email: String) {
        viewModelScope.launch {
            _linkSent.value = account(context).sendLink(email)
            if (!_linkSent.value) _authProblem.value = "That email would not send."
        }
    }

    fun signOut(context: android.content.Context) {
        viewModelScope.launch {
            sync?.forget()
            account(context).signOut()
            _reader.value = null
            _kept.value = emptyList()
            _targets.value = emptyList()
            _exported.value = null
            loadMarks()
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
            queueSync()
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
        viewModelScope.launch {
            reiad.savePrefs(change)
            queueSync()
        }
    }

    fun chooseAudience(id: String) {
        viewModelScope.launch { reiad.setAudience(id) }
    }
}

/* ---------- the app ---------- */

@Composable
fun App(arrivals: StateFlow<String?> = MutableStateFlow(null)) {
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
    val words by model.words.collectAsState()
    val wordsProblem by model.wordsProblem.collectAsState()
    val stockState by model.stock.collectAsState()
    val calcState by model.calc.collectAsState()
    val liveState by model.live.collectAsState()
    val routineState by model.routine.collectAsState()
    val toolNote by model.toolNote.collectAsState()
    val openPiece by model.open.collectAsState()
    val reader by model.reader.collectAsState()
    val authProblem by model.authProblem.collectAsState()
    val linkSent by model.linkSent.collectAsState()
    val kept by model.kept.collectAsState()
    val targets by model.targets.collectAsState()
    val daysActive by model.daysActive.collectAsState()
    val exported by model.exported.collectAsState()
    val erasing by model.erasing.collectAsState()
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
        /* Built here rather than lazily inside a handler, so a
           reader who is already signed in has their name on the
           first frame rather than after they press something. */
        model.account(context)
    }

    /* A sign-in coming back. Keyed on the URI so the same arrival
       is not processed twice on a recomposition. */
    val arrival by arrivals.collectAsState()
    LaunchedEffect(arrival) {
        arrival?.let { model.arrived(context, it) }
    }

    val accent: Accent = when (val here = where) {
        Where.Account -> Accents.GREEN
        is Where.Book -> accentOf(here.school)
        is Where.Ladder -> accentOf(here.school)
        is Where.Reading -> accentOf(here.school)
        is Where.Hub -> tokenAccent(site?.accents?.get(here.section))
        is Where.Reading2 -> tokenAccent(site?.accents?.get(here.section))
        is Where.Group -> accentOfGroup(here.group)
        /* The calculators are gold on the site, which is the
           accent `shared/nav.ts` gives that group, so a reader
           arriving from the tools tab does not watch the whole
           page change colour. */
        Where.Stock -> Accents.GOLD
        Where.Calculators -> Accents.GOLD
        Where.Live -> Accents.GOLD
        Where.Routine -> Accents.GOLD
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
        Where.Account -> "account"
        is Where.Book -> here.school.key
        is Where.Ladder -> here.school.key
        is Where.Reading -> here.school.key
        is Where.Hub -> here.section
        is Where.Reading2 -> here.section
        is Where.Group -> here.group.items.firstOrNull()?.key
        Where.Stock -> STOCK_KEY
        Where.Calculators -> TOOLS_KEY
        Where.Live -> LIVE_KEY
        Where.Routine -> ROUTINE_KEY
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
                /* Every row of the menu, through the one function
                   that decides where a nav item goes.

                   It handled SCHOOLS and nothing else, so the
                   menu's other fourteen rows did nothing at all
                   when pressed: the account, both tool screens,
                   the live portfolio, the routine and every
                   reading hub. The drawer is the only way to
                   reach a group the bottom bar has no room for,
                   which made "account doesn't open" a true report
                   about a page that renders perfectly. */
                onItem = { item ->
                    where = if (opensHere(site, item)) {
                        goTo(model, context, site, item, where)
                    } else {
                        openOnSite(context, item.href, colours)
                        where
                    }
                },
                onDrawer = { drawer = it },
                onSearch = { searching = true },
                onSettings = { settings = true },
                onAudience = { model.chooseAudience(it) },
            ) {
            when (val here = where) {
                Where.Account -> {
                    BackHandler { where = Where.Home }
                    AccountScreen(
                        reader = reader,
                        kept = kept,
                        targets = targets,
                        daysActive = daysActive,
                        ticksOf = { key -> ticks[key].orEmpty().size },
                        onOpenKept = { row -> openOnSite(context, row.url, colours) },
                        onRemoveTarget = { model.removeTarget(it) },
                        onExport = { model.export(context) },
                        exported = exported,
                        onErase = { model.erase() },
                        erasing = erasing,
                        problem = authProblem,
                        linkSent = linkSent,
                        bottomPadding = BAR_CLEARANCE,
                        onGoogle = { model.account(context).signIn("google") },
                        onLink = { model.sendLink(context, it) },
                        onSignOut = { model.signOut(context) },
                    )
                }

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
                        /* Looked up by URL, which is what the row
                           is keyed on: one row per person per
                           page. Null until the account has
                           answered at all. */
                        kept = if (reader == null) null
                        else kept.firstOrNull { it.url == shown.url }
                            ?: Kept(url = shown.url, title = shown.title, kind = "piece"),
                        signedIn = reader != null,
                        onKeep = { saved, note ->
                            model.keep(shown.url, shown.title, "piece", saved, note)
                        },
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

                Where.Routine -> {
                    BackHandler { where = Where.Home }
                    RoutineScreen(
                        state = routineState,
                        onMark = { task, value -> model.mark(task, value) },
                        onMood = { model.mood(it) },
                        onNote = { model.note(it) },
                        onOpenSite = { openOnSite(context, "/tools/routine", colours) },
                        contentPadding = PaddingValues(
                            start = Gap.s8, end = Gap.s8,
                            top = TOP_CLEARANCE, bottom = BAR_CLEARANCE,
                        ),
                    )
                }

                Where.Live -> {
                    BackHandler { where = Where.Home }
                    LiveScreen(
                        state = liveState,
                        /* The key is typed on the SITE, never
                           here: a broker credential is the one
                           thing worth making somebody enter where
                           they can see the address bar. */
                        onConnect = { openOnSite(context, "/tools/live", colours) },
                        contentPadding = PaddingValues(
                            start = Gap.s8, end = Gap.s8,
                            top = TOP_CLEARANCE, bottom = BAR_CLEARANCE,
                        ),
                    )
                }

                Where.Calculators -> {
                    BackHandler { where = Where.Home }
                    val toolWords = words
                    if (toolWords == null) {
                        ToolsWaiting(wordsProblem) { model.retryTools() }
                    } else {
                        CalculatorsScreen(
                            words = toolWords,
                            state = calcState,
                            onState = { model.setCalc(it) },
                            lang = prefs.lang,
                            onLang = { model.chooseToolLang(it) },
                            /* The names out of the manifest, both
                               languages, rather than a second copy
                               in the phrase table. */
                            titles = site?.tools.orEmpty()
                                .associate { it.id to (it.en to it.bn) },
                            contentPadding = PaddingValues(
                                start = Gap.s8, end = Gap.s8,
                                top = TOP_CLEARANCE, bottom = BAR_CLEARANCE,
                            ),
                        )
                    }
                }

                Where.Stock -> {
                    BackHandler { where = Where.Home }
                    val toolWords = words
                    if (toolWords == null) {
                        /* No key names before the words answer: a
                           page that turns into sentences a second
                           later is worse than the shape of one.
                           But never NOTHING, which is what stood
                           here and is what a reader met when the
                           endpoint 404ed. */
                        ToolsWaiting(wordsProblem) { model.retryTools() }
                    } else {
                        StockScreen(
                            words = toolWords,
                            /* The language is the READER's, out
                               of `tool-lang`, rather than this
                               screen's own: it is the same choice
                               they made on the site and it comes
                               down with the rest of their
                               preferences. */
                            state = stockState.copy(lang = prefs.lang),
                            onState = { model.setStock(it) },
                            onCopyLink = { model.copyCheck(context) },
                            onExport = { model.exportCheck(context) },
                            onLang = { model.chooseToolLang(it) },
                            note = toolNote,
                            contentPadding = PaddingValues(
                                start = Gap.s8, end = Gap.s8,
                                top = TOP_CLEARANCE, bottom = BAR_CLEARANCE,
                            ),
                        )
                    }
                }

                is Where.Group -> {
                    BackHandler { where = Where.Home }
                    GroupScreen(
                        group = here.group,
                        accents = site?.accents.orEmpty(),
                        bottomPadding = BAR_CLEARANCE,
                        canOpenHere = { item -> opensHere(site, item) },
                        onOpenHere = { item -> where = goTo(model, context, site, item, where) },
                    )
                }

                Where.Home -> Home(
                    site = site,
                    stale = stale,
                    note = note,
                    ticks = ticks,
                    audience = audience,
                    onOpen = { school ->
                        model.openLadder(school)
                        where = Where.Ladder(school)
                    },
                    /* A handle on the front page goes where the
                       same item in the menu goes, through the one
                       function that decides that: two copies of
                       this `when` is how a destination ends up
                       reachable from the menu and dead from the
                       front page. */
                    onGo = { item ->
                        where = if (opensHere(site, item)) {
                            goTo(model, context, site, item, where)
                        } else {
                            openOnSite(context, item.href, colours)
                            where
                        }
                    },
                    onRetry = { model.refresh() },
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
                        if (which != null) {
                            model.visited(
                                which,
                                Bookmark(
                                    id = id,
                                    title = here.lesson.bn,
                                    stage = here.stage.slug,
                                    /* A hint, and labelled one on
                                       the site for the same
                                       reason: a lesson can move
                                       and an id cannot. */
                                    url = lessonUrl(
                                        here.school.key,
                                        here.stage,
                                        here.lesson.slug,
                                    ),
                                ),
                            )
                        }
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
/** Whether this app can draw the destination itself.

    Anything it cannot opens on the site in a Custom Tab, which is
    the honest answer for a page nobody has ported: a dead handle
    is worse than a browser. */
internal fun opensHere(site: SiteManifest?, item: NavItem): Boolean =
    item.key == "account" || item.key == STOCK_KEY ||
        item.key == TOOLS_KEY || item.key == LIVE_KEY ||
        item.key == ROUTINE_KEY ||
        site?.ladders?.any { it.key == item.key } == true ||
        readingSection(site, item.key) != null

/** Where a nav item goes, and the ONE place that decides it.

    Both the menu and the front page's handles come through here,
    because the same item pressed in two places has to reach the
    same screen. It was one `when` inside the menu's own callback,
    which made a second caller a second copy. */
internal fun goTo(
    model: AppModel,
    context: android.content.Context,
    site: SiteManifest?,
    item: NavItem,
    now: Where,
): Where {
    val school = site?.ladders?.firstOrNull { it.key == item.key }
    val section = readingSection(site, item.key)
    return when {
        item.key == "account" -> Where.Account
        item.key == STOCK_KEY -> { model.openTools(); Where.Stock }
        item.key == TOOLS_KEY -> { model.openTools(); Where.Calculators }
        item.key == LIVE_KEY -> { model.openLive(context); Where.Live }
        item.key == ROUTINE_KEY -> { model.openRoutine(context); Where.Routine }
        school != null -> { model.openLadder(school); Where.Ladder(school) }
        section != null -> Where.Hub(section, sectionTitle(site, section))
        else -> now
    }
}

internal fun accentOf(school: LadderSchool): Accent =
    Accents.byToken(school.accent) ?: Accents.BY_KEY[school.key] ?: Accents.GREEN

internal fun accentOfGroup(group: NavGroup): Accent = tokenAccent(group.accent)

/** Which reading section a nav key is, or null.

    Asked of the MANIFEST rather than of a list here, so a fourth
    reading section added to the site turns up with no release.
    `sections` is the site's own table of them, and its `id` is
    the same string a piece carries in its `section` column. */
internal fun readingSection(site: SiteManifest?, key: String?): String? =
    site?.sections?.firstOrNull { it.id == key }?.id

internal fun sectionTitle(site: SiteManifest?, section: String): String =
    site?.sections?.firstOrNull { it.id == section }
        ?.let { it.bn.ifBlank { it.en } }
        ?: section.replaceFirstChar { it.uppercase() }

/* ---------- home ---------- */

@Composable
fun Home(
    site: SiteManifest?,
    stale: Boolean,
    note: String?,
    ticks: Map<String, Set<String>>,
    audience: String?,
    onOpen: (LadderSchool) -> Unit,
    onGo: (NavItem) -> Unit = {},
    onRetry: () -> Unit = {},
) {
    val c = LocalReiad.current
    /* One sway for the whole screen, not one per card. Every card
       on a page leans by the same amount, because they are all on
       the same handset: a lean per card would be twelve sensor
       listeners answering one movement. */
    val sway = rememberSway()
    /* The site's own door, chosen by the audience switch exactly
       as `data-hl` chooses it there, and `open` for a reader who
       has not answered it. */
    val door = site?.door
    val copy = door?.copy?.get(audience ?: "open") ?: door?.copy?.get("open")
    val icons = remember(site) {
        site?.nav.orEmpty().flatMap { it.items }
            .mapNotNull { item -> item.key?.let { it to item.icon } }
            .toMap()
    }
    val rows = remember(site) {
        site?.nav.orEmpty()
            .flatMap { group -> group.items.map { group to it } }
            .filter { (_, item) ->
                item.key != null && site?.ladders?.none { it.key == item.key } == true
            }
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = Gap.s8),
        /* The bar FLOATS over the page rather than pushing it, so
           the page has to end above it or the last card sits under
           the bar and looks like the list has been cut off. */
        contentPadding = PaddingValues(top = TOP_CLEARANCE, bottom = BAR_CLEARANCE),
    ) {
        item("door") {
            if (copy != null) {
                Door(
                    eyebrow = door?.eyebrow,
                    headline = copy.headline,
                    mark = copy.mark,
                    lede = copy.lede,
                    facts = door?.facts.orEmpty().map { Fact(it.n, it.label) },
                )
            } else {
                /* Before the first fetch has landed, and on a phone
                   that has never had one. The site's name is the
                   honest fallback: it is a fact this app ships
                   with rather than one it is waiting for. */
                Text(
                    site?.site?.name ?: "Reiad's Library",
                    style = MaterialTheme.typography.displaySmall,
                    color = c.ink,
                )
                Text(
                    site?.site?.tagline ?: "Finance & Bangladesh markets",
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.inkSoft,
                )
            }
            if (stale) {
                Spacer(Modifier.height(Gap.s6))
                Chip("SAVED COPY")
            }
            Spacer(Modifier.height(Gap.s10))
        }

        if (site == null) {
            /* Never an empty screen, and never a bare sentence in
               the middle of one. Either the app is reading the
               site or it could not: both are said, and the second
               one has a button. */
            item("waiting") {
                if (note != null) {
                    Problem("The site did not answer", note, onRetry = onRetry)
                } else {
                    Skeleton()
                }
            }
        }

        items(site?.ladders.orEmpty(), key = { it.key }) { school ->
            SchoolCard(
                school,
                ticks[school.key].orEmpty().size,
                sway,
                icons[school.key],
                onOpen,
            )
            Spacer(Modifier.height(Gap.s7))
        }

        /* And everything else the site holds, as HANDLES rather
           than as cards, which is the site's own side column: a
           tool, a reading hub and the account are one line each.
           A list of places to go should not be a page of
           paragraphs, and on a handset that difference is four
           screens of scrolling. */
        if (rows.isNotEmpty()) {
            item("rows-head") {
                Spacer(Modifier.height(Gap.s7))
                Text(
                    "AND THE REST OF IT",
                    style = MaterialTheme.typography.labelSmall,
                    color = c.inkSoft,
                )
                Spacer(Modifier.height(Gap.s6))
            }
            items(rows, key = { (_, item) -> item.key ?: item.label }) { (group, item) ->
                ReiadTheme(accent = accentOfGroup(group), dark = c.isDark) {
                    RowCard(
                        title = item.sub?.takeIf { it.isNotBlank() } ?: item.label,
                        icon = item.icon,
                        onOpen = { onGo(item) },
                    )
                }
                Spacer(Modifier.height(Gap.s5))
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
fun SchoolCard(
    school: LadderSchool,
    done: Int,
    sway: Sway,
    icon: String?,
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
            /* The disc, from the icon the nav table already
               names for this school. Looked up rather than
               chosen here, so a sixth school arrives drawn. */
            art = icon?.let { { Icon(it, size = 18.dp) } },
            onOpen = { onOpen(school) },
        )
    }
}

/* ---------- a ladder ---------- */

@Composable
fun Ladder(
    school: LadderSchool,
    stages: List<Stage>,
    ticks: Set<String>,
    stale: Boolean,
    onBack: () -> Unit,
    onOpen: (Stage, Lesson) -> Unit,
    onOpenBook: (Stage) -> Unit,
    bookmark: Bookmark? = null,
) {
    val c = LocalReiad.current
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = Gap.s8),
        contentPadding = PaddingValues(top = TOP_CLEARANCE, bottom = BAR_CLEARANCE),
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
            val at = bookmark?.let { mark ->
                stages.firstNotNullOfOrNull { stage ->
                    stage.lessons.firstOrNull { lessonId(stage.slug, it.slug) == mark.id }
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
fun StageCard(
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
fun Reading(
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
            .padding(top = TOP_CLEARANCE, bottom = BAR_CLEARANCE)
    ) {
        Crumb(stage.bn, onBack)
        Spacer(Modifier.height(Gap.s7))

        /* The lesson's own head, at the size the site sets a
           lesson's head. It was `headlineMedium` with a grey line
           under it, which is a card's title rather than a page's:
           a reader who has opened a lesson has arrived somewhere
           and the top of the page should say so. */
        PageHead(
            title = lesson.bn,
            eyebrow = listOfNotNull(
                stage.en ?: stage.bn,
                lesson.minutes.takeIf { it > 0 }?.let { "$it min" },
                lesson.risk,
            ).joinToString(" · "),
            lede = lesson.blurb,
        )
        Spacer(Modifier.height(Gap.s7))

        when {
            page == null -> Skeleton(lines = 5, label = "Opening the lesson")
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
