package uk.co.reiad.library.ui

import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import uk.co.reiad.library.core.NavGroup
import uk.co.reiad.library.core.PageHeadWords
import uk.co.reiad.library.core.hubItem
import uk.co.reiad.library.core.rowsOf
import uk.co.reiad.library.core.NavItem
import uk.co.reiad.library.core.SITE_ORIGIN

/* ============================================================
   A group of the menu, as a screen.

   This exists because of what the alternative was. The bar has a
   tab per group and only one group, the learning one, holds
   things this app can open by itself. Sending the other four
   Home makes four tabs that appear to do nothing, which is worse
   than not having them: a reader presses once, nothing happens,
   and they stop trusting the bar.

   So every item in every group opens. A school with a ladder
   opens HERE. Anything else opens the page on the site, in a
   Custom Tab, which keeps the reader's session, the theme and the
   back gesture, and says plainly where they are.

   **The tab is honest about which is which.** A card that opens
   in the app and a card that leaves for the web are different
   promises, and the action written at the bottom of a `GoCard` is
   where the difference is said: "পড়া শুরু" against "Open on the
   site". Making them look identical would be the `.cell` problem
   this app already has two card types to avoid.

   ---- and a Custom Tab is not a WebView ----

   A WebView here would mean a second browser with its own cookie
   jar, its own storage and no session: a reader signed in on the
   site would be signed out inside the app, on the same phone. A
   Custom Tab is the reader's own browser, so they are already
   signed in and their reading preferences are already applied.
   ============================================================ */

@Composable
fun GroupScreen(
    group: NavGroup,
    accents: Map<String, String>,
    bottomPadding: Dp,
    canOpenHere: (NavItem) -> Boolean,
    onOpenHere: (NavItem) -> Unit,
    /** Every hub page's own words, out of `/api/site`. The tab
        for a group that HAS a hub is that hub, so it says what
        the site's page says rather than repeating the group's
        two-word label. */
    heads: Map<String, PageHeadWords> = emptyMap(),
) {
    val c = LocalReiad.current
    val context = LocalContext.current

    /* A group's own front page, listed inside itself, is a card
       that takes you to the list you are reading. It was the
       first card of the Learning tab: দক্ষতা, with no blurb,
       leading to a copy. The flag is `shared/nav.ts`'s, so this
       needs no release the day a fifth hub appears.

       Kept where it is the ONLY item, because the alternative is
       a tab with nothing on it: the reading group is one entry
       and that entry is its hub. */
    val hub = group.hubItem()
    val rows = rowsOf(group)
    val head = hub?.key?.let { heads[it] }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = Gap.s8),
        contentPadding = PaddingValues(top = topClearance(), bottom = bottomPadding),
    ) {
        item {
            PageHead(
                /* The hub page's own head where there is one, and
                   the group's two halves where there is not: the
                   Bangla name is the heading and the English is
                   the eyebrow above it. */
                title = head?.title?.ifBlank { null } ?: tabLabel(group.label),
                eyebrow = head?.eyebrow?.ifBlank { null }
                    ?: group.label.substringAfter("\u00b7", "").trim().ifBlank { null },
                lede = head?.lede?.ifBlank { null },
            )
            Spacer(Modifier.height(Gap.s9))
        }

        items(rows, key = { it.href }) { item ->
            val accent = accents[item.key] ?: item.accent ?: group.accent
            ReiadTheme(
                accent = accentOf(accent),
                dark = c.isDark,
            ) {
                Column {
                    when {
                        /* Promised and not written. A card that
                           goes nowhere is not a link, which is
                           why this is a different component
                           rather than a disabled one. */
                        item.soon -> SoonCard(
                            title = item.sub ?: item.label,
                            dek = item.blurb,
                        )

                        canOpenHere(item) -> GoCard(
                            /* The disc, from the icon this item
                               already names in the nav table. It
                               was missing here and present on the
                               front page, which made the same
                               destination two different cards. */
                            art = { Icon(item.icon, size = 18.dp) },
                            title = item.sub ?: item.label,
                            dek = item.blurb,
                            chip = item.kindLabel(),
                            /* The site's own two, decided by what
                               the thing IS. Six cards in a column
                               all saying পড়া শুরু is six cards
                               saying nothing. */
                            go = item.goWords(),
                            onOpen = { onOpenHere(item) },
                        )

                        else -> GoCard(
                            art = { Icon(item.icon, size = 18.dp) },
                            title = item.sub ?: item.label,
                            dek = item.blurb,
                            chip = item.kindLabel(),
                            /* Said in words, because leaving the
                               app is a thing a reader should be
                               told about before it happens rather
                               than after. */
                            go = "Open on the site",
                            onOpen = { openOnSite(context, item.href, c) },
                        )
                    }
                    Spacer(Modifier.height(Gap.s7))
                }
            }
        }
    }
}

/**
 * The chip's word.
 *
 * The item's own English LABEL first, and its `kind` only where
 * there is no label to use. Three schools in one group all carry
 * `kind: "কোর্স"`, so keying on that drew three identical chips
 * on three cards whose titles were the only thing telling them
 * apart, while the front page's cards said MONEY, GERMAN and
 * QUR'ANIC ARABIC.
 */
private fun NavItem.kindLabel(): String? =
    label.takeIf { it.isNotBlank() } ?: kind?.takeIf { it.isNotBlank() }

/** What the card's action line says.

    `/skills` on the site writes one of two, off the entry's own
    kind: a course is opened and a collection of writing is
    browsed. This said পড়া শুরু on every card of every group,
    including the calculators, where nothing is read at all. */
private fun NavItem.goWords(): String = when {
    kind == "\u0995\u09cb\u09b0\u09cd\u09b8" -> "\u0995\u09cb\u09b0\u09cd\u09b8\u099f\u09be \u0996\u09c1\u09b2\u09c1\u09a8"
    ladder -> "\u09b2\u09c7\u0996\u09be\u0997\u09c1\u09b2\u09cb \u09a6\u09c7\u0996\u09c1\u09a8"
    else -> "\u0996\u09c1\u09b2\u09c1\u09a8"
}

/** The reader's own browser, tinted to match, with the site's
    address bar left visible so nobody is in any doubt about
    where they are. */
fun openOnSite(context: Context, href: String, colours: ReiadColours) {
    val url = if (href.startsWith("http")) href else SITE_ORIGIN + href
    runCatching {
        CustomTabsIntent.Builder()
            .setShowTitle(true)
            .setUrlBarHidingEnabled(false)
            .setDefaultColorSchemeParams(
                androidx.browser.customtabs.CustomTabColorSchemeParams.Builder()
                    .setToolbarColor(colours.panel.toArgb())
                    .build(),
            )
            .build()
            .launchUrl(context, Uri.parse(url))
    }
    /* A device with no browser at all is not a crash. Nothing
       opens and the reader is where they were, which is the only
       honest answer available. */
}
