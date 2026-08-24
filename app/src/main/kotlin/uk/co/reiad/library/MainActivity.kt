package uk.co.reiad.library

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.StateFlow as KStateFlow
import kotlinx.coroutines.launch
import uk.co.reiad.library.core.Accent
import uk.co.reiad.library.core.Accents
import uk.co.reiad.library.core.BodyParser
import uk.co.reiad.library.core.Block
import uk.co.reiad.library.core.Bookmark
import uk.co.reiad.library.core.LadderSchool
import uk.co.reiad.library.core.Lesson
import uk.co.reiad.library.core.LessonPage
import uk.co.reiad.library.core.NavGroup
import uk.co.reiad.library.core.Piece
import uk.co.reiad.library.core.School
import uk.co.reiad.library.core.measureOf
import uk.co.reiad.library.core.scaleOf
import uk.co.reiad.library.core.Scenario
import uk.co.reiad.library.core.rungsOf
import uk.co.reiad.library.core.standingOf
import uk.co.reiad.library.core.stock.inScript
import uk.co.reiad.library.core.stock.readShare
import uk.co.reiad.library.core.stock.shareQuery
import uk.co.reiad.library.core.stock.summarise
import uk.co.reiad.library.core.ProgressKeys
import uk.co.reiad.library.core.NavItem
import uk.co.reiad.library.core.SiteManifest
import uk.co.reiad.library.core.Story
import uk.co.reiad.library.core.resolveHref
import uk.co.reiad.library.core.nav.Destination
import uk.co.reiad.library.core.nav.LIVE_KEY
import uk.co.reiad.library.core.nav.SKILLS_KEY
import uk.co.reiad.library.core.nav.PORTFOLIO_KEY
import uk.co.reiad.library.core.nav.DIET_KEY
import uk.co.reiad.library.core.nav.ROUTINE_KEY
import uk.co.reiad.library.core.nav.STOCK_KEY
import uk.co.reiad.library.core.nav.TOOLS_KEY
import uk.co.reiad.library.core.nav.destinationOf
import uk.co.reiad.library.core.Prefs
import uk.co.reiad.library.core.Stage
import uk.co.reiad.library.core.Theme
import uk.co.reiad.library.core.bengaliNumber
import uk.co.reiad.library.core.checkpointBases
import uk.co.reiad.library.core.checkpointCount
import uk.co.reiad.library.core.lessonId
import uk.co.reiad.library.core.lessonUrl
import uk.co.reiad.library.account.Account
import uk.co.reiad.library.account.Said
import uk.co.reiad.library.account.Say
import uk.co.reiad.library.account.Library
import uk.co.reiad.library.account.Sync
import uk.co.reiad.library.account.SyncWorker
import uk.co.reiad.library.account.exportAll
import uk.co.reiad.library.core.Arrival
import uk.co.reiad.library.core.Kept
import uk.co.reiad.library.core.Reader
import uk.co.reiad.library.core.Target
import uk.co.reiad.library.data.Held
import androidx.glance.appwidget.updateAll
import uk.co.reiad.library.data.Reiad
import uk.co.reiad.library.read.RemindWorker
import uk.co.reiad.library.widget.ContinueWidget
import uk.co.reiad.library.widget.NewsWidget
import uk.co.reiad.library.widget.ProgressWidget
import uk.co.reiad.library.data.SchoolWorker
import uk.co.reiad.library.data.Shelf
import uk.co.reiad.library.data.forgetHeld
import uk.co.reiad.library.data.heldAll
import uk.co.reiad.library.data.heldFor
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
import uk.co.reiad.library.ui.HeldPanel
import uk.co.reiad.library.ui.KeepSchool
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
import uk.co.reiad.library.diet.Log
import uk.co.reiad.library.routine.Days
import uk.co.reiad.library.core.diet.Ate
import uk.co.reiad.library.core.diet.DietDay
import uk.co.reiad.library.core.diet.DietEntry
import uk.co.reiad.library.core.diet.DietProfile
import uk.co.reiad.library.core.diet.FoodLibrary
import uk.co.reiad.library.core.diet.GoalKind
import uk.co.reiad.library.core.diet.Portion
import uk.co.reiad.library.core.diet.activityFactor
import uk.co.reiad.library.core.diet.bodyOf
import uk.co.reiad.library.core.diet.estimatedBurn
import uk.co.reiad.library.core.diet.fatEstimate
import uk.co.reiad.library.core.diet.loggedFrom
import uk.co.reiad.library.core.diet.restingBurn
import uk.co.reiad.library.core.diet.target
import uk.co.reiad.library.ui.DietScreen
import uk.co.reiad.library.ui.DietState
import uk.co.reiad.library.routine.RoutineRow
import uk.co.reiad.library.core.routine.consistency
import uk.co.reiad.library.core.routine.dayBefore
import uk.co.reiad.library.core.routine.echo
import uk.co.reiad.library.core.routine.greeting
import uk.co.reiad.library.core.routine.heat
import uk.co.reiad.library.core.routine.everMarked
import uk.co.reiad.library.core.routine.flock
import uk.co.reiad.library.core.routine.garden
import uk.co.reiad.library.core.routine.gardenFrom
import uk.co.reiad.library.core.routine.moodsFrom
import uk.co.reiad.library.core.routine.seasonsFrom
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
import uk.co.reiad.library.ui.barClearance
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import uk.co.reiad.library.core.BOARD_FLOOR
import uk.co.reiad.library.core.catalogueFloor
import uk.co.reiad.library.core.Placed
import uk.co.reiad.library.core.spanOf
import uk.co.reiad.library.core.unitsOf
import uk.co.reiad.library.core.kindOf
import uk.co.reiad.library.core.layoutOf
import uk.co.reiad.library.core.moved
import uk.co.reiad.library.core.storedOf
import uk.co.reiad.library.ui.BoardActions
import uk.co.reiad.library.ui.BoardData
import uk.co.reiad.library.ui.DRAWABLE
import uk.co.reiad.library.core.Motion
import uk.co.reiad.library.ui.ButtonKind
import uk.co.reiad.library.ui.PillButton
import uk.co.reiad.library.ui.rememberReducedMotion
import uk.co.reiad.library.ui.Widget
import uk.co.reiad.library.ui.WidgetFrame
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import uk.co.reiad.library.ui.WidgetPicker
import uk.co.reiad.library.ui.dragHandle
import uk.co.reiad.library.ui.rememberBoardDrag
import uk.co.reiad.library.ui.topClearance
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
import uk.co.reiad.library.ui.Path
import uk.co.reiad.library.ui.SkillsScreen
import uk.co.reiad.library.ui.PortfolioScreen
import uk.co.reiad.library.ui.Paths
import uk.co.reiad.library.ui.RoutineLine
import uk.co.reiad.library.ui.ThreadState
import uk.co.reiad.library.ui.accentOfSchool
import uk.co.reiad.library.ui.LessonHead
import uk.co.reiad.library.ui.SetupState
import uk.co.reiad.library.ui.seeded
import uk.co.reiad.library.ui.Pane
import uk.co.reiad.library.ui.PieceScreen
import uk.co.reiad.library.ui.Plate
import uk.co.reiad.library.ui.ReadingHub
import uk.co.reiad.library.ui.ReadingWidget
import uk.co.reiad.library.ui.ResumeCard
import uk.co.reiad.library.ui.StageState
import uk.co.reiad.library.ui.WorkbookScreen
import uk.co.reiad.library.ui.SchoolHead
import uk.co.reiad.library.ui.ReiadTheme
import uk.co.reiad.library.ui.coloursOf
import uk.co.reiad.library.ui.Rung
import uk.co.reiad.library.ui.SearchScreen
import uk.co.reiad.library.ui.SettingsSheet
import uk.co.reiad.library.ui.Shell
import uk.co.reiad.library.ui.ShellState
import uk.co.reiad.library.ui.LocalOpenLink
import uk.co.reiad.library.ui.LocalGlassLook
import uk.co.reiad.library.ui.glassLookOf
import uk.co.reiad.library.ui.ReiadColours
import uk.co.reiad.library.ui.arriving
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

    /**
     * The address an intent carries, whichever way it came.
     *
     * Two shapes reach this activity and they carry the URL in
     * different places. A VIEW puts it in `data`, which is a
     * tapped link or a shortcut. A SEND puts it in `EXTRA_TEXT`,
     * which is a reader sharing the page from a browser, and the
     * text is usually a sentence with a link somewhere inside it
     * rather than a bare URL: WhatsApp and Chrome both prepend
     * the page title.
     *
     * So the first `https://` run in the text is what is taken.
     * Anything else, including a share that carries no link at
     * all, answers null and the app opens where it was.
     */
    private fun addressIn(intent: Intent?): String? {
        if (intent == null) return null
        intent.data?.toString()?.let { return it }
        if (intent.action != Intent.ACTION_SEND) return null
        val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return null
        val at = text.indexOf("https://")
        if (at < 0) return null
        return text.substring(at).takeWhile { !it.isWhitespace() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        arrival.value = addressIn(intent)

        /* Two launch deaths in a row: the normal screen is what
           is dying, so the one drawn INSTEAD is the report. See
           `Guard.kt`, which exists because the only diagnosis a
           phone could offer was "doesn't open, just crashes". */
        if (ReiadApp.troubled(this)) {
            val stack = ReiadApp.lastCrash(this).orEmpty()
            setContent {
                uk.co.reiad.library.ui.ReiadTheme {
                    CrashScreen(
                        stack = stack,
                        onCopy = {
                            val clip = getSystemService(android.content.ClipboardManager::class.java)
                            clip?.setPrimaryClip(
                                android.content.ClipData.newPlainText("crash", stack),
                            )
                        },
                        onTryAgain = {
                            ReiadApp.forgetCrash(this)
                            recreate()
                        },
                    )
                }
            }
            return
        }

        /* The screen came up and stayed up: whatever this counter
           held, it is not a crash LOOP. Ten seconds, matching the
           window `ReiadApp` counts an early death inside. */
        window.decorView.postDelayed({ ReiadApp.settled(this) }, ReiadApp.EARLY_MS)

        setContent { App(arrival) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        arrival.value = addressIn(intent)
    }
}

/** How far above the bottom a page has to stop.

    The bar is a floating pill rather than a bar welded to the
    edge, which is the site's own arrangement one level down, so
    nothing insets the content for it and every screen has to
    leave the room itself. */

/* The four tool keys are `core/nav/Address.kt`'s, imported above.
   They were four `private const` here, and the address router
   needed the same four: a second copy of a nav key is a card that
   silently starts opening the site in a browser instead, and a
   deep link that silently lands on the front page. */

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

    /** The two hubs that are a LIST of things the manifest
        already carries: what this site teaches, and the work it
        shows. Both were a browser hand-off for eleven blocks,
        for a list this phone was holding the whole time. */
    data object Skills : Where

    data object Portfolio : Where

    /** The routine. It belongs to an ACCOUNT rather than to this
        phone, which is the one thing about it worth saying twice:
        there is nothing to show signed out, and that is not an
        error. */
    data object Routine : Where
    data object Diet : Where
}

