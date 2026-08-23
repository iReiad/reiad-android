package uk.co.reiad.library.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import uk.co.reiad.library.core.PageEntry
import uk.co.reiad.library.core.PageHeadWords
import uk.co.reiad.library.core.NavGroup
import uk.co.reiad.library.core.NavItem

/* ============================================================
   The two hubs that are a LIST OF THINGS.

   `/skills` is what this site teaches, `/portfolio` is the work
   it shows. Both were a browser hand-off: the app had every row
   of both in the manifest already and drew neither, so pressing
   either sent a reader out to Chrome for a list the phone was
   holding.

   ---- the words come down too ----

   Every head here is `shared/heads.ts` through `/api/site`, with
   any count already resolved. Nothing on this screen is written
   in Kotlin, which is the point: a headline reworded on the site
   is reworded here on the next fetch.

   A head that has not arrived draws the title alone rather than
   an empty paragraph, because a deployment that predates the
   table is a real state and it should look like a plain page
   rather than a broken one.
   ============================================================ */

@Composable
private fun HubHead(head: PageHeadWords?, fallback: String) {
    if (head == null) {
        PageHead(title = fallback)
        return
    }
    PageHead(
        title = head.title.ifBlank { fallback },
        eyebrow = head.eyebrow.ifBlank { null },
        lede = head.lede.ifBlank { null },
    )
}

/**
 * What this site teaches, one card each.
 *
 * Drawn from the LEARN GROUP of the nav table, which is what the
 * site's own `/skills` reads and is not the same as `SKILLS` in
 * `content.ts`. The difference is the colours: a nav item
 * carries the school's own accent, and the site has a comment
 * about the day this page did not, when German, Qur'anic Arabic,
 * English, Cooking and Travel all lost the blue, teal, violet,
 * rose and plum the one table gives them, on the page whose
 * whole job is to show them side by side.
 *
 * `skills` itself is filtered out, because a card taking a
 * reader to the page they are already on is a card that does
 * nothing.
 *
 * `unlisted` is NOT filtered here and must not be: `/api/site`
 * already drops those before they leave, which is why the third
 * party course section never reaches this app at all. A filter
 * for a case that cannot arrive is a filter nobody can test, and
 * it would read as though the app were making that decision.
 */
@Composable
fun SkillsScreen(
    group: NavGroup?,
    head: PageHeadWords?,
    bottomPadding: Dp,
    onOpen: (NavItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rows = group?.items.orEmpty().filter { it.key != "skills" }

    LazyColumn(
        modifier.fillMaxSize().padding(horizontal = Gap.s8),
        contentPadding = PaddingValues(top = TOP_CLEARANCE, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(Gap.s6),
    ) {
        item("head") {
            HubHead(head, "দক্ষতা")
            Spacer(Modifier.height(Gap.s5))
        }
        items(rows, key = { it.key ?: it.href }) { item ->
            /* Each in its OWN colour, which the rail taught the
               reader and which this page exists to show. */
            ReiadTheme(
                accent = accentOf(item.accent),
                dark = LocalReiad.current.isDark,
            ) {
                if (item.soon) {
                    SoonCard(
                        title = item.sub?.ifBlank { null } ?: item.label,
                        dek = item.blurb?.ifBlank { null },
                    )
                } else {
                    GoCard(
                        title = item.sub?.ifBlank { null } ?: item.label,
                        dek = item.blurb?.ifBlank { null },
                        chip = item.kind?.ifBlank { null } ?: item.label,
                        /* The site's own two, decided by what the
                           thing IS rather than by a flag this app
                           invented: a course is opened and a
                           collection of writing is browsed. */
                        go = if (item.kind == "কোর্স") "কোর্সটা খুলুন" else "লেখাগুলো দেখুন",
                        art = { Icon(item.icon, size = 18.dp) },
                        onOpen = { onOpen(item) },
                    )
                }
            }
        }
    }
}

/**
 * The case studies.
 *
 * Every one of them is a live model on the site: a page with
 * sliders on it, not a write-up. So each is a `GoCard` that
 * opens on the site, and the card says so rather than pretending
 * the app renders it. `kind` is what the thing IS, which the
 * site's own `PAGES` table carries and the chip prints.
 */
@Composable
fun PortfolioScreen(
    cases: List<PageEntry>,
    head: PageHeadWords?,
    bottomPadding: Dp,
    onOpen: (PageEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier.fillMaxSize().padding(horizontal = Gap.s8),
        contentPadding = PaddingValues(top = TOP_CLEARANCE, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(Gap.s6),
    ) {
        item("head") {
            HubHead(head, "Portfolio")
            Spacer(Modifier.height(Gap.s5))
        }
        items(cases, key = { it.url }) { page ->
            GoCard(
                title = page.short?.ifBlank { null } ?: page.title,
                dek = page.blurb?.ifBlank { null },
                chip = page.kind?.ifBlank { null } ?: page.hint,
                /* Said out loud on the card. Each of these is a
                   live model with sliders on it, and this app
                   does not draw one: a card that opened a browser
                   without warning would be the promise it cannot
                   keep. */
                go = "Open on the site",
                onOpen = { onOpen(page) },
            )
        }
        item("why") {
            Spacer(Modifier.height(Gap.s5))
            InfoCard(
                title = "These are live models",
                dek = "Each one has sliders and recomputes as you drag them, which is " +
                    "the whole point of it. They open in your browser.",
            )
        }
    }
}
