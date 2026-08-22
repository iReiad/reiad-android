package uk.co.reiad.library

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import uk.co.reiad.library.core.Accents
import uk.co.reiad.library.core.Block
import uk.co.reiad.library.core.BodyParser
import uk.co.reiad.library.core.Lesson
import uk.co.reiad.library.core.LessonPage
import uk.co.reiad.library.core.School
import uk.co.reiad.library.core.Stage
import uk.co.reiad.library.core.lessonId
import uk.co.reiad.library.data.Reiad
import uk.co.reiad.library.ui.AccentRail
import uk.co.reiad.library.ui.BodyView
import uk.co.reiad.library.ui.Card
import uk.co.reiad.library.ui.Chip
import uk.co.reiad.library.ui.Corner
import uk.co.reiad.library.ui.Gap
import uk.co.reiad.library.ui.Groove
import uk.co.reiad.library.ui.LocalReiad
import uk.co.reiad.library.ui.ReiadTheme
import uk.co.reiad.library.ui.Rung

/* ============================================================
   The money school, on a handset: the ladder, a lesson, a tick.

   This is the first slice ANDROID.md names, and it is deliberately
   the whole vertical rather than a prettier ladder: a ladder that
   renders and a lesson that does not open is the shape of thing
   this project keeps promising not to ship.

   Opening is not finishing. The money school's tick is a BUTTON,
   and a visit only moves the bookmark. The other three schools
   mark a lesson on opening, which is their own semantics and
   arrives with them.
   ============================================================ */

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ReiadTheme(accent = Accents.GREEN) {
                Surface(
                    Modifier.fillMaxSize(),
                    color = LocalReiad.current.paper,
                ) { MoneySchool() }
            }
        }
    }
}

/* ---------- state ---------- */

sealed interface Screen {
    data object Ladder : Screen
    data class Reading(val stage: Stage, val lesson: Lesson) : Screen
}

class SchoolModel(private val reiad: Reiad) : ViewModel() {

    private val _stages = MutableStateFlow<List<Stage>>(emptyList())
    val stages: StateFlow<List<Stage>> = _stages.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _lesson = MutableStateFlow<LessonPage?>(null)
    val lesson: StateFlow<LessonPage?> = _lesson.asStateFlow()

    private val _ticks = MutableStateFlow<Set<String>>(emptySet())
    val ticks: StateFlow<Set<String>> = _ticks.asStateFlow()

    init {
        viewModelScope.launch {
            _ticks.value = reiad.ticksNow(School.MONEY)
            runCatching { reiad.ladder("money") }
                .onSuccess { _stages.value = it.stages }
                .onFailure { _error.value = it.message ?: "Could not reach the site" }
        }
    }

    fun open(stage: Stage, lesson: Lesson) {
        _lesson.value = null
        viewModelScope.launch {
            runCatching { reiad.lesson("money", stage.slug, lesson.slug) }
                .onSuccess { _lesson.value = it.lesson }
                .onFailure { _error.value = it.message ?: "Could not reach that lesson" }
        }
    }

    fun tick(stage: Stage, lesson: Lesson) {
        viewModelScope.launch {
            _ticks.value = reiad.toggleTick(School.MONEY, lessonId(stage.slug, lesson.slug))
        }
    }
}

/* ---------- screens ---------- */

@Composable
fun MoneySchool() {
    val context = LocalContext.current
    val reiad = remember { Reiad(context.applicationContext) }
    val model = remember { SchoolModel(reiad) }

    var screen by remember { mutableStateOf<Screen>(Screen.Ladder) }
    val stages by model.stages.collectAsState()
    val ticks by model.ticks.collectAsState()
    val error by model.error.collectAsState()
    val lesson by model.lesson.collectAsState()

    when (val current = screen) {
        Screen.Ladder -> Ladder(
            stages = stages,
            ticks = ticks,
            error = error,
            onOpen = { stage, item ->
                model.open(stage, item)
                screen = Screen.Reading(stage, item)
            },
        )

        is Screen.Reading -> Reading(
            stage = current.stage,
            lesson = current.lesson,
            page = lesson,
            ticked = lessonId(current.stage.slug, current.lesson.slug) in ticks,
            onBack = { screen = Screen.Ladder },
            onTick = { model.tick(current.stage, current.lesson) },
        )
    }
}