internal class AppModel(private val reiad: Reiad) : ViewModel() {

    private val _site = MutableStateFlow<SiteManifest?>(null)
    val site: StateFlow<SiteManifest?> = _site.asStateFlow()

    private val _stale = MutableStateFlow(false)
    val stale: StateFlow<Boolean> = _stale.asStateFlow()

    private val _note = MutableStateFlow<String?>(null)
    val note: StateFlow<String?> = _note.asStateFlow()

    /* ---------- the offline shelf ----------

       Which schools the reader asked to keep, and how much of
       each is actually here. `held` is COUNTED from the cache
       every time the ladder opens rather than remembered: a
       number this app kept for itself would go on saying "kept"
       about a school somebody cleared in Android's settings. */

    private val _shelf = MutableStateFlow<Set<String>>(emptySet())
    val shelf: StateFlow<Set<String>> = _shelf.asStateFlow()

    private val _held = MutableStateFlow(Held(0, 0))
    val held: StateFlow<Held> = _held.asStateFlow()

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

    /** The routine, in one line, for the account screen.

        The routine tool's own state is a year of entries, six
        charts and a season; the account wants what the site's
        account wants, which is its NAME, how many things are in
        it, and how many days have been written. Read separately
        rather than by opening the tool, because the account
        should not pull a year of rows to print a sentence. */
    private val _routineLine = MutableStateFlow<RoutineLine?>(null)
    val routineLine: StateFlow<RoutineLine?> = _routineLine.asStateFlow()

    /* ---------- the thread under a piece or a lesson ---------- */

    private val _thread = MutableStateFlow(ThreadState())
    val thread: StateFlow<ThreadState> = _thread.asStateFlow()

    private var sayer: Say? = null

    fun openThread(slug: String, section: String) {
        _thread.value = ThreadState(slug = slug, section = section, loading = true)
        viewModelScope.launch {
            val answer = reiad.thread(slug)
            val body = answer.value
            _thread.value = ThreadState(
                slug = slug,
                section = section,
                comments = body?.comments.orEmpty(),
                count = body?.count ?: 0,
                loading = false,
                stale = answer.stale,
                problem = if (body == null) {
                    answer.problem ?: "This phone has not read this thread before."
                } else {
                    null
                },
            )
        }
    }

    /**
     * Leaves a comment, and DOES NOT put it in the thread.
     *
     * The endpoint deliberately answers with no row, precisely so
     * a page cannot render what it just sent. Showing your own
     * pending words back to you is the one thing moderation
     * exists to prevent, and "shown immediately and confirmed
     * after" is exactly what `keep()` does one file away: right
     * for a bookmark, wrong here.
     *
     * An admin is the exception and it is the SERVER'S: their own
     * comment is filed live, the answer says so, and the thread is
     * re-read rather than having a row invented for it.
     */
    fun leaveComment(context: android.content.Context, body: String, parentId: Int?) {
        val now = _thread.value
        if (now.slug.isBlank()) return
        val voice = sayer ?: Say(account(context)).also { sayer = it }
        _thread.value = now.copy(posting = true, said = null, wrong = false)
        viewModelScope.launch {
            when (val said = voice.leave(now.slug, now.section, body, parentId)) {
                is Said.Live -> {
                    _thread.value = _thread.value.copy(
                        posting = false,
                        said = "Up now.",
                        wrong = false,
                    )
                    /* Re-read, rather than adding the row here:
                       the server decides what a thread contains
                       and this app has just been told its comment
                       is part of it. */
                    openThread(now.slug, now.section)
                }
                is Said.Queued -> _thread.value = _thread.value.copy(
                    posting = false,
                    said = "Sent. It will appear once it has been read.",
                    wrong = false,
                )
                is Said.Wrong -> _thread.value = _thread.value.copy(
                    posting = false,
                    said = said.why,
                    wrong = true,
                )
            }
        }
    }

    private val _diet = MutableStateFlow(DietState())
    val diet: StateFlow<DietState> = _diet.asStateFlow()

