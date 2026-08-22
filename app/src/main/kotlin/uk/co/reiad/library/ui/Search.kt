package uk.co.reiad.library.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import uk.co.reiad.library.core.Found
import uk.co.reiad.library.core.Kind
import uk.co.reiad.library.core.Piece
import uk.co.reiad.library.core.SiteManifest
import uk.co.reiad.library.core.grouped
import uk.co.reiad.library.core.search

/* ============================================================
   The palette.

   `core/Search.kt` does the finding, against the manifest the app
   already has, which is what makes this work on a bus. The screen
   only has to draw it and keep out of the way.

   ---- what it shows before anything is typed ----

   Not everything. A palette listing three hundred rows before a
   key is pressed is a wall, and the site's own palette does not
   do it either. What it shows instead is where a reader most
   likely wants to go: the schools, which are the four things this
   app is for.
   ============================================================ */

@Composable
fun SearchScreen(
    site: SiteManifest?,
    pieces: List<Piece>,
    onOpen: (Found) -> Unit,
    onClose: () -> Unit,
) {
    val c = LocalReiad.current
    val reduced = rememberReducedMotion()
    val retreat = rememberRetreat(onBack = onClose)
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val results = remember(site, pieces, query) { search(site, query, pieces = pieces) }
    val groups = remember(results) { grouped(results) }

    /* Straight into the box. Somebody who opened search wants to
       type, and a keyboard they have to summon is a tap nobody
       asked for. */
    LaunchedEffect(Unit) { focus.requestFocus() }

    Column(
        Modifier
            .fillMaxSize()
            .retreating(retreat, reduced)
            .background(c.paper)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = Gap.s8),
    ) {
        Spacer(Modifier.height(Gap.s7))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .weight(1f)
                    .height(Gap.tap)
                    .clip(RoundedCornerShape(Corner.pill))
                    /* A text field answers differently from
                       everything else and the site says so in
                       `NOT_GLASS`: its affordance is the caret
                       and the focus ring, and a lit resting rim
                       on a box you type into is a box that looks
                       like a button. So it is a GROOVE: a channel
                       waiting to be filled, which is exactly what
                       it is. */
                    .material(Kind.GROOVE, c, Corner.pill, ground = c.paperSunk)
                    .padding(horizontal = Gap.s7),
                contentAlignment = Alignment.CenterStart,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon("search", size = 18.dp, tint = c.inkSoft)
                    Spacer(Modifier.width(Gap.s5))
                    Box(Modifier.weight(1f)) {
                        if (query.isEmpty()) {
                            Text(
                                "Search the library",
                                style = MaterialTheme.typography.bodyLarge,
                                color = c.inkSoft,
                            )
                        }
                        BasicTextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = c.ink),
                            cursorBrush = SolidColor(c.accent),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = { results.firstOrNull()?.let(onOpen) },
                            ),
                            modifier = Modifier.fillMaxWidth().focusRequester(focus),
                        )
                    }
                }
            }
            Spacer(Modifier.width(Gap.s5))
            Box(
                Modifier
                    .clip(RoundedCornerShape(Corner.pill))
                    .clickable(role = Role.Button, onClick = onClose)
                    .padding(Gap.s6),
            ) {
                Icon("close", size = 20.dp, tint = c.inkSoft)
            }
        }

        Spacer(Modifier.height(Gap.s7))

        when {
            query.isBlank() -> Suggestions(site, onOpen)

            results.isEmpty() -> Column {
                Text(
                    "Nothing here matches that.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = c.ink,
                )
                Spacer(Modifier.height(Gap.s4))
                Text(
                    if (site == null) {
                        "The site has not been read yet, so there is nothing to search. " +
                            "Open the app once with a connection and this works offline after that."
                    } else {
                        "This searches page titles and their descriptions, not the words " +
                            "inside a lesson."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = c.inkSoft,
                )
            }

            else -> LazyColumn(contentPadding = PaddingValues(bottom = Gap.s11)) {
                for ((hint, rows) in groups) {
                    item(key = "head:$hint") {
                        Text(
                            hint.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = c.inkSoft,
                            modifier = Modifier.padding(top = Gap.s6, bottom = Gap.s3),
                        )
                    }
                    items(rows, key = { it.url }) { row ->
                        Result(row, site?.accents.orEmpty(), onOpen)
                    }
                }
            }
        }
    }
}

/** Before a key is pressed: the four schools, which are the four
    things this app is for. */
@Composable
private fun Suggestions(site: SiteManifest?, onOpen: (Found) -> Unit) {
    val c = LocalReiad.current
    val schools = site?.ladders.orEmpty()
    if (site == null || schools.isEmpty()) return
    Column {
        Text(
            "SCHOOLS",
            style = MaterialTheme.typography.labelSmall,
            color = c.inkSoft,
            modifier = Modifier.padding(bottom = Gap.s3),
        )
        for (school in schools) {
            Result(
                Found(school.bn, school.href, "School", school.key, school.blurb, rank = 0),
                site.accents,
                onOpen,
            )
        }
    }
}

/** A row wears its destination's colour.

    The manifest sends `accents` as a map from a nav key to the
    stylesheet's own token name, `money` to `var(--green)`, which
    is the site's one table for this. Building the token from the
    key would be inventing a second spelling: there is no
    `--money`. */
@Composable
private fun Result(row: Found, accents: Map<String, String>, onOpen: (Found) -> Unit) {
    val c = LocalReiad.current
    val accent = accentColour(row.group?.let { accents[it] }, c)
    Rung(Modifier.clickable(role = Role.Button) { onOpen(row) }) {
        Column(Modifier.weight(1f)) {
            Text(
                row.title,
                style = if (isBangla(row.title)) BanglaBody else MaterialTheme.typography.bodyLarge,
                color = c.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            row.blurb?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = c.inkSoft,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(Gap.s5))
        Icon("chevron", size = 16.dp, tint = accent)
    }
}