@Composable
private fun Ladder(
    stages: List<Stage>,
    ticks: Set<String>,
    error: String?,
    onOpen: (Stage, Lesson) -> Unit,
) {
    val c = LocalReiad.current
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = Gap.s8),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = Gap.s10),
    ) {
        item {
            Text("টাকা ও শেয়ার", style = MaterialTheme.typography.displaySmall, color = c.ink)
            Text(
                "Money and shares, from nothing",
                style = MaterialTheme.typography.bodyMedium,
                color = c.inkSoft,
            )
            Spacer(Modifier.height(Gap.s9))
        }

        if (error != null && stages.isEmpty()) {
            item {
                Card {
                    Text("Offline", style = MaterialTheme.typography.titleMedium, color = c.ink)
                    Text(error, style = MaterialTheme.typography.bodyMedium, color = c.inkSoft)
                }
            }
        }

        if (stages.isEmpty() && error == null) {
            item { Text("Reading the ladder…", color = c.inkSoft) }
        }

        items(stages) { stage ->
            val lessons = stage.lessons
            val done = lessons.count { lessonId(stage.slug, it.slug) in ticks }
            StageCard(stage, lessons, done, ticks, onOpen)
            Spacer(Modifier.height(Gap.s8))
        }
    }
}

@Composable
private fun StageCard(
    stage: Stage,
    lessons: List<Lesson>,
    done: Int,
    ticks: Set<String>,
    onOpen: (Stage, Lesson) -> Unit,
) {
    val c = LocalReiad.current
    Card(accented = done > 0) {
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
        Spacer(Modifier.height(Gap.s6))

        for (lesson in lessons) {
            val id = lessonId(stage.slug, lesson.slug)
            Rung(Modifier.clickable(enabled = lesson.isWritten) { onOpen(stage, lesson) }) {
                Box(
                    Modifier
                        .width(18.dp)
                        .height(18.dp)
                        .background(
                            if (id in ticks) c.accent else c.hairline,
                            androidx.compose.foundation.shape.RoundedCornerShape(Corner.pill),
                        )
                )
                Spacer(Modifier.width(Gap.s6))
                Column(Modifier.weight(1f)) {
                    Text(
                        lesson.bn,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (lesson.isWritten) c.ink else c.inkSoft,
                    )
                    lesson.en?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = c.inkSoft)
                    }
                }
                if (!lesson.isWritten) Chip("আসছে")
                else if (lesson.minutes > 0) {
                    Text(
                        "${lesson.minutes} min",
                        style = MaterialTheme.typography.labelSmall,
                        color = c.inkSoft,
                    )
                }
            }
        }
    }
}

@Composable
private fun Reading(
    stage: Stage,
    lesson: Lesson,
    page: LessonPage?,
    ticked: Boolean,
    onBack: () -> Unit,
    onTick: () -> Unit,
) {
    val c = LocalReiad.current
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Gap.s8, vertical = Gap.s10)
    ) {
        Text(
            "← ${stage.bn}",
            style = MaterialTheme.typography.labelLarge,
            color = c.accent,
            modifier = Modifier.clickable { onBack() },
        )
        Spacer(Modifier.height(Gap.s7))

        Text(lesson.bn, style = MaterialTheme.typography.displaySmall, color = c.ink)
        lesson.blurb?.let {
            Spacer(Modifier.height(Gap.s4))
            Text(it, style = MaterialTheme.typography.bodyMedium, color = c.inkSoft)
        }
        Spacer(Modifier.height(Gap.s9))

        if (page == null) {
            Text("Opening…", color = c.inkSoft)
        } else {
            val blocks = remember(page.body) { BodyParser.parse(page.body).blocks }
            if (blocks.isEmpty()) {
                Text(
                    "This one is promised and not written yet.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = c.inkSoft,
                )
            } else {
                BodyView(blocks)
            }
        }

        Spacer(Modifier.height(Gap.s10))

        /* The money school's tick is a button. Opening a lesson
           does not mark it, deliberately. */
        Button(
            onClick = onTick,
            modifier = Modifier.fillMaxWidth().height(Gap.tap),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(Corner.pill),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (ticked) c.accent else c.panel,
                contentColor = if (ticked) c.paper else c.accent,
            ),
        ) {
            Text(
                if (ticked) "পড়া হয়েছে ✓" else "পড়া হয়েছে",
                style = MaterialTheme.typography.labelLarge,
            )
        }
        Spacer(Modifier.height(Gap.s10))
    }
}