    private var dietStore: Log? = null

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
            keepRoutineGlance(context)
        }
    }

    /**
     * Today's log, the reader's own answers, and what the two make
     * together.
     *
     * Derived in ONE place rather than in the screen, for the
     * reason the routine's own loader gives: a figure and the bar
     * under it must not be able to disagree about what today is.
     */
    /** @param date the day to show. Today by default, and NEVER
        the future: a log is a record, and a plate that has not
        been eaten has no business in one. The screen's arrows
        stop at today for the same reason. */
    fun openDiet(context: android.content.Context, date: String? = null) {
        val store = dietStore ?: Log(account(context)).also { dietStore = it }
        val today = java.time.LocalDate.now().toString()
        val shown = (date ?: _diet.value.shownDate.ifBlank { today })
            .let { if (it > today) today else it }
        _diet.value = DietState(loading = true, shownDate = shown)
        viewModelScope.launch {
            if (account(context).token() == null) {
                _diet.value = DietState(loading = false, signedOut = true)
                return@launch
            }
            val profile = store.profile()
            /* A fortnight behind TODAY, whichever day is shown:
               the trend and the body readings describe the
               person now, not the person on the day being
               edited. It is the shortest window the trend means
               anything over; the long view is /tools/diet/trend. */
            val days = store.days(dayBefore(today, 14))
            val entries = store.entries(shown)
            _diet.value = readDiet(profile, days, entries, today)
                .copy(
                    library = foodLibrary ?: loadFoods(),
                    shownDate = shown,
                )
            keepDietGlance(context)
        }
    }

    /** The summary the diet widgets read, from the SAME totals
        the screen draws (`totalFor`, planned rows filtered), so
        the widget and the screen cannot disagree about today. */
    private fun keepDietGlance(context: android.content.Context) {
        val now = _diet.value
        if (now.today.isBlank() || now.signedOut) return
        /* Only TODAY reaches the widgets. Editing Tuesday's
           forgotten dinner must not put Tuesday's total on the
           home screen with today's date implied. */
        if (now.shownDate.isNotBlank() && now.shownDate != now.today) return
        val day = uk.co.reiad.library.core.diet.totalFor(
            now.entries,
            now.library?.macros ?: listOf("protein", "carbs", "fat", "fibre"),
        )
        viewModelScope.launch {
            val summary = uk.co.reiad.library.data.DietGlance(
                date = now.today,
                kcal = day.kcal.toInt(),
                target = now.target?.kcal ?: 0,
                entries = day.count,
            )
            reiad.keepDietGlance(summary)
            _dietGlance.value = summary
            runCatching { uk.co.reiad.library.widget.DietWidget().updateAll(context) }
        }
    }

    /* ---------- the portion library ----------

       Fetched once per process and held, because it is 57 KB that
       does not change between two presses of Add, and `foods()`
       is already cached on disk under `cache:foods` for the run
       after this one. Null where nothing has ever arrived, which
       the picker says out loud rather than showing an empty
       list. */

    private var foodLibrary: FoodLibrary? = null

    private suspend fun loadFoods(): FoodLibrary? {
        val answer = reiad.foods().value ?: return null
        return FoodLibrary.from(answer)?.also { foodLibrary = it }
    }

    /**
     * One thing eaten, added.
     *
     * `loggedFrom` can REFUSE, and the screen will not have
     * offered an Add that presses where it does. This still
     * checks, because a refusal that reaches here means the two
     * disagree and writing the row anyway would put a figure
     * nobody measured into somebody's log.
     */
    fun addEaten(context: android.content.Context, row: Portion, ate: Ate) {
        val store = dietStore ?: return
        val library = foodLibrary ?: return
        val state = _diet.value
        val today = state.today.ifBlank { java.time.LocalDate.now().toString() }
        val shown = state.shownDate.ifBlank { today }
        val now = java.time.LocalTime.now()
        val entry = loggedFrom(
            row = row,
            ate = ate,
            /* The SHOWN day, which is the whole point of the date
               walk: yesterday's forgotten dinner goes on
               yesterday. */
            date = shown,
            library = library,
            /* The local clock, ONLY on today. The hour a thing
               was eaten is a fact, and stamping the hour of
               remembering onto the day of eating would file
               Tuesday's dinner at Wednesday's clock time in the
               by-hour reading. Absent is the honest value for a
               back-filled row. */
            atTime = if (shown == today) {
                "%02d:%02d".format(now.hour, now.minute)
            } else {
                null
            },
        ) ?: return
        viewModelScope.launch {
            store.addEntry(entry)?.let { _note.value = it }
            openDiet(context)
        }
    }

    /** A weight, saved. A PARTIAL upsert: the day's other columns
        are absent from the body, so this does not erase a waist
        measured this morning. */
    fun weighIn(context: android.content.Context, kg: Double) {
        val store = dietStore ?: return
        val state = _diet.value
        val shown = state.shownDate.ifBlank {
            state.today.ifBlank { java.time.LocalDate.now().toString() }
        }
        _diet.value = state.copy(saving = true)
        viewModelScope.launch {
            /* Said out loud when it fails, for the reason
                `writeDay` gives: a weight that went to a 400 and
                said nothing is a reading the reader believes is
                on their account. The SHOWN day, so a missed
                morning can be back-filled and the trend gets its
                point. */
            store.saveDay(DietDay(date = shown, weightKg = kg))?.let { _note.value = it }
            openDiet(context)
        }
    }

    fun removeEaten(context: android.content.Context, id: String) {
        val store = dietStore ?: return
        viewModelScope.launch {
            store.removeEntry(id)?.let { _note.value = it }
            openDiet(context)
        }
    }

    private fun readDiet(
        profile: DietProfile?,
        days: List<DietDay>,
        entries: List<DietEntry>,
        today: String,
    ): DietState {
        /* The most recent weight rather than today's, and that is
           deliberate: a reader who weighs twice a week still has a
           body, and a screen that showed no BMI on the days
           between would be describing the scale rather than the
           person. */
        val latest = days.firstOrNull { it.weightKg != null }
        val day = days.firstOrNull { it.date == today }
        val body = bodyOf(profile, day ?: latest, java.time.LocalDate.now().year)
            ?: bodyOf(profile, latest, java.time.LocalDate.now().year)

        /* The trend, out of the same fortnight. Day numbers are
           days-before-today so the arithmetic never parses a
           date, and the list arrives newest first, so it is
           reversed into time order for the fit. */
        val weighed = days.filter { it.weightKg != null }
        val points = weighed.map { d ->
            uk.co.reiad.library.core.diet.Point(
                day = -java.time.temporal.ChronoUnit.DAYS.between(
                    java.time.LocalDate.parse(d.date),
                    java.time.LocalDate.parse(today),
                ).toInt(),
                kg = d.weightKg ?: 0.0,
            )
        }.sortedBy { it.day }
        val smoothed = uk.co.reiad.library.core.diet.trend(points).lastOrNull()?.kg
        val perWeek = uk.co.reiad.library.core.diet.slopePerWeek(points)

        val resting = body?.let { restingBurn(it) }
        val maintenance = resting?.let {
            estimatedBurn(it.kcal, activityFactor(profile?.activity ?: "sedentary"))
        }
        val goal = when (profile?.goal) {
            "gain" -> GoalKind.GAIN
            "maintain" -> GoalKind.MAINTAIN
            else -> GoalKind.LOSE
        }

        /* The burn the reader's OWN fortnight implies, off the
           same two series this fetch already holds: the weights
           above, and each day's kcal rollup. `learnedBurn` does
           all its own refusing, so an unsupported fortnight is a
           null here rather than a wide guess drawn anyway. */
        val learned = uk.co.reiad.library.core.diet.learnedBurn(
            points,
            days.mapNotNull { d ->
                d.kcal?.takeIf { it > 0 }?.let {
                    uk.co.reiad.library.core.diet.Intake(
                        day = -java.time.temporal.ChronoUnit.DAYS.between(
                            java.time.LocalDate.parse(d.date),
                            java.time.LocalDate.parse(today),
                        ).toInt(),
                        kcal = it,
                    )
                }
            },
        )

        return DietState(
            loading = false,
            trendKg = smoothed,
            perWeek = perWeek,
            learned = learned,
            /* Only in a deficit, because the figure is what a
               deficit costs: grams per kilogram of LEAN mass,
               rising with the chosen rate. */
            protein = if (goal == GoalKind.LOSE && body != null) {
                uk.co.reiad.library.core.diet.proteinFloor(
                    fatEstimate(body).leanKg,
                    profile?.ratePct ?: 0.5,
                )
            } else {
                null
            },
            today = today,
            profile = profile,
            day = day,
            entries = entries,
            body = body,
            maintenance = maintenance,
            target = if (body == null || resting == null || maintenance == null) {
                null
            } else {
                target(
                    body = body,
                    maintenance = maintenance,
                    restingKcal = resting.kcal,
                    kind = goal,
                    ratePct = profile?.ratePct ?: 0.5,
                )
            },
        )
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
            season = seasonOf(today, seasonsFrom(words())),
            moods = moodsFrom(words()),
            greeting = hello(),
            /* The birds and the garden, off the task the routine
               names for them. Both are counted over the WHOLE
               history rather than a window, which is the one
               thing that makes them different from a streak: a
               fortnight off does not take a bird away.

               Keyed by task id rather than by position, so a
               reader who reorders their list keeps their flock.
               A routine with no such task simply has none, which
               is why this is a lookup and not an assumption. */
            /* The two ids and the plant list come DOWN from the
               site where the manifest has landed, and fall back
               to the compiled tables where it has not. A fifth
               plant is a fetch rather than a release. */
            flock = flock(everMarked(entries, birdTask())),
            garden = garden(everMarked(entries, gardenTask()), gardenFrom(words())),
            loading = false,
        )
    }

    private fun hello(): String = greeting(java.time.LocalTime.now().hour).first

    /** The routine's words as the site last sent them, or null. */
    private fun words(): uk.co.reiad.library.core.RoutineWords? = _site.value?.routine

    private fun birdTask(): String = words()?.grown?.get("birds") ?: BIRD_TASK

    private fun gardenTask(): String = words()?.grown?.get("plants") ?: GARDEN_TASK

    private companion object {
        /* The two tasks the drawings hang on, by the ids the
           site's shipped template gives them: `GROWN` in
           `shared/routine.ts`.

           They are three letters rather than words on purpose,
           and the first draft of this line guessed `birds` and
           `plants`, which would have drawn an empty sky and a
           bare garden on every phone with every check passing.
           `RoutineIdsTest` asks a real routine whether they
           exist. */
        const val BIRD_TASK = "brd"
        const val GARDEN_TASK = "pln"
    }

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
            /* SAID OUT LOUD WHEN IT FAILS. This dropped the
               result for as long as it existed, and a whole day
               of marks went to a 400 that nothing reported: the
               screen had already drawn them, so there was nothing
               to see. A write that can fail and cannot say so is
               a write that loses work quietly. */
            val problem = routineStore?.save(routineId, entry)
            _routine.value = _routine.value.copy(saving = false)
            if (problem != null) _note.value = problem
        }
        host?.let { keepRoutineGlance(it) }
    }

    /** The summary the home-screen widget reads, kept in step
        with the screen: the same `done()` the day page draws, so
        the two can never disagree about today. */
    private fun keepRoutineGlance(context: android.content.Context) {
        val now = _routine.value
        val counting = now.shape.tasks.filter { it.counts && !it.archived }
        if (now.today.isBlank() || counting.isEmpty()) return
        val marks = now.entry?.marks.orEmpty()
        val markedNow = counting.count { (marks[it.id] ?: 0.0) > 0 }
        viewModelScope.launch {
            val summary = uk.co.reiad.library.data.RoutineGlance(
                date = now.today,
                marked = markedNow,
                of = counting.size,
            )
            reiad.keepRoutineGlance(summary)
            _routineGlance.value = summary
            runCatching { uk.co.reiad.library.widget.RoutineWidget().updateAll(context) }
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

    /* ---------- saved checks ---------- */

    private val _scenarios = MutableStateFlow<List<Scenario>>(emptyList())
    val scenarios: StateFlow<List<Scenario>> = _scenarios.asStateFlow()

    private val _saveNote = MutableStateFlow<String?>(null)
    val saveNote: StateFlow<String?> = _saveNote.asStateFlow()

    /** This check, under a name, on the account.

        What is stored is the QUERY STRING rather than a blob of
        the fifty-six fields: it is the format the stock check has
        shared analyses in since it was written, `ShareTest`
        asserts it byte-for-byte against the site's, and a second
        serialisation would be a second thing to keep in step with
        the model.

        The summary is one line of the ANSWER, so the account can
        list a check without loading the model that produced it. */
    fun saveCheck(name: String) {
        val shelf = library ?: run {
            _saveNote.value = "Sign in to keep a check."
            return
        }
        val state = _stock.value
        val words = _words.value
        viewModelScope.launch {
            val query = shareQuery(
                state.inputs,
                state.weights,
                style = state.style.takeIf { it != "custom" },
                lang = state.lang.takeIf { it != "en" },
            )
            val a = analyse(state.inputs, state.weights)
            val problem = shelf.saveScenario(
                tool = "stock",
                name = name,
                query = query,
                summary = summarise(a, words),
            )
            /* The tool's own phrase where it worked, and the
               DATABASE'S sentence where it did not: "that did not
               save" is not actionable and the constraint's own
               name is. */
            _saveNote.value = if (problem == null) {
                words?.t(Keys.SAVED, state.lang) ?: "Saved."
            } else {
                problem
            }
            if (problem == null) _scenarios.value = shelf.scenarios()
        }
    }

    fun readScenarios() {
        val shelf = library ?: return
        viewModelScope.launch { _scenarios.value = shelf.scenarios() }
    }

    fun removeScenario(id: String) {
        val shelf = library ?: return
        viewModelScope.launch {
            shelf.removeScenario(id)?.let { _note.value = it }
            _scenarios.value = shelf.scenarios()
        }
    }

    /** Opens a saved check with its numbers back in the fields.

        Through `readShare`, which is the same decoder every link
        anybody has pasted goes through, so a check saved on a
        laptop opens here with the same figures. */
    fun openScenario(scenario: Scenario) {
        val shared = readShare(scenario.inputs.query)
        _stock.value = StockState(
            inputs = shared.inputs,
            weights = shared.weights,
            style = shared.style,
            lang = shared.lang ?: _stock.value.lang,
        )
        openTools()
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

    /** Keep this school, or stop keeping it.

        Following queues the fetch; unfollowing cancels it AND
        leaves what is already held alone, which is deliberate: a
        reader who stops following has said "do not fetch more",
        not "delete what I have". Forgetting is its own button in
        settings, and it says what it touches. */
    fun keepSchool(context: android.content.Context, school: String) {
        viewModelScope.launch {
            val after = Shelf(context).toggle(school)
            _shelf.value = after
            if (school in after) SchoolWorker.fetch(context, school)
            else SchoolWorker.stop(context, school)
            _held.value = heldFor(context, school)
        }
    }

    /** What is on the shelf, and how much of ONE school is here.

        Per school rather than in total, because the number is
        drawn beside "12 of 60" on that school's own ladder and a
        total there would be a number about somewhere else. The
        settings sheet asks for the total separately. */
    fun readShelf(context: android.content.Context, school: String? = null) {
        viewModelScope.launch {
            _shelf.value = Shelf(context).schools().first()
            _held.value = if (school == null) heldAll(context) else heldFor(context, school)
        }
    }

    fun forgetHeldNow(context: android.content.Context) {
        viewModelScope.launch {
            forgetHeld(context)
            _held.value = heldAll(context)
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
            /* And the home screen. A tick is the ONE moment the
               progress widget's answer changes, which is why its
               `updatePeriodMillis` is 0: a widget that polled
               would wake the app on a schedule to redraw four
               numbers that had not moved. */
            host?.let { context -> runCatching { ProgressWidget().updateAll(context) } }
        }
    }

    /** Where the app is running, for the worker. Null until the
        account has been asked for once, which every launch does. */
    private var host: android.content.Context? = null

    private fun queueSync() {
        host?.let { if (account != null) SyncWorker.soon(it) }
    }

    fun ticksOf(key: String): Set<String> = _ticks.value[key].orEmpty()

    /* ---------- the market board ----------

       Fetched once per launch, like the pieces, and only when
       the board actually holds the widget: three RSS feeds read
       on a Worker is not a request to make for a reader who has
       taken it off. */

    private val _news = MutableStateFlow<List<Story>>(emptyList())
    val news: StateFlow<List<Story>> = _news.asStateFlow()

    fun fetchNews() {
        if (_news.value.isNotEmpty()) return
        viewModelScope.launch {
            reiad.news().value?.let {
                _news.value = it.items
                /* The widget reads the same cache this fetch just
                   wrote, so this is the moment its answer changed
                   and the only moment it needs redrawing. */
                host?.let { context -> runCatching { NewsWidget().updateAll(context) } }
            }
        }
    }

    /* ---------- the daily reminder ----------

       This handset's, not the account's: see `REMIND_KEY`. */

    val remindAt: StateFlow<String?> = reiad.remindAt
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun remindAt(context: android.content.Context, at: String?) {
        viewModelScope.launch {
            reiad.setRemindAt(at)
            RemindWorker.at(context, RemindWorker.parse(at))
        }
    }

    /* ---------- the board the reader arranged ----------

       Straight off the store, because it is one small list read
       on one screen. Null means "never arranged", which is not
       the same as an empty board: `layoutOf` falls back for the
       first and honours the second. Saving stamps a `ts` and
       queues the exchange like any other synced key. */

    val board: StateFlow<List<String>?> = reiad.board
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun saveBoard(next: List<String>) {
        viewModelScope.launch {
            reiad.saveBoard(next)
            queueSync()
        }
    }

    fun resetBoard() {
        viewModelScope.launch {
            reiad.resetBoard()
            queueSync()
        }
    }

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
    fun visited(context: android.content.Context, school: School, mark: Bookmark) {
        viewModelScope.launch {
            reiad.remember(school, mark)
            _bookmarks.value = _bookmarks.value + (school.id to mark)
            queueSync()
            /* And the home screen, which is the ONE moment its
               answer changes.

               `updatePeriodMillis` is 0 in the widget's own XML
               for exactly this reason: a widget that polled would
               wake the app on a schedule to redraw a sentence
               that had not changed since the last time. */
            runCatching { ContinueWidget().updateAll(context) }
        }
    }

    /* ---------- the account ---------- */

    private val _reader = MutableStateFlow<Reader?>(null)
    val reader: StateFlow<Reader?> = _reader.asStateFlow()

    /** Whether this account can open the courses shelf: the
        server's own answer, never a guess from a token. */
    private val _mine = MutableStateFlow(false)
    val mine: StateFlow<Boolean> = _mine.asStateFlow()

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

    /** The three questions, as the reader is answering them.

        The FORM's state rather than the row's: it is seeded from
        the account and never over anything already typed, because
        a reader who starts filling this in while the profile is
        still in flight must not have it taken away underneath
        them. */
    private val _setup = MutableStateFlow(SetupState())
    val setup: StateFlow<SetupState> = _setup.asStateFlow()

    /** Where the reader stands in each school whose ladder has
        arrived. A school absent from this list is a school this
        phone has not read yet, and the account draws nothing for
        it: "you have finished nothing" and "this has not loaded"
        must not look the same. */
    private val _paths = MutableStateFlow<List<Path>>(emptyList())
    val paths: StateFlow<List<Path>> = _paths.asStateFlow()

    private val _daysActive = MutableStateFlow<Set<String>>(emptySet())
    val daysActive: StateFlow<Set<String>> = _daysActive.asStateFlow()

    /** Today's routine summary, mirrored from the cache the
        launcher widget reads, so the board's routine widget and
        the home-screen one draw from one record. */
    private val _routineGlance =
        MutableStateFlow<uk.co.reiad.library.data.RoutineGlance?>(null)
    val routineGlance: StateFlow<uk.co.reiad.library.data.RoutineGlance?> =
        _routineGlance.asStateFlow()

    private val _dietGlance =
        MutableStateFlow<uk.co.reiad.library.data.DietGlance?>(null)
    val dietGlance: StateFlow<uk.co.reiad.library.data.DietGlance?> =
        _dietGlance.asStateFlow()

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
    /** Whether this was the sign-in redirect.

        Answered rather than assumed, because the SAME activity
        receives both this and every https link to the site: a
        caller that could not tell them apart would either route a
        sign-in callback to a 404 or swallow every shared link. */
    fun isArrival(uri: String): Boolean = uri.startsWith("uk.co.reiad.library://auth")

    fun arrived(context: android.content.Context, uri: String): Boolean {
        if (!isArrival(uri)) return false
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
        return true
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
            /* Whether the courses shelf answers this account.
               Asked once per sign-in, of the endpoint itself. */
            _mine.value = shelf.courses()
            /* Seeded, never assigned: `seeded()` is a null-or-blank
               test on every field for the reason above. The
               reader's own name off the token is the fallback, so
               somebody who has never saved sees their own name
               rather than an empty box. */
            _setup.value = _setup.value.seeded(
                profile = shelf.profile(),
                fallbackName = account?.reader?.first()?.name.orEmpty(),
                started = startedIn(_ticks.value),
            )
        }
        readPaths()
        readScenarios()
        readRoutineLine()
    }

    /** The routine's name, its size, and how many days have been
        written, without opening the tool.

        The store is BUILT here where it is missing, rather than
        returning early. It used to be created only by
        `openRoutine`, so on the account screen it was always
        null and this section never appeared for anybody who had
        not already opened the routine tool this session: a panel
        that works after you visit the thing it is a summary of. */
    fun readRoutineLine() {
        val where = host ?: return
        val store = routineStore ?: Days(account(where)).also { routineStore = it }
        viewModelScope.launch {
            val row = store.routine()
            _routineLine.value = if (row == null) {
                RoutineLine(built = false)
            } else {
                /* A year, which is what "days written" means on
                   the site's own account panel. The tool asks for
                   the same window. */
                val today = java.time.LocalDate.now().toString()
                RoutineLine(
                    built = true,
                    name = row.name,
                    tasks = row.tasks.size,
                    written = store.entries(dayBefore(today, 365)).size,
                )
            }
        }
    }

    /** Where the reader stands in each school.

        The ladders come from the CACHE first and the network
        second, which `Reiad.fetch` already does, so this is four
        reads off disk for somebody who has opened the schools and
        four small requests for somebody who has not. It runs on
        the account screen only, which is a screen a reader opened
        deliberately.

        The ticks, the bookmark and the checkpoints are all this
        phone's. That split is the rule: the ladder is the
        server's and the ticks are ours. */
    fun readPaths() {
        val manifest = _site.value ?: return
        viewModelScope.launch {
            val found = mutableListOf<Path>()
            for (school in manifest.ladders) {
                val which = School.of(school.key) ?: continue
                val answer = reiad.ladder(school.key)
                val stages = answer.value?.stages ?: continue
                found.add(
                    Path(
                        school = school,
                        at = standingOf(
                            ladder = rungsOf(stages),
                            read = reiad.ticksNow(which),
                            last = reiad.bookmark(which).first()?.id,
                            checks = reiad.checkpoints(which).first(),
                        ),
                    ),
                )
            }
            _paths.value = found
        }
    }

    /** An ISO instant, for `setup_at`. Written out because
        `java.time` needs API 26, which is this app's minimum, and
        because the column is a `timestamptz`: a local time with
        no zone on it is a time Postgres has to guess about. */
    private fun nowIso(): String = java.time.Instant.now().toString()

    fun editSetup(next: SetupState) { _setup.value = next }

    /** Saves the three answers, and stamps `setup_at` so the
        screen stops asking.

        Set on the first save whether or not anything was ticked:
        somebody who saves a name and nothing else has been
        through setup. */
    fun saveProfile(stampOnly: Boolean = false) {
        val shelf = library ?: return
        val now = _setup.value
        if (!stampOnly && now.name.isBlank()) {
            _setup.value = now.copy(note = "A name cannot be empty.", wrong = true)
            return
        }
        _setup.value = now.copy(busy = true, note = null, wrong = false)
        viewModelScope.launch {
            val stamp = nowIso()
            val problem = if (stampOnly) {
                shelf.saveProfile(setupAt = stamp)
            } else {
                shelf.saveProfile(
                    displayName = now.name.trim(),
                    following = now.following.toList(),
                    pace = now.pace,
                    setupAt = stamp,
                )
            }
            _setup.value = _setup.value.copy(
                busy = false,
                asked = _setup.value.asked || problem == null,
                note = when {
                    problem != null -> problem
                    stampOnly -> "Fine. Everything above is here whenever you want it."
                    else -> "Saved."
                },
                wrong = problem != null,
            )
        }
    }

    fun addTarget(target: Target) {
        val shelf = library ?: return
        viewModelScope.launch {
            shelf.addTarget(target)?.let { _note.value = it }
            _targets.value = shelf.targets()
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
            /* The optimistic update above is what makes the
               control feel instant, and this is what keeps it
               honest: a note that hit a 400 says so and the list
               is re-read either way, so the screen ends up
               showing what the account actually holds. */
            shelf.keep(url, title, kind, saved, note)?.let { _note.value = it }
            _kept.value = shelf.kept()
        }
    }

    fun removeTarget(id: String) {
        val shelf = library ?: return
        viewModelScope.launch {
            shelf.removeTarget(id)?.let { _note.value = it }
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
            val problem = shelf.eraseAll()
            /* The mirror comes off either way. Leaving a phone
               full of rows the account no longer has would put
               every one of them straight back on the next
               exchange. */
            sync?.forget()
            _kept.value = emptyList()
            _targets.value = emptyList()
            _daysActive.value = emptySet()
            loadMarks()
            _erasing.value = problem
                ?: "Erased. Nothing of yours is on this account or on this phone."
        }
    }

    fun sendLink(context: android.content.Context, email: String) {
        viewModelScope.launch {
            _authProblem.value = null
            /* The server's own words when it refuses. "That email
               would not send" was the whole of what a reader saw
               for a rate limit, a malformed address and a project
               with email sign-in switched off alike, which is
               three different fixes reported as one mystery. */
            val wrong = account(context).sendLink(email)
            _linkSent.value = wrong == null
            _authProblem.value = wrong
        }
    }

    /** Google, and what happens when there is no browser.

        `signIn` returned Unit and swallowed the exception, so on
        a phone with no Custom Tabs provider the button did
        nothing and said nothing. */
    fun signInWith(context: android.content.Context, provider: String) {
        _authProblem.value = account(context).signIn(provider)
    }

    fun signOut(context: android.content.Context) {
        viewModelScope.launch {
            sync?.forget()
            account(context).signOut()
            _reader.value = null
            _kept.value = emptyList()
            _targets.value = emptyList()
            _exported.value = null
            _mine.value = false
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
        viewModelScope.launch { fetchPieces() }
    }

    private suspend fun fetchPieces() {
        val answer = reiad.pieces()
        answer.value?.let { _pieces.value = it.articles }
        if (answer.stale) _stale.value = true
    }

    /** One piece, by the slug a link named.

        Suspending rather than fire and forget, because the caller
        is a deep link: it has to WAIT for the list before it can
        say whether the address resolves, and a version that
        returned immediately would send every shared article to
        its section hub. */
    suspend fun pieceBySlug(section: String, slug: String): Piece? {
        if (_pieces.value.isEmpty()) fetchPieces()
        return _pieces.value.firstOrNull { it.section == section && it.slug == slug }
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

    /* ============================================================
       LAST IN THE CLASS, AND THAT IS THE FIX FOR A LAUNCH CRASH.

       This block sat three hundred lines up, above the fields it
       writes, and "build 404c6e8 doesn't open, just crashes" was
       the whole of the symptom. Kotlin runs initializers in
       source order, and `viewModelScope` dispatches on
       Main.immediate: a coroutine launched here can run, or
       resume from a DataStore read that completed without
       suspending, BEFORE the declarations below the block have
       assigned their fields. `_routineGlance.value` then throws
       an NPE inside a coroutine nothing catches, and an uncaught
       coroutine exception is process death, every launch, on
       exactly the phones where the store answers fastest.

       An init block after every property cannot meet a null
       field, however the coroutines interleave. Do not move it
       up, however lonely it looks down here; `LaunchTest` fails
       on any uncaught launch exception either way.
       ============================================================ */
    init {
        refresh()
        viewModelScope.launch {
            _routineGlance.value = reiad.cachedRoutineGlance()
            _dietGlance.value = reiad.cachedDietGlance()
            /* The board's streak widget reads the same local set
               the account page draws, and needs it before the
               account screen has ever been opened. */
            _daysActive.value = reiad.daysActive()
        }
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
    val shelf by model.shelf.collectAsState()
    val held by model.held.collectAsState()
    val stockState by model.stock.collectAsState()
    val calcState by model.calc.collectAsState()
    val liveState by model.live.collectAsState()
    val routineState by model.routine.collectAsState()
    val dietState by model.diet.collectAsState()
    val toolNote by model.toolNote.collectAsState()
    val openPiece by model.open.collectAsState()
    val reader by model.reader.collectAsState()
    val authProblem by model.authProblem.collectAsState()
    val linkSent by model.linkSent.collectAsState()
    val kept by model.kept.collectAsState()
    val mine by model.mine.collectAsState()
    val targets by model.targets.collectAsState()
    val setupState by model.setup.collectAsState()
    val paths by model.paths.collectAsState()
    val scenarios by model.scenarios.collectAsState()
    val saveNote by model.saveNote.collectAsState()
    val routineLine by model.routineLine.collectAsState()
    val threadState by model.thread.collectAsState()
    val daysActive by model.daysActive.collectAsState()
    val routineGlance by model.routineGlance.collectAsState()
    val dietGlance by model.dietGlance.collectAsState()
    val exported by model.exported.collectAsState()
    val erasing by model.erasing.collectAsState()
    val checks by model.checks.collectAsState()
    val bookmarks by model.bookmarks.collectAsState()
    /* The voice, collected ONCE for the whole app: the piece
       screen's controls and the card on the board are two views
       of one reading, and two collectors would be two answers to
       "is it paused". */
    val speaking by uk.co.reiad.library.read.Reader.state.collectAsState()
    val board by model.board.collectAsState()
    val news by model.news.collectAsState()
    val remindAt by model.remindAt.collectAsState()

    /* The reminder's permission, asked for at the moment somebody
       turns it on rather than at launch: a prompt on first run is
       a question about a feature nobody has met yet, and the
       usual answer to that is no. Refusal is not an error, it is
       an answer, and `RemindWorker` posts silently where it was
       given. */
    val askToNotify = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { }
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
        Where.Diet -> Accents.GOLD
        Where.Skills -> Accents.GREEN
        Where.Portfolio -> Accents.PLUM
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

    val arrival by arrivals.collectAsState()
    /* A link that arrives goes to the page it NAMES.

       Two things arrive on this activity and only one of them was
       handled: the sign-in redirect, and every https link to the
       site, which the manifest registers as an app link. A shared
       lesson opened the app on the front page for eleven blocks,
       which is worse than not registering at all: the reader has
       been taken away from the page they asked for and it is now
       a back-press and a browser away.

       Keyed on the manifest as well as the URL, because a link
       tapped from cold arrives before the first fetch has landed
       and the school keys are what decide where it goes: without
       that key it would resolve once, against nothing, and stay
       resolved. */
    LaunchedEffect(arrival, site) {
        val url = arrival ?: return@LaunchedEffect
        if (model.arrived(context, url)) return@LaunchedEffect
        /* The palette computed here rather than read from
           `LocalReiad`, because this effect runs above the
           theme: it has to answer a link that arrives before
           anything is on screen. */
        followTo(destinationOf(url, site), model, context, site, coloursOf(accent, dark)) {
            where = it
        }
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
        Where.Diet -> DIET_KEY
        Where.Skills -> SKILLS_KEY
        Where.Portfolio -> PORTFOLIO_KEY
        Where.Home -> null
    }

    /* The reader's own type size, applied at the ONE place every
       screen goes through. It has been in `reader-prefs` and
       syncing since the app was written, and nothing read it. */
    ReiadTheme(
        accent = accent,
        dark = dark,
        scale = scaleOf(prefs.text),
        measure = measureOf(prefs.measure),
    ) {
        val colours = LocalReiad.current
        /* ONE sway for the whole app: the shell's ambient field
           and the cards' glint read the same sensor, so the
           light behind the page and the light on it lean
           together. Two instances would be two listeners at
           50Hz for the same three numbers. */
        val sway = rememberSway()
        Surface(Modifier.fillMaxSize(), color = colours.paper) {
            CompositionLocalProvider(
                LocalGlassLook provides remember(prefs) { glassLookOf(prefs) },
            ) {
            Shell(
                state = ShellState(site, current, audience, drawer, reader != null),
                sway = sway,
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
                onSettings = { settings = true; model.readShelf(context) },
                /* The account, one tap from every screen, which is
                   where the site keeps it. It was behind More and
                   four groups of drawer, and the report that came
                   back was that pressing account went back to the
                   front page: it did, because the thing being
                   pressed was the bar's name. */
                onAccount = { where = Where.Account },
                onAudience = { model.chooseAudience(it) },
            ) {
            /* Every link in every body, answered. The href goes
               through the same resolver a shared link does, so a
               glossary term opens as a lesson, a piece linked
               from a piece opens in place, and everything else
               opens on the site in the reader's own browser
               rather than doing nothing under the accent. */
            val scope = rememberCoroutineScope()
            val openLink: (String) -> Unit = { href ->
                scope.launch {
                    followTo(destinationOf(href, site), model, context, site, colours) {
                        where = it
                    }
                }
            }
            CompositionLocalProvider(LocalOpenLink provides openLink) {
            /* ---------- how a screen arrives ----------

               The platform's own fade-through rather than a cut:
               the leaving screen drops fast, the arriving one
               fades up from a whisker small, on the site's own
               timings. A cut is what made every navigation feel
               like a page load, and it is the single cheapest
               difference between "a website in a wrapper" and an
               app.

               Under reduced motion it IS a cut, which is what
               that setting asks for. */
            val reduced = rememberReducedMotion()
            AnimatedContent(
                targetState = where,
                transitionSpec = {
                    if (reduced) {
                        EnterTransition.None togetherWith ExitTransition.None
                    } else {
                        (
                            fadeIn(tween(Motion.ENTER_MS, delayMillis = 80)) +
                                scaleIn(
                                    initialScale = 0.97f,
                                    animationSpec = tween(Motion.ENTER_MS, delayMillis = 80),
                                )
                            ).togetherWith(fadeOut(tween(90)))
                    }
                },
                label = "screen",
            ) { here ->
            when (here) {
                Where.Skills -> {
                    BackHandler { where = Where.Home }
                    SkillsScreen(
                        /* The LEARN group of the nav table, which
                           is what the site's own `/skills` reads.
                           Not `SKILLS` in `content.ts`: the nav
                           item is what carries each school's own
                           colour, and the site has a comment
                           about the day this page did without
                           them. */
                        group = site?.nav?.firstOrNull { it.id == "learn" },
                        head = site?.heads?.get(SKILLS_KEY),
                        bottomPadding = barClearance(),
                        onOpen = { item ->
                            /* Through the ONE function that
                               decides where a nav item goes, so a
                               row here reaches the same screen as
                               the same row in the menu. */
                            where = if (opensHere(site, item)) {
                                goTo(model, context, site, item, where)
                            } else {
                                openOnSite(context, item.href, colours)
                                where
                            }
                        },
                        mine = mine,
                        /* `/skills/courses`, which is the route
                           the site actually serves. It was
                           `/courses`, which is a 404, and a
                           button that opens a not-found page is
                           the same to a reader as a button that
                           does nothing: it was reported as
                           exactly that. A path this app writes
                           by hand rather than reads from the
                           manifest is a path somebody has to
                           check against the live site, and this
                           one now has been. */
                        onOpenCourses = { openOnSite(context, COURSES_HREF, colours) },
                    )
                }

                Where.Portfolio -> {
                    BackHandler { where = Where.Home }
                    PortfolioScreen(
                        /* Out of the manifest's own `pages`, by
                           the group the site files them under. A
                           case study added on the site is on this
                           screen with no release. */
                        cases = site?.pages.orEmpty().filter { it.group == "case" },
                        head = site?.heads?.get(PORTFOLIO_KEY),
                        bottomPadding = barClearance(),
                        onOpen = { page -> openOnSite(context, page.url, colours) },
                    )
                }

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
                        bottomPadding = barClearance(),
                        onGoogle = { model.signInWith(context, "google") },
                        onLink = { model.sendLink(context, it) },
                        onSignOut = { model.signOut(context) },
                        setup = setupState,
                        /* Both vocabularies out of the MANIFEST.
                           Each is a CHECK constraint in Postgres,
                           so a fourth pace offered here that the
                           constraint has not heard of is a 400 on
                           the whole patch: one list, and it is the
                           site's. */
                        schools = site?.ladders.orEmpty(),
                        paces = site?.profile?.paces.orEmpty(),
                        targetKinds = site?.profile?.targetKinds.orEmpty(),
                        started = startedIn(ticks),
                        onSetupChange = { model.editSetup(it) },
                        onSaveProfile = { model.saveProfile() },
                        onNotNow = { model.saveProfile(stampOnly = true) },
                        onAddTarget = { model.addTarget(it) },
                        paths = paths,
                        onOpenSchool = { school ->
                            model.openLadder(school)
                            where = Where.Ladder(school)
                        },
                        scenarios = scenarios,
                        onOpenScenario = { row ->
                            model.openScenario(row)
                            where = Where.Stock
                        },
                        onRemoveScenario = { model.removeScenario(it) },
                        routine = routineLine,
                        onOpenRoutine = {
                            model.openRoutine(context)
                            where = Where.Routine
                        },
                    )
                }

                is Where.Hub -> {
                    BackHandler { where = Where.Home }
                    ReadingHub(
                        title = here.title,
                        pieces = pieces.filter { it.section == here.section },
                        stale = stale,
                        bottomPadding = barClearance(),
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
                    /* A relative link inside a piece means "in
                       this section", which only this screen
                       knows. */
                    CompositionLocalProvider(
                        LocalOpenLink provides { href ->
                            openLink(
                                if (href.startsWith("http") || href.startsWith("/")) href
                                else "/${here.section}/$href",
                            )
                        },
                    ) {
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
                        bottomPadding = barClearance(),
                        onOpen = { piece ->
                            model.openPiece(piece)
                            where = Where.Reading2(here.section, piece)
                        },
                        onBack = {
                            model.closePiece()
                            where = Where.Hub(here.section, sectionTitle(site, here.section))
                        },
                        thread = threadState,
                        onLeaveComment = { body, parent ->
                            model.leaveComment(context, body, parent)
                        },
                        onRetryThread = { model.openThread(shown.slug, here.section) },
                    )
                    /* Keyed on the SLUG, so walking from one piece
                       to the next in the same section re-reads the
                       thread rather than leaving the last one's
                       comments under the new piece. */
                    LaunchedEffect(shown.slug) { model.openThread(shown.slug, here.section) }
                    }
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
                        bottomPadding = barClearance(),
                        onWrite = { slot, text -> which?.let { model.write(it, slot, text) } },
                        onTickDay = { id -> which?.let { model.tickDay(it, id) } },
                        onReveal = { day -> model.reveal(here.stage.slug, day) },
                        onBack = { where = Where.Ladder(here.school) },
                    )
                }

                Where.Diet -> {
                    BackHandler { where = Where.Home }
                    DietScreen(
                        state = dietState,
                        onWeight = { model.weighIn(context, it) },
                        onRemove = { model.removeEaten(context, it) },
                        onAdd = { row, ate -> model.addEaten(context, row, ate) },
                        onDay = { model.openDiet(context, it) },
                        /* Eleven of the tool's fourteen pages are
                           still the site's, and so are the barcode
                           scanner and the two public databases.
                           This opens the log rather than
                           pretending the app has all of it. */
                        onOpenSite = {
                            openOnSite(context, "/tools/diet/log", colours)
                        },
                        words = site?.dietWords ?: uk.co.reiad.library.core.DietWords(),
                        lang = prefs.lang,
                        contentPadding = PaddingValues(
                            start = Gap.s8, end = Gap.s8,
                            top = topClearance(), bottom = barClearance(),
                        ),
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
                            top = topClearance(), bottom = barClearance(),
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
                        onRetry = { model.openLive(context) },
                        contentPadding = PaddingValues(
                            start = Gap.s8, end = Gap.s8,
                            top = topClearance(), bottom = barClearance(),
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
                                top = topClearance(), bottom = barClearance(),
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
                            /* Null signed out, which is what the
                               site does too: a control that
                               cannot do anything is a promise
                               this screen cannot keep. */
                            onSave = if (reader != null) {
                                { name -> model.saveCheck(name) }
                            } else {
                                null
                            },
                            saveNote = saveNote,
                            onExport = { model.exportCheck(context) },
                            onLang = { model.chooseToolLang(it) },
                            note = toolNote,
                            contentPadding = PaddingValues(
                                start = Gap.s8, end = Gap.s8,
                                top = topClearance(), bottom = barClearance(),
                            ),
                        )
                    }
                }

                is Where.Group -> {
                    BackHandler { where = Where.Home }
                    GroupScreen(
                        group = here.group,
                        accents = site?.accents.orEmpty(),
                        bottomPadding = barClearance(),
                        canOpenHere = { item -> opensHere(site, item) },
                        onOpenHere = { item -> where = goTo(model, context, site, item, where) },
                        heads = site?.heads.orEmpty(),
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
                    board = board,
                    daysActive = daysActive,
                    routineGlance = routineGlance,
                    dietGlance = dietGlance,
                    sway = sway,
                    bookmarks = bookmarks,
                    pieces = pieces,
                    lang = prefs.lang,
                    signedIn = reader != null,
                    onBoard = { model.saveBoard(it) },
                    onResetBoard = { model.resetBoard() },
                    onPiece = { piece ->
                        model.openPiece(piece)
                        where = Where.Reading2(piece.section, piece)
                    },
                    /* Into that school's ladder, which is where
                       the bookmark is drawn: the row is
                       highlighted and the lesson is one press
                       away. Straight into the lesson would need
                       the ladder fetched first anyway, and would
                       leave a reader with no way back to the
                       school they were in the middle of. */
                    onResume = { key, _ ->
                        site?.ladders?.firstOrNull { it.key == key }?.let { school ->
                            model.openLadder(school)
                            where = Where.Ladder(school)
                        }
                    },
                    news = news,
                    onNeedNews = { model.fetchNews() },
                    /* Somebody else's page, in the reader's own
                       browser, with the address bar visible: this
                       app does not host The Business Standard and
                       should not look as though it does. */
                    onStory = { story -> openOnSite(context, story.url, colours) },
                    kept = kept,
                    targets = targets,
                    onKept = { row -> openOnSite(context, row.url, colours) },
                    /* The voice, if there is one. `Reader` is an
                       object rather than state, so its title and
                       slug are read HERE, where the state flow
                       that changes with them is already being
                       collected: reading them inside the board
                       would be reading a field nothing tells
                       Compose about. */
                    speaking = speaking,
                    readingTitle = uk.co.reiad.library.read.Reader.title,
                    onReadingOpen = {
                        /* Through the same resolver a link in a
                           body goes through, so there is one
                           answer to "what does this href open"
                           in the whole app. */
                        val href = uk.co.reiad.library.read.Reader.from
                        if (href.isNotBlank()) {
                            scope.launch {
                                followTo(
                                    destinationOf(href, site), model, context, site, colours,
                                ) { where = it }
                            }
                        }
                    },
                )

                is Where.Ladder -> {
                    BackHandler { where = Where.Home }
                    /* Counted when the ladder opens and again
                       whenever the school changes, because the
                       background fetch is still arriving: a
                       reader watching "3 of 60" become "60 of 60"
                       is the whole of what this feature promises
                       being kept in front of them. */
                    LaunchedEffect(here.school.key) {
                        model.readShelf(context, here.school.key)
                    }
                    Ladder(
                        school = here.school,
                        stages = stages,
                        ticks = ticks[here.school.key].orEmpty(),
                        stale = stale,
                        bookmark = bookmarks[here.school.key],
                        following = here.school.key in shelf,
                        held = held,
                        onKeep = { model.keepSchool(context, here.school.key) },
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
                                context,
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
                    /* A link inside a LESSON is written relative
                       to its stage, `dividend.html`, so only
                       this screen can resolve it. Resolved, it
                       goes through the same follower as every
                       other address, which is what turns a
                       glossary term into the lesson it is. */
                    CompositionLocalProvider(
                        LocalOpenLink provides { href ->
                            openLink(resolveHref(href, here.school.key, here.stage))
                        },
                    ) {
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
                    held = held,
                    onForget = { model.forgetHeldNow(context) },
                    remindAt = remindAt,
                    onRemind = { at ->
                        model.remindAt(context, at)
                        /* Only when they are turning it ON, and
                           only on the versions that ask. A
                           permission prompt for a setting somebody
                           just switched off is a prompt with no
                           question in it. */
                        if (at != null &&
                            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU
                        ) {
                            askToNotify.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                )
            }
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
    item.key == "account" || item.key == SKILLS_KEY ||
        item.key == PORTFOLIO_KEY || item.key == STOCK_KEY ||
        item.key == TOOLS_KEY || item.key == LIVE_KEY ||
        item.key == ROUTINE_KEY || item.key == DIET_KEY ||
        site?.ladders?.any { it.key == item.key } == true ||
        readingSection(site, item.key) != null

/** Where a site ADDRESS takes the reader, and the one place that
    decides it.

    Two kinds of address come through here and they must agree: a
    link that opens the app from outside, and a link INSIDE a
    lesson or a piece, which for eleven blocks was drawn in the
    accent, underlined, and did nothing at all when pressed. Both
    resolve through `destinationOf`, so a glossary term opens as
    the lesson it is, a piece linked from a piece opens in place,
    and anything this app cannot draw opens on the site rather
    than dying under a finger.

    Every branch is braced, because `where` is written through a
    callback and a bare `-> go(...)` after an arrow has its own
    parsing story one function up. */
internal suspend fun followTo(
    to: Destination,
    model: AppModel,
    context: android.content.Context,
    site: SiteManifest?,
    colours: ReiadColours,
    go: (Where) -> Unit,
) {
    when (to) {
        Destination.Home -> { go(Where.Home) }
        Destination.Account -> { go(Where.Account) }

        is Destination.School -> {
            site?.ladders?.firstOrNull { it.key == to.key }?.let { school ->
                model.openLadder(school)
                go(Where.Ladder(school))
            }
        }

        is Destination.Lesson -> {
            /* The stage and the lesson are carried as slugs
               and nothing else, which is all the endpoint
               needs: the route reads the row. The titles
               arrive with the body, so the screen fills in
               rather than waiting for a ladder fetch first. */
            site?.ladders?.firstOrNull { it.key == to.school }?.let { school ->
                val stage = Stage(slug = to.stage)
                val lesson = Lesson(slug = to.slug)
                model.openLesson(school, stage, lesson)
                go(Where.Reading(school, stage, lesson))
            }
        }

        is Destination.Hub -> {
            go(Where.Hub(to.section, sectionTitle(site, to.section)))
        }

        is Destination.Piece -> {
            /* The hub first and the piece after, so a reader
               whose piece has been unpublished lands on the
               section rather than on nothing, and so back
               from the piece goes somewhere. */
            go(Where.Hub(to.section, sectionTitle(site, to.section)))
            model.pieceBySlug(to.section, to.slug)?.let { piece ->
                model.openPiece(piece)
                go(Where.Reading2(to.section, piece))
            }
        }

        is Destination.Tool -> {
            when (to.key) {
                STOCK_KEY -> { model.openTools(); go(Where.Stock) }
                TOOLS_KEY -> { model.openTools(); go(Where.Calculators) }
                LIVE_KEY -> { model.openLive(context); go(Where.Live) }
                ROUTINE_KEY -> { model.openRoutine(context); go(Where.Routine) }
                DIET_KEY -> { model.openDiet(context); go(Where.Diet) }
                SKILLS_KEY -> { go(Where.Skills) }
                PORTFOLIO_KEY -> { go(Where.Portfolio) }
                else -> Unit
            }
        }

        /* Not the front page. A link to something this app
           does not draw opens where it does exist. */
        is Destination.Elsewhere -> {
            openOnSite(context, to.url, colours)
        }
    }
}

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
        item.key == DIET_KEY -> { model.openDiet(context); Where.Diet }
        item.key == SKILLS_KEY -> Where.Skills
        item.key == PORTFOLIO_KEY -> Where.Portfolio
        school != null -> { model.openLadder(school); Where.Ladder(school) }
        section != null -> Where.Hub(section, sectionTitle(site, section))
        else -> now
    }
}

/** Which schools have a tick on this phone.

    The SCHOOL id, which is not the storage key: the money
    school's ticks are still filed under `learn-read` and its id
    has been `money` since it moved. `profiles.following` is
    constrained to the ids, so sending a key would be a 400 on
    the whole patch rather than one ignored field. */
internal fun startedIn(ticks: Map<String, Set<String>>): Set<String> =
    School.entries
        .filter { !ticks[ProgressKeys.read(it)].isNullOrEmpty() }
        .map { it.id }
        .toSet()

/** A school's colour, which the rail taught the reader.

    `accentOfSchool` in `ui/Paths.kt` is the same function where
    the drawing is; this is the one name the rest of this file
    already uses. One implementation, two names, and the alias is
    what stops a third appearing. */
internal fun accentOf(school: LadderSchool): Accent = accentOfSchool(school)

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

/** The admin's own course shelf, as the site routes it.

    Verified against the live site rather than guessed. It was
    `/courses`, which is a 404 there, and that is the whole of
    why the card opened nothing: a Custom Tab dutifully showing a
    not-found page is, to the reader who pressed the button,
    a button that does not work.

    A path this app writes by hand is a path somebody has to
    check, which is the argument for reading them out of the
    manifest wherever there is one. There is none for this shelf:
    it is admin-only and the menu the manifest carries is the
    menu everybody gets. */
const val COURSES_HREF = "/skills/courses"

/** Two, which is what a phone's home screen has and what makes
    a square a square. Not a setting: a three-column board of
    Bangla widgets is three columns of truncation. */
private const val BOARD_COLUMNS = 2

/** How the widgets that are NOT in the hand make way.

    The same spring the carried card settles on, so the board has
    one weight rather than a curve for the neighbours and a
    spring for the card: two timings on one gesture is what makes
    a rearrange read as two things happening at once. */
private val BOARD_FLOW = androidx.compose.animation.core.spring<androidx.compose.ui.unit.IntOffset>(
    dampingRatio = 0.82f,
    stiffness = 380f,
    visibilityThreshold = androidx.compose.ui.unit.IntOffset(1, 1),
)

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
    /** The board this reader arranged, as stored. Null means
        they never have, which is not the same as an empty board:
        the site's own default answers the first and nothing
        answers the second. */
    board: List<String>? = null,
    daysActive: Set<String> = emptySet(),
    routineGlance: uk.co.reiad.library.data.RoutineGlance? = null,
    dietGlance: uk.co.reiad.library.data.DietGlance? = null,
    bookmarks: Map<String, Bookmark> = emptyMap(),
    pieces: List<Piece> = emptyList(),
    news: List<Story> = emptyList(),
    onNeedNews: () -> Unit = {},
    lang: String = "bn",
    signedIn: Boolean = false,
    onBoard: (List<String>) -> Unit = {},
    onResetBoard: () -> Unit = {},
    onPiece: (Piece) -> Unit = {},
    onResume: (String, Bookmark) -> Unit = { _, _ -> },
    onStory: (Story) -> Unit = {},
    kept: List<Kept> = emptyList(),
    targets: List<Target> = emptyList(),
    onKept: (Kept) -> Unit = {},
    /** The voice, if it is going. The card that shows it is not
        part of the arrangement and never enters `board`: it is
        drawn above it for as long as something is being read. */
    speaking: uk.co.reiad.library.read.Speaking = uk.co.reiad.library.read.Speaking(),
    readingTitle: String = "",
    onReadingOpen: () -> Unit = {},
    /* One sway for the whole app, not one per screen or per
       card: every surface leans by the same amount because they
       are all on the same handset. `App` passes the instance the
       shell's ambient field reads, so the light behind the page
       and the light on the cards lean together. */
    sway: Sway = rememberSway(),
) {
    val c = LocalReiad.current
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

    /* ---------- the board ----------

       The reader's own arrangement, filtered against what THIS
       build can draw. A kind the site has shipped and this app
       has no renderer for is skipped rather than left as a blank
       rectangle with a title on it: see `ui/Widgets.kt`.

       The site's own default is the fallback where the reader
       has arranged nothing, and `BOARD_FLOOR` is the fallback
       for THAT, on a phone that has never fetched anything. */
    var arranging by rememberSaveable { mutableStateOf(false) }
    /* Decoration answers to reduced motion before anything else
       does: the jiggle while arranging is the definition of
       decoration. */
    val jiggle = !rememberReducedMotion()
    val catalogue = remember(site) {
        /* The manifest carries no catalogue yet, and an empty
           catalogue emptied the PICKER: a reader who removed a
           widget could never get it back. The floor answers
           until the site's own table arrives, and the site's
           wins the moment it does. */
        (site?.widgets?.kinds?.takeIf { it.isNotEmpty() } ?: catalogueFloor())
            .associateBy { it.id }
    }
    val placed = remember(board, site) {
        layoutOf(
            stored = board,
            drawable = DRAWABLE,
            fallback = site?.widgets?.home?.takeIf { it.isNotEmpty() } ?: BOARD_FLOOR,
        )
    }
    val data = BoardData(
        site = site, ticks = ticks, bookmarks = bookmarks, pieces = pieces,
        sway = sway, icons = icons, lang = lang, news = news,
        daysActive = daysActive, routine = routineGlance, diet = dietGlance,
        today = remember { java.time.LocalDate.now().toString() },
        kept = kept, targets = targets,
    )
    val act = BoardActions(
        onSchool = onOpen, onItem = onGo, onPiece = onPiece, onResume = onResume,
        onStory = onStory, onKept = onKept,
    )

    /* Three RSS feeds read on a Worker is not a request to make
       for a reader who has taken the widget off their board. */
    LaunchedEffect(placed) {
        if (placed.any { it.id == "market" }) onNeedNews()
    }

    val listState = rememberLazyGridState()
    /* Hold a widget and move it: the board reorders under the
       finger as it passes each neighbour, and every question
       about where the finger is goes to what the list actually
       laid out. See `ui/BoardDrag.kt`.

       THE GESTURE OWNS A WORKING COPY. Writing the store on
       every pass meant the next pass was computed against the
       board as it stood before the last one, and the card fought
       its way back to where it started: that was the report
       "unable to move positions, if i change one that jumps
       right back". The copy is opened on pick, mutated in the
       same frame the finger crosses a neighbour, and committed
       ONCE on drop. `working` is read at CALL time inside these
       lambdas, not at composition time, which is what makes two
       moves in one frame land in order. */
    var working by remember { mutableStateOf<List<Placed>?>(null) }
    val showing = working ?: placed
    val drag = rememberBoardDrag(
        state = listState,
        /* Only the widgets. The greeting, the arrange button and
           the picker are items in the same grid, and a card
           dragged over the greeting must find nothing to swap
           with rather than swapping with the page's own head. */
        keys = showing.map { it.id }.toSet(),
        indexOf = { key ->
            (working ?: placed).indexOfFirst { it.id == key }.takeIf { it >= 0 }
        },
        onPick = { working = placed },
        onMove = { from, to -> working = moved(working ?: placed, from, to) },
        onDrop = {
            /* Only a changed board is worth a write: a long press
               that went nowhere is not an arrangement. */
            working?.takeIf { it != placed }?.let { onBoard(storedOf(it)) }
            working = null
        },
    )

    /* CAPPED AND CENTRED, not reflowed, at tablet width. A wide
       widget across 840dp is a reading whose number sits a
       hand-span from its label, and the site answers the same
       way: the page column has a maximum and the board lives
       inside it. Reflowing wides into columns would resize what
       the reader sized. */
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    /* ---------- TWO COLUMNS, and the same two in both modes ----------

       A phone's home screen is legible at a glance because every
       tile is one square or a whole number of them, and because
       the grid does not change when you hold a finger down: the
       widgets start jiggling exactly where they already were.

       This board used to be a column of full-width cards that
       PAIRED its squares only while reading, and unpaired them
       into a single file the moment somebody pressed সাজান. So
       arranging began with every widget on the board changing
       size and jumping to a new row, which is the "jumpy"
       report, and the drag that followed was a drag through a
       layout the reader had never seen. One grid, both modes,
       and nothing moves when the mode does.

       A square that has nothing to pair with keeps its own half
       and leaves the other empty, which is what a home screen
       does too: the ORDER is the reader's, and pulling a widget
       up from further down to fill the hole would rearrange
       their board for them. */
    LazyVerticalGrid(
        columns = GridCells.Fixed(BOARD_COLUMNS),
        modifier = Modifier
            .fillMaxHeight()
            .widthIn(max = 660.dp)
            .padding(horizontal = Gap.s8),
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(Gap.s7),
        verticalArrangement = Arrangement.spacedBy(Gap.s7),
        /* The bar FLOATS over the page rather than pushing it, so
           the page has to end above it or the last card sits under
           the bar and looks like the list has been cut off. */
        contentPadding = PaddingValues(top = topClearance(), bottom = barClearance()),
    ) {
        item("door", span = { GridItemSpan(maxLineSpan) }) {
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

        /* ---------- what is being read, while it is ----------

           It arrives when the voice starts and goes when it
           stops, above the board and not IN it: see
           `ui/Speaking.kt` for why a card that comes and goes on
           its own cannot be part of an arrangement somebody
           made. A reader who walked away from a lesson can hold
           it from the front page. */
        item("reading", span = { GridItemSpan(maxLineSpan) }) {
            androidx.compose.animation.AnimatedVisibility(
                visible = speaking.on,
                enter = androidx.compose.animation.fadeIn(
                    androidx.compose.animation.core.tween(uk.co.reiad.library.core.Motion.ENTER_MS),
                ) + androidx.compose.animation.expandVertically(
                    androidx.compose.animation.core.tween(uk.co.reiad.library.core.Motion.ENTER_MS),
                ),
                exit = androidx.compose.animation.fadeOut(
                    androidx.compose.animation.core.tween(uk.co.reiad.library.core.Motion.QUICK_MS),
                ) + androidx.compose.animation.shrinkVertically(
                    androidx.compose.animation.core.tween(uk.co.reiad.library.core.Motion.QUICK_MS),
                ),
            ) {
                Column {
                    ReadingWidget(
                        speaking = speaking,
                        title = readingTitle,
                        onOpen = onReadingOpen,
                    )
                    Spacer(Modifier.height(Gap.s7))
                }
            }
        }

        if (site == null) {
            /* Never an empty screen, and never a bare sentence in
               the middle of one. Either the app is reading the
               site or it could not: both are said, and the second
               one has a button. */
            item("waiting", span = { GridItemSpan(maxLineSpan) }) {
                if (note != null) {
                    Problem("The site did not answer", note, onRetry = onRetry)
                } else {
                    Skeleton()
                }
            }
        }

        /* ---------- the board ---------- */

        item("arrange", span = { GridItemSpan(maxLineSpan) }) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                PillButton(
                    label = if (arranging) {
                        if (lang == "bn") "হয়ে গেছে" else "Done"
                    } else {
                        if (lang == "bn") "সাজান" else "Arrange"
                    },
                    onClick = { arranging = !arranging },
                    icon = if (arranging) "check" else "sliders",
                    pressed = arranging,
                )
            }
            Spacer(Modifier.height(Gap.s6))
        }

        /* ---------- the widgets ----------

           ONE list, both modes. Arranging adds the jiggle, the
           two badges and the drag; it does not move anything,
           because a board that rearranges itself the moment you
           ask to rearrange it has already lost the reader's
           place. */
        itemsIndexed(
            showing,
            key = { _, p -> p.id },
            span = { _, p -> GridItemSpan(spanOf(p.size)) },
        ) { at, p ->
            val kind = kindOf(p.id, catalogue, p.size)
            val held = drag.holding(p.id)
            val knock = LocalHapticFeedback.current
            WidgetFrame(
                modifier = Modifier
                    /* A widget finding its new place GLIDES
                       there: on a drop, on an arrow press, on an
                       add or a remove, the others make way
                       rather than teleporting. The one in the
                       hand is excused, because it is already
                       answering the finger and a second
                       animation would fight it.

                       A SPRING rather than a curve, and the
                       same one the carried card settles on, so
                       everything that moves on this board moves
                       with one weight. */
                    .then(
                        if (held) Modifier
                        else Modifier.animateItem(placementSpec = BOARD_FLOW)
                    )
                    .zIndex(if (held) 1f else 0f)
                    /* HOLD TO ARRANGE, from the board itself.
                       The সাজান button stays for anyone who
                       would never guess a long press, but the
                       gesture a phone teaches on its own home
                       screen works here too. Behind the widget's
                       own taps, so opening a card is untouched,
                       and off entirely once the board IS loose,
                       where the drag owns the same finger. */
                    .then(
                        if (arranging) Modifier
                        else Modifier.pointerInput(Unit) {
                            detectTapGestures(onLongPress = {
                                knock.performHapticFeedback(HapticFeedbackType.LongPress)
                                arranging = true
                            })
                        }
                    ),
                kind = kind,
                placed = p,
                arranging = arranging,
                moving = jiggle,
                lifted = held,
                shift = { drag.shift(p.id) },
                first = at == 0,
                last = at == showing.lastIndex,
                lang = lang,
                onUp = { onBoard(storedOf(moved(showing, at, at - 1))) },
                onDown = { onBoard(storedOf(moved(showing, at, at + 1))) },
                onResize = {
                    val other = kind.other(p.size) ?: return@WidgetFrame
                    onBoard(storedOf(showing.toMutableList().also { it[at] = p.copy(size = other) }))
                },
                onRemove = {
                    onBoard(storedOf(showing.filterIndexed { i, _ -> i != at }))
                },
                handle = Modifier.dragHandle(drag, p.id),
            ) {
                /* The UNIT, which is what makes these read as
                   widgets rather than as cards that happen to be
                   near each other: a square is a square, a wide
                   is the row at that height, a large is the row
                   two squares deep. A floor rather than a fixed
                   height, because a Bangla line that runs long
                   must lengthen its widget rather than be cut in
                   half. */
                BoxWithConstraints {
                    /* The square is worked out from the CELL, so
                       one number describes the board at every
                       width: on a narrow phone, on a wide one,
                       and inside the 660dp cap on a tablet. A
                       widget one column wide IS the square; one
                       that spans the row is two of them with the
                       gutter between. */
                    val square = if (spanOf(p.size) == 1) maxWidth
                        else (maxWidth - Gap.s7) / 2
                    val units = unitsOf(p.size)
                    /* The floor goes to the WIDGET, not around
                       it. Wrapping it in a taller box left the
                       card its own content height with dead
                       board showing underneath, which is a
                       reserved space that looks like a mistake
                       rather than a tile. Every kind takes a
                       modifier for exactly this. */
                    Widget(
                        p.id, p.size, data, act,
                        modifier = Modifier.heightIn(
                            min = square * units + Gap.s7 * (units - 1),
                        ),
                    )
                }
            }
        }

        if (arranging) {
            item("picker", span = { GridItemSpan(maxLineSpan) }) {
                Spacer(Modifier.height(Gap.s6))
                WidgetPicker(
                    /* The catalogue minus what is already on the
                       board, and minus what this build cannot
                       draw: offering a widget that would not
                       appear is worse than not offering it. */
                    offered = catalogue.values
                        .filter { it.id in DRAWABLE && placed.none { p -> p.id == it.id } },
                    lang = lang,
                    signedIn = signedIn,
                    onAdd = { kind ->
                        onBoard(storedOf(placed + Placed(kind.id, kind.added())))
                    },
                    onReset = onResetBoard,
                )
                Spacer(Modifier.height(Gap.s8))
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
            /* The nav table's own word, which is Bangla, and
               the Latin label only when a school has none.

               It was `school.en`, so the cards on the front page
               read MONEY, GERMAN and QUR'ANIC ARABIC to a reader
               the whole site is written in Bangla for. The site's
               own card says কোর্স, out of `kind` in `shared/nav.ts`,
               and that field simply was not being carried: the
               table mapped its fields by hand. */
            chip = school.kind.ifBlank { school.en },
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
    /** Whether this school is on the shelf, and how much of it
        actually reached the phone. Both are the caller's, because
        the second one is counted from the cache and this is a
        drawing. */
    following: Boolean = false,
    held: Held = Held(0, 0),
    onKeep: () -> Unit = {},
) {
    val c = LocalReiad.current
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = Gap.s8),
        contentPadding = PaddingValues(top = topClearance(), bottom = barClearance()),
    ) {
        item {
            Crumb("Home", onBack)
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

            /* Will this work on a plane. The one question a
               reader has about offline, answered with a count
               rather than a promise. */
            if (lessons > 0) {
                Spacer(Modifier.height(Gap.s8))
                KeepSchool(
                    following = following,
                    held = held,
                    lessons = lessons,
                    waiting = following && held.lessons == 0,
                    onToggle = onKeep,
                )
            }

            Spacer(Modifier.height(Gap.s9))
        }

        if (stages.isEmpty()) {
            item { Skeleton(lines = 4, label = "Reading the ladder") }
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
            Rung(onClick = { onOpenBook(stage) }) {
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

        /* The rungs get the same breath the drawer's rows do:
           flush, fourteen lessons read as one striped block. */
        Column(verticalArrangement = Arrangement.spacedBy(Gap.s2)) {
        for (lesson in lessons) {
            val id = lessonId(stage.slug, lesson.slug)
            Rung(onClick = { onOpen(stage, lesson) }, enabled = lesson.isWritten) {
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
            .padding(top = topClearance(), bottom = barClearance())
    ) {
        Crumb(stage.bn, onBack)
        Spacer(Modifier.height(Gap.s7))

        /* The lesson's own head, and it is FOUR things.

           It was a plain `PageHead`, so the icon, the name in
           the language the school teaches and the accent rail
           under the definition were all missing, and the
           one-liner read as another paragraph of grey text. The
           site sets all four and every word was already in the
           row: nothing here needed fetching, only drawing. */
        LessonHead(
            title = lesson.bn,
            /* Whichever second language this school teaches
               under. Asked of the LESSON rather than decided
               from the school's key, because the row is what
               carries it and a fifth school would arrive
               drawn. */
            also = lesson.en ?: lesson.de ?: lesson.ar,
            icon = lesson.icon,
            eyebrow = stage.en ?: stage.bn,
            oneLiner = lesson.blurb,
            /* Bangla digits inside Bangla words, which is what
               `bnNum` is for and what the site does through
               `look.words.minutes`. The risk badge rides here
               too: the site puts it in the lesson's meta and the
               card draws it from the same place. */
            meta = listOfNotNull(
                lesson.minutes.takeIf { it > 0 }?.let { "${inScript(it.toString(), "bn")} মিনিট পড়া" },
                lesson.risk,
            ).joinToString(" · ").ifBlank { null },
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
                /* Parsed off the main thread, for the reason
                   `PieceScreen` says at length: `remember { }`
                   runs inside composition, and a long lesson is a
                   hitch on exactly the frame a reader is
                   watching. */
                val blocks by produceState(emptyList<Block>(), page.body) {
                    value = withContext(Dispatchers.Default) {
                        BodyParser.parse(page.body).blocks
                    }
                }
                if (blocks.isEmpty()) {
                    Skeleton(lines = 6, label = "Opening the lesson")
                    return@Column
                }
                /* Every checklist in a school lesson IS a set of
                   checkpoints. The numbering is computed once,
                   across the whole lesson, because that is how
                   the site files them and the ids are in real
                   accounts. */
                val bases = remember(blocks) { checkpointBases(blocks) }
                /* The parsed body settles up over the skeleton
                   rather than popping over it. */
                Column(Modifier.arriving(lessonKey)) {
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
        }

        Spacer(Modifier.height(Gap.s10))

        /* The money school's tick is a button. The other three
           marked this lesson when it opened, so what they get is
           a statement rather than a control. */
        if (isMoney) {
            PillButton(
                if (ticked) "পড়া হয়েছে ✓" else "পড়া হয়েছে",
                onTick,
                kind = ButtonKind.SOFT,
                wide = true,
                pressed = ticked,
            )
        } else if (ticked) {
            Plate(Modifier.fillMaxWidth()) {
                Text("পড়া হয়েছে ✓", style = MaterialTheme.typography.labelLarge, color = c.accent)
            }
        }
        Spacer(Modifier.height(Gap.s10))
    }
}
