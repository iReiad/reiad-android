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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import uk.co.reiad.library.core.Accent
import uk.co.reiad.library.core.Accents
import uk.co.reiad.library.core.BodyParser
import uk.co.reiad.library.core.LadderSchool
import uk.co.reiad.library.core.Lesson
import uk.co.reiad.library.core.LessonPage
import uk.co.reiad.library.core.NavGroup
import uk.co.reiad.library.core.School
import uk.co.reiad.library.core.SiteManifest
import uk.co.reiad.library.core.Prefs
import uk.co.reiad.library.core.Stage
import uk.co.reiad.library.core.Theme
import uk.co.reiad.library.core.lessonId
import uk.co.reiad.library.data.Reiad
import uk.co.reiad.library.ui.AccentRail
import uk.co.reiad.library.ui.BodyView
import uk.co.reiad.library.ui.Faces
import uk.co.reiad.library.ui.Chip
import uk.co.reiad.library.ui.Control
import uk.co.reiad.library.ui.Corner
import uk.co.reiad.library.ui.Gap
import uk.co.reiad.library.ui.GoCard
import uk.co.reiad.library.ui.Groove
import uk.co.reiad.library.ui.GroupScreen
import uk.co.reiad.library.ui.accentOf as tokenAccent
import uk.co.reiad.library.ui.InfoCard
import uk.co.reiad.library.ui.LocalReiad
import uk.co.reiad.library.ui.Pane
import uk.co.reiad.library.ui.Plate
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

    val accent: Accent = when (val here = where) {
        is Where.Ladder -> accentOf(here.school)
        is Where.Reading -> accentOf(here.school)
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
        is Where.Ladder -> here.school.key
        is Where.Reading -> here.school.key
        is Where.Group -> here.group.items.firstOrNull()?.key
        Where.Home -> null
    }

    ReiadTheme(accent = accent, dark = dark) {
        Surface(Modifier.fillMaxSize(), color = LocalReiad.current.paper) {
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
                is Where.Group -> {
                    BackHandler { where = Where.Home }
                    GroupScreen(
                        group = here.group,
                        accents = site?.accents.orEmpty(),
                        bottomPadding = BAR_CLEARANCE,
                        canOpenHere = { item ->
                            site?.ladders?.any { it.key == item.key } == true
                        },
                        onOpenHere = { item ->
                            val school = site?.ladders?.firstOrNull { it.key == item.key }
                            if (school != null) {
                                model.openLadder(school)
                                where = Where.Ladder(school)
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
                        onBack = { where = Where.Home },
                        onOpen = { stage, lesson ->
                            model.openLesson(here.school, stage, lesson)
                            where = Where.Reading(here.school, stage, lesson)
                        },
                    )
                }

                is Where.Reading -> {
                    BackHandler { where = Where.Ladder(here.school) }
                    val id = lessonId(here.stage.slug, here.lesson.slug)
                    Reading(
                        school = here.school,
                        stage = here.stage,
                        lesson = here.lesson,
                        page = page,
                        ticked = id in ticks[here.school.key].orEmpty(),
                        isMoney = here.school.key == School.MONEY.id,
                        onBack = { where = Where.Ladder(here.school) },
                        onTick = { model.tick(here.school, here.stage, here.lesson) },
                    )
                }
            }
            }

            if (searching) {
                SearchScreen(
                    site = site,
                    onOpen = { found ->
                        searching = false
                        val school = site?.ladders?.firstOrNull { it.key == found.group }
                        if (school != null) {
                            model.openLadder(school)
                            where = Where.Ladder(school)
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
            Text(school.bn, style = MaterialTheme.typography.displaySmall, color = c.ink)
            Text(school.en, style = MaterialTheme.typography.bodyMedium, color = c.inkSoft)
            if (stale) {
                Spacer(Modifier.height(Gap.s5))
                Chip("SAVED COPY")
            }
            Spacer(Modifier.height(Gap.s9))
        }

        if (stages.isEmpty()) {
            item { Text("Reading the ladder…", color = c.inkSoft) }
        }

        items(stages) { stage ->
            StageCard(stage, ticks, onOpen)
            Spacer(Modifier.height(Gap.s8))
        }
    }
}

@Composable
private fun StageCard(stage: Stage, ticks: Set<String>, onOpen: (Stage, Lesson) -> Unit) {
    val c = LocalReiad.current
    val lessons = stage.lessons
    val done = lessons.count { lessonId(stage.slug, it.slug) in ticks }

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
                color = c.inkSoft,
            )
        }

        Spacer(Modifier.height(Gap.s6))
        Groove(if (lessons.isEmpty()) 0f else done.toFloat() / lessons.size)
        Spacer(Modifier.height(Gap.s5))

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
                BodyView(blocks)
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
