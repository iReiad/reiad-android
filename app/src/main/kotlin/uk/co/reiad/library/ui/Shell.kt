package uk.co.reiad.library.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.window.core.layout.WindowWidthSizeClass
import uk.co.reiad.library.core.Accents
import uk.co.reiad.library.core.Kind
import uk.co.reiad.library.core.Motion
import uk.co.reiad.library.core.NavGroup
import uk.co.reiad.library.core.NavItem
import uk.co.reiad.library.core.SiteManifest
import uk.co.reiad.library.core.orderFor

/* ============================================================
   The shell: a rail down the left, a bar across the top, and the
   whole menu once.

   **The menu is the site's, and there is no copy of it here.**
   `shared/nav.ts` is one table with three readers on the site, and
   `/api/site` sends it whole. So a school added there appears in
   this app's bar, in its drawer and in its search, in its own
   colour, with no release. Building any of these three from a
   `when` over destinations would have broken that on the first
   day, silently, in the way the whole `/api/site` contract exists
   to prevent.

   ---- what adapts, and what does not ----

   | | |
   | --- | --- |
   | compact, a phone | a bottom bar, and the full menu behind it in a drawer |
   | medium, a small tablet or an unfolded foldable | a rail down the left, the same drawer |
   | expanded, a large tablet | the rail, open, with its labels showing |

   The BAR is the groups, not a list of screens somebody chose:
   whatever the site's nav table holds, in the order the reader's
   audience asks for. Five groups today, and if there are six
   tomorrow there are six here.

   ---- the audience reorders and never hides ----

   `orderFor` in core is where that is enforced. A group the order
   forgets is appended rather than dropped, so the rule survives
   the site growing a group and forgetting to add it to one of the
   two lists.
   ============================================================ */

/** Which of the three layouts this window is. */
enum class Chrome { BAR, RAIL, RAIL_OPEN }

@Composable
fun rememberChrome(): Chrome {
    val width = currentWindowAdaptiveInfo().windowSizeClass.windowWidthSizeClass
    return when (width) {
        WindowWidthSizeClass.EXPANDED -> Chrome.RAIL_OPEN
        WindowWidthSizeClass.MEDIUM -> Chrome.RAIL
        else -> Chrome.BAR
    }
}

/** Everything the shell needs to know about where the reader is
    and what they have chosen. Passed in rather than reached for,
    so the shell has no opinion about how state is held. */
data class ShellState(
    val site: SiteManifest?,
    val current: String?,
    val audience: String?,
    val drawerOpen: Boolean,
    /** Only the account control's LABEL depends on this, and it
        defaults to false so a caller that has not asked yet says
        "sign in" rather than greeting a stranger. */
    val signedIn: Boolean = false,
)

/** The groups this reader should meet, in their order.

    Derived from the manifest every time rather than remembered,
    because the manifest is what changes: a refresh that brings a
    sixth group has to reach the bar without a restart. */
fun groupsFor(site: SiteManifest?, audience: String?): List<NavGroup> {
    val groups = site?.nav.orEmpty()
    if (groups.isEmpty()) return emptyList()
    val order = site?.order?.get(audience)
    val ids = orderFor(groups.map { it.id }, order)
    return ids.mapNotNull { id -> groups.firstOrNull { it.id == id } }
}

@Composable
fun Shell(
    state: ShellState,
    onHome: () -> Unit,
    onGroup: (NavGroup) -> Unit,
    onItem: (NavItem) -> Unit,
    onDrawer: (Boolean) -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    /** The account, from the bar. The site's own top bar carries
        this control on every page and the app's carried search and
        the theme toggle and stopped, which left the account behind
        More and four groups of drawer. */
    onAccount: () -> Unit,
    onAudience: (String) -> Unit,
    content: @Composable () -> Unit,
) {
    val chrome = rememberChrome()
    val groups = groupsFor(state.site, state.audience)

    /* What the two floating bars actually came to, in pixels,
       fed back so a page knows how far to keep clear. Nought
       until the first layout pass, which is what the seeded
       default in `LocalChromeGaps` is for. */
    var topPx by remember { mutableIntStateOf(0) }
    var bottomPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val gaps = ChromeGaps(
        top = if (topPx > 0) with(density) { topPx.toDp() } + Gap.s6 else TOP_BAR_ONLY,
        bottom = if (bottomPx > 0) with(density) { bottomPx.toDp() } + Gap.s6 else BOTTOM_BAR_ONLY,
    )
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    CompositionLocalProvider(LocalChromeGaps provides gaps) {
    Box(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxSize()) {
            if (chrome != Chrome.BAR && groups.isNotEmpty()) {
                Rail(
                    groups, state.current, chrome == Chrome.RAIL_OPEN,
                    onHome, onItem, onSearch, onSettings,
                )
            }
            Box(Modifier.weight(1f).fillMaxHeight()) {
                content()
                if (chrome == Chrome.BAR) {
                    /* The site's own top bar, and it is the app's
                       identity as much as the colours are: every
                       page of reiad.co.uk is a floating pill with
                       the name in it and the two controls that
                       are not destinations. The app had neither,
                       so search was reachable only through a
                       drawer nobody opens for it. */
                    /* Under the bar and over the page, so prose
                       leaves before it reaches the clock. */
                    BarScrim(
                        /* Down to the bar's own top edge, so the
                           strip between the system's bar and this
                           one is covered too: at `Gap.s7` there
                           were ten unfaded dp in between and a
                           sentence could sit in them. */
                        height = statusBar + Gap.s5 + Gap.s6,
                        fromTop = true,
                        modifier = Modifier.align(Alignment.TopCenter),
                    )
                    TopBar(
                        name = state.site?.site?.name ?: "Reiad's Library",
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            /* `ChromeGapTest` reads this back: the
                               clearance a page keeps has to be
                               what the bar came to. */
                            .testTag("topbar")
                            .onSizeChanged { topPx = it.height },
                        signedIn = state.signedIn,
                        /* The key in `nav.ts`'s `you` group. Said
                           here rather than passed in, because the
                           bar reads the same field to decide the
                           same question and two callers deciding
                           it separately is how they part. */
                        onAccountPage = state.current == "account",
                        onHome = onHome,
                        onSearch = onSearch,
                        onSettings = onSettings,
                        onAccount = onAccount,
                    )
                }
                if (chrome == Chrome.BAR && groups.isNotEmpty()) {
                    BarScrim(
                        height = navBar + Gap.s5 + Gap.s6,
                        fromTop = false,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                    Bar(
                        groups = groups,
                        current = state.current,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .testTag("bottombar")
                            .onSizeChanged { bottomPx = it.height },
                        onHome = onHome,
                        onGroup = onGroup,
                        onMore = { onDrawer(true) },
                    )
                }
            }
        }

        if (state.drawerOpen) {
            Drawer(
                site = state.site,
                groups = groups,
                current = state.current,
                audience = state.audience,
                onItem = { onDrawer(false); onItem(it) },
                onAudience = onAudience,
                onSettings = { onDrawer(false); onSettings() },
                onClose = { onDrawer(false) },
            )
        }
    }
    }
}

/* ---------- how far a page has to keep clear ----------

   Both bars FLOAT over the page rather than pushing it, which is
   what makes them glass: a reader scrolls prose under them and
   sees it through them. The cost is that every screen has to open
   below the top one and end above the bottom one, and a screen
   that forgets is a screen whose first line is behind a bar.

   Written down once here rather than at each of eleven call
   sites. The top bar arrived after those eleven were written, so
   every one of them opened at the old distance and every one of
   them had its heading cut in half. */

/** What the bar is on its own, before the system's own bars are
    added to it. Not the answer: `topClearance()` is. */
private val TOP_BAR_ONLY = 84.dp
private val BOTTOM_BAR_ONLY = 96.dp

/** How tall each floating bar actually measured, plus a gap.

    **Measured rather than written down, and that is the whole
    fix.** Both of these were constants, and both were wrong on
    every phone with a notch: a bar carries
    `windowInsetsPadding(statusBars)`, so its bottom edge sits
    that much lower than the number said, and the page opened
    UNDER it. The first heading of every screen was cut in half
    and prose ran into the clock. It is worse than a fixed number
    that is merely too small, because it is right on whatever
    device it was tuned on: an emulator with no cutout.

    A bar's height is not a constant for a second reason. It holds
    text, and the reader chooses the type size, so at 150% the bar
    grows and a constant cannot follow it.

    Seeded with the analytic guess so the first frame is close and
    nothing jumps, then replaced by what the bar measured. */
data class ChromeGaps(val top: Dp, val bottom: Dp)

val LocalChromeGaps = compositionLocalOf { ChromeGaps(TOP_BAR_ONLY, BOTTOM_BAR_ONLY) }

/** How far below the top of the window a page's first line goes. */
@Composable
fun topClearance(): Dp =
    if (rememberChrome() == Chrome.BAR) LocalChromeGaps.current.top else Gap.s10

/** How far above the bottom of the window a page's last line
    ends. A rail has no bottom bar, so there only the system's own
    navigation bar has to be cleared. */
@Composable
fun barClearance(): Dp =
    if (rememberChrome() == Chrome.BAR) {
        LocalChromeGaps.current.bottom
    } else {
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + Gap.s8
    }

/** What a scrolling page's `contentPadding` should be.

    Asks the chrome, so a tablet with a rail down the side gets
    the smaller top gap it deserves: there is no top bar there. */
@Composable
fun pagePadding(horizontal: Dp = Gap.s8, extraTop: Dp = 0.dp): PaddingValues =
    PaddingValues(
        start = horizontal,
        end = horizontal,
        top = topClearance() + extraTop,
        bottom = barClearance(),
    )

/** The fade behind a system bar.

    A glass bar that floats lets a reader see prose through it,
    which is the point. The system's own bar is not glass and does
    not move: the clock and the battery are painted on top of
    whatever scrolls under them, so a line of Bangla arriving
    there collides with the time and both become unreadable. This
    is the page's own ground fading out under it, so text leaves
    rather than crashes.

    Not a solid block, because a hard edge across the top of the
    screen is a title bar, and this site does not have one. */
@Composable
private fun BarScrim(height: Dp, fromTop: Boolean, modifier: Modifier = Modifier) {
    if (height <= 0.dp) return
    val c = LocalReiad.current
    /* Solid for most of its height and then a short fade, rather
       than fading the whole way. A gradient that starts fading at
       the top leaves prose legible right up to the bar's edge,
       which is what a photograph of a real phone showed: a line
       of Bangla ending under the clock. */
    val stops = listOf(c.paper, c.paper, c.paper.copy(alpha = 0.72f), Color.Transparent)
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .background(
                if (fromTop) {
                    Brush.verticalGradient(stops)
                } else {
                    Brush.verticalGradient(stops.reversed())
                },
            ),
    )
}

/* ---------- the top bar ---------- */

/** One of the round buttons in the top bar.

    **Not a `control`, and that is the design system's own
    answer rather than a saving.** `--standing` is the axis for
    exactly this: a LONE button has to look pressable because
    nothing else says it is, and a row of them does not, because
    the row is the affordance. Three 44dp circles of glass, each
    with its own hairline rim, ten apart, read as one
    three-lobed object and were reported twice as congested.

    The target is still 44dp. What went is the ring around it,
    not the room to press it. */
@Composable
private fun RoundButton(
    icon: String,
    label: String,
    onClick: () -> Unit,
    /** Whether this is the place the reader is standing.

        The bar says where you are and one control up here is a
        destination too, so it has to be able to say the same
        thing: a person button that looks identical on the account
        page and off it is a reader pressing it to find out. */
    here: Boolean = false,
) {
    val c = LocalReiad.current
    Box(
        Modifier
            .size(Gap.tap)
            .clip(RoundedCornerShape(Corner.pill))
            /* Only the one you are on has a ground, which is the
               bar's own rule one level up and the site's
               `--standing` axis: three circles with the same rest
               state read as three boxes. */
            .then(
                if (!here) Modifier
                else Modifier.material(
                    kind = Kind.CHIP,
                    colours = c,
                    corner = Corner.pill,
                    ground = c.accentSoft,
                ),
            )
            .clickable(role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = label
                if (here) selected = true
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, size = 19.dp, tint = if (here) c.accent else c.ink)
    }
}

/**
 * The site's top bar: a floating pill with the name in it.
 *
 * Every page of the site has this and the app had none of it, so
 * a reader arriving from the site met something that shared its
 * colours and none of its furniture. It carries the two controls
 * that are not destinations, which is why they are not in the
 * bottom bar: search and the reader's own settings act ON the
 * page rather than taking you off it.
 *
 * The name is a link home, exactly as the site's is.
 */
@Composable
fun TopBar(
    name: String,
    modifier: Modifier = Modifier,
    /** Which of the two labels the account button wears. The site
        says "Sign in to Reiad's Library" or "Signed in as <name>.
        Open your account menu.", and the difference is the whole
        of what a reader learns from the control before pressing
        it. */
    signedIn: Boolean = false,
    /** Whether the account is the page on screen. The bottom bar
        deliberately never offers the account, so this control is
        the only one that can say so. */
    onAccountPage: Boolean = false,
    onHome: () -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onAccount: () -> Unit = {},
) {
    val c = LocalReiad.current
    Row(
        modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = Gap.s7, vertical = Gap.s5)
            .clip(RoundedCornerShape(Corner.pill))
            .material(Kind.PANE, c, Corner.pill)
            .padding(horizontal = Gap.s5, vertical = Gap.s4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .weight(1f)
                .heightIn(min = Gap.tap)
                .clip(RoundedCornerShape(Corner.pill))
                .clickable(role = Role.Button, onClick = onHome)
                .padding(horizontal = Gap.s4, vertical = Gap.s3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon("bars", size = 18.dp, tint = c.accent)
            Spacer(Modifier.width(Gap.s5))
            Text(
                name,
                style = MaterialTheme.typography.titleSmall,
                color = c.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        /* One step wider than they were. Three 44dp circles, each
           with its own hairline rim, eight apart read as one
           three-lobed object rather than three buttons. */
        Spacer(Modifier.width(Gap.s5))
        RoundButton("search", "Search", onSearch)
        Spacer(Modifier.width(Gap.s5))
        RoundButton("sliders", "Settings", onSettings)
        Spacer(Modifier.width(Gap.s5))
        /* Third and last, which is where `aab/src/signin.ts`
           appends it on the site: after the theme toggle, so the
           order does not change for anybody used to it.

           The word "account" is in both labels on purpose. It is
           what a reader says when they cannot find it, so it is
           what `AccountReachTest` looks for. */
        RoundButton(
            icon = "user",
            label = if (signedIn) {
                "Your account"
            } else {
                "Sign in to your account"
            },
            onClick = onAccount,
            here = onAccountPage,
        )
    }
}

/* ---------- the bar ---------- */

/** The groups, across the bottom, and one more button.

    A PANE, because it holds other things, floating one gap above
    the navigation bar rather than welded to the bottom edge: the
    site's own top bar is a pill with a gap under it, and a reader
    who has learnt that the floating thing is the site's controls
    reads a second floating thing the same way.

    Each destination is a `--standing: 0` row for the reason the
    site's rail items are: there are five of them in a line and
    the LINE is the affordance. Only the one you are on stands. */
@Composable
private fun Bar(
    groups: List<NavGroup>,
    current: String?,
    modifier: Modifier = Modifier,
    onHome: () -> Unit,
    onGroup: (NavGroup) -> Unit,
    onMore: () -> Unit,
) {
    val c = LocalReiad.current

    /* Home first, and it is not in the nav table.

       The site has no home ENTRY: its rail draws the link
       separately because a table of destinations does not need a
       row saying "the top". On a phone it does: with only group
       tabs, a reader who opened a group could get back to the
       front page by system back and by nothing else, which is a
       way out that leaves no mark on screen. */
    val stops = buildList {
            add(BarStop("home", "Home", c.accent, null, onHome))
            for (group in barGroups(groups, current)) {
                add(
                    BarStop(
                        /* A group has no icon of its own in the
                           site's table, so it wears its first
                           item's. That is a choice rather than a
                           fact, and it works because the first
                           item of a group is the one the group is
                           named after. */
                        icon = group.items.firstOrNull()?.icon ?: "home",
                        label = tabLabel(group.label),
                        accent = accentColour(group.accent, c),
                        key = group.id,
                        open = { onGroup(group) },
                    ),
                )
            }
        add(BarStop("menu", "More", c.accent, null, onMore))
    }

    val here = stops.firstOrNull { stop ->
        if (stop.key == null) current == null && stop.label == "Home"
        else groups.firstOrNull { it.id == stop.key }
            ?.items?.any { it.key != null && it.key == current } == true
    }

    Box(
        modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = Gap.s7, vertical = Gap.s5)
            .clip(RoundedCornerShape(Corner.pill))
            .material(Kind.PANE, c, Corner.pill)
            .padding(Gap.s3),
    ) {
        /* One gesture across the whole bar, with the glass thumb
           under the finger the whole way: press and slide through
           the destinations, and it opens the one you let go over.
           A tap is the same gesture with no travel in it.

           `Segmented` is the same control every switch on this
           site uses now, for the reason its own head gives: a row
           of separate hit targets is where a press ends up on the
           wrong one. */
        Segmented(
            options = stops,
            chosen = here,
            onChoose = { it.open() },
            height = Gap.tap + Gap.s3,
            label = { it.label },
            /* The thumb is a quiet panel rather than the accent:
               on a bar the accent belongs to the icon, and five
               accent tiles in a row is a bar with no answer to
               "which one am I on". */
            thumbGround = c.accentSoft,
            thumbInset = 0.dp,
        ) { stop, on ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(stop.icon, size = 19.dp, tint = if (on) stop.accent else c.inkSoft)
                Spacer(Modifier.height(2.dp))
                Text(
                    stop.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (on) stop.accent else c.inkSoft,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** One destination on the bar.

    `key` is the group's id, or null for the two that are not
    groups: Home and More. It is what says which stop the reader
    is standing on, and it is an id rather than a label because a
    label is bilingual and gets cut down to fit. */
private data class BarStop(
    val icon: String,
    val label: String,
    val accent: Color,
    val key: String?,
    val open: () -> Unit,
)

/**
 * A group's name, cut down to something a tab can hold.
 *
 * The site's group labels are bilingual and written for a menu
 * that has a whole line for each: "শেখা · Learning", "আপনার ·
 * Yours". Five of those across a handset truncate into
 * "শেখা ·", "Lea…", "কাজে লাগান", "…", which is the congestion
 * the app shipped with.
 *
 * The Bangla half is the one kept, because Bangla is the site's
 * learning language and the tab beneath an icon is a reminder
 * rather than a description. Nothing is invented and nothing is
 * abbreviated: the label is split on the separator the table
 * already uses.
 */
internal fun tabLabel(label: String): String =
    label.substringBefore("·").trim().ifBlank { label }

/** Which groups the bar can hold, and the rule that the one you
    are ON is always among them.

    Home, three groups and a More: five targets, which is the most
    a phone bar should carry. Which three is the audience's order,
    EXCEPT that a reader standing in a group further down would
    otherwise see a bar with nothing selected on it, which reads
    as "you are nowhere". So the current group displaces the last
    of the three.

    This is not the audience switch hiding something: every group
    is in the drawer, one tap away, and the switch's own promise
    is about the MENU. A bar is not the menu, and saying so here
    is cheaper than pretending a phone is a desktop.

    **`you` IS NEVER ONE OF THEM, AND THAT IS THE RULE RATHER THAN
    A CHOICE ABOUT SPACE.** Its one listed item is the account,
    the account has a control of its own in the top bar, and the
    displacement above put the two on screen at once: standing on
    `/account`, a reader saw the person button lit at the top
    right and an "আপনার" tab lit at the bottom, both going to the
    page they were already on. Two controls for one destination is
    a reader asking what the difference is, and there is none. */
private fun barGroups(groups: List<NavGroup>, current: String?, slots: Int = 3): List<NavGroup> {
    val offered = groups.filter { it.id != "you" }
    if (offered.size <= slots) return offered
    val first = offered.take(slots)
    val standing = offered.firstOrNull { g -> g.items.any { it.key != null && it.key == current } }
    if (standing == null || standing in first) return first
    return first.dropLast(1) + standing
}

/* ---------- the rail ---------- */

@Composable
private fun Rail(
    groups: List<NavGroup>,
    current: String?,
    open: Boolean,
    onHome: () -> Unit,
    onItem: (NavItem) -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
) {
    val c = LocalReiad.current
    Column(
        Modifier
            .width(if (open) 232.dp else 76.dp)
            .fillMaxHeight()
            .material(Kind.PANE, c, corner = 0.dp, ground = c.paperSunk)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(vertical = Gap.s7, horizontal = Gap.s5)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = if (open) Alignment.Start else Alignment.CenterHorizontally,
    ) {
        RailRow("home", "Home", current == null, c.accent, open, onHome)
        RailRow("search", "Search", false, c.accent, open, onSearch)
        Spacer(Modifier.height(Gap.s6))

        for (group in groups) {
            if (open) {
                Text(
                    group.label.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = c.inkSoft,
                    modifier = Modifier.padding(start = Gap.s6, top = Gap.s5, bottom = Gap.s3),
                )
            }
            for (item in group.items) {
                RailRow(
                    icon = item.icon,
                    label = item.label,
                    selected = item.key != null && item.key == current,
                    accent = accentColour(item.accent ?: group.accent, c),
                    open = open,
                    /* THE ITEM, not the group it is in.

                       Every row here opened its group, which is
                       the bottom bar's rule applied one level
                       down where it is wrong: a bar shows five
                       GROUPS and a rail shows every ITEM, so a
                       row reading "Account" sent a reader to a
                       list with Account on it. Seventeen rows,
                       each landing one tap short of what it
                       named, and the rail renders identically
                       either way. */
                    onClick = { onItem(item) },
                )
            }
            if (!open) Spacer(Modifier.height(Gap.s5))
        }

        Spacer(Modifier.height(Gap.s7))
        RailRow("theme", "Settings", false, c.accent, open, onSettings)
    }
}

@Composable
private fun RailRow(
    icon: String,
    label: String,
    selected: Boolean,
    accent: Color,
    open: Boolean,
    onClick: () -> Unit,
) {
    val c = LocalReiad.current
    val glow = rememberGlow()
    Row(
        Modifier
            .then(if (open) Modifier.fillMaxWidth() else Modifier)
            .height(Gap.tap)
            .clip(RoundedCornerShape(Corner.pill))
            .material(
                kind = Kind.CARD,
                colours = c,
                corner = Corner.pill,
                ground = if (selected) accent.copy(alpha = 0.14f) else Color.Transparent,
                lit = { glow.lit },
            )
            .follows(glow)
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = Gap.s6),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, size = 20.dp, tint = if (selected) accent else c.inkSoft)
        if (open) {
            Spacer(Modifier.width(Gap.s6))
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (selected) c.ink else c.inkSoft,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/* ---------- the drawer ---------- */

/** The whole table, with its headings, and the audience switch at
    the top of it.

    On the site this is the same menu the rail shows, and the
    reason it exists separately on a phone is that a rail of
    sixteen destinations does not fit across the bottom of one.
    What it is NOT is a different menu: it reads the same list. */
@Composable
private fun Drawer(
    site: SiteManifest?,
    groups: List<NavGroup>,
    current: String?,
    audience: String?,
    onItem: (NavItem) -> Unit,
    onAudience: (String) -> Unit,
    onSettings: () -> Unit,
    onClose: () -> Unit,
) {
    val c = LocalReiad.current
    val reduced = rememberReducedMotion()
    val retreat = rememberRetreat(onBack = onClose)
    Box(
        Modifier
            .fillMaxSize()
            /* The scrim closes it. Not a decoration: on a phone
               the outside of a sheet is the biggest and most
               obvious target there is. */
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(
                indication = null,
                interactionSource = remembering(),
                onClick = onClose,
            ),
    ) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .retreating(retreat, reduced)
                .clip(RoundedCornerShape(topStart = Corner.lg, topEnd = Corner.lg))
                .material(Kind.PANE, c, Corner.lg, ground = c.paper)
                .clickable(indication = null, interactionSource = remembering()) { }
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = Gap.s8, vertical = Gap.s8)
                .verticalScroll(rememberScrollState()),
        ) {
            site?.audiences?.takeIf { it.size > 1 }?.let { audiences ->
                AudienceSwitch(audiences.map { it.id to it.label }, audience, onAudience)
                Spacer(Modifier.height(Gap.s8))
            }

            for (group in groups) {
                Text(
                    group.label.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = c.inkSoft,
                    modifier = Modifier.padding(bottom = Gap.s4),
                )
                for (item in group.items) {
                    DrawerRow(
                        item = item,
                        selected = item.key != null && item.key == current,
                        accent = accentColour(item.accent ?: group.accent, c),
                        onClick = { onItem(item) },
                    )
                }
                Spacer(Modifier.height(Gap.s7))
            }

            Rung(Modifier.clickable(role = Role.Button, onClick = onSettings)) {
                Icon("theme", size = 20.dp, tint = c.inkSoft)
                Spacer(Modifier.width(Gap.s6))
                Text("Settings", style = MaterialTheme.typography.bodyLarge, color = c.ink)
            }
            Spacer(Modifier.height(Gap.s8))
        }
    }
}

@Composable
private fun DrawerRow(
    item: NavItem,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
) {
    val c = LocalReiad.current
    Rung(Modifier.clickable(role = Role.Button, onClick = onClick)) {
        Icon(item.icon, size = 20.dp, tint = accent)
        Spacer(Modifier.width(Gap.s6))
        Column(Modifier.weight(1f)) {
            Text(
                item.label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (selected) accent else c.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            item.sub?.let {
                Text(
                    it,
                    style = BanglaBody.copy(
                        fontSize = MaterialTheme.typography.bodySmall.fontSize,
                        lineHeight = MaterialTheme.typography.bodySmall.fontSize * 1.5f,
                    ),
                    color = c.inkSoft,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        /* The right of the row, which was empty on every one of
           twenty rows: an icon and two lines hard against the
           left edge of a full-width tile, and then nothing. It
           was reported by somebody drawing on a photograph of it.

           What goes there is what the row IS, out of the nav
           table's own `kind`, and then a chevron. Both are facts
           rather than filler: the chip says whether this is a
           course or a piece of writing, and the chevron says the
           row goes somewhere, which on a list where some rows
           open in the app and some leave for the browser is worth
           saying. */
        if (item.soon) {
            Chip("আসছে")
        } else {
            item.kind?.takeIf { it.isNotBlank() }?.let { kind ->
                Chip(kind, tone = c.inkSoft)
                Spacer(Modifier.width(Gap.s5))
            }
            Icon("chevron", size = 15.dp, tint = c.inkSoft)
        }
    }
}

/* ---------- the audience switch ---------- */

/** A groove with a control riding in it, which is the site's own
    segmented control said in the material's words: the track is
    a channel cut in and the thumb is a thing sitting on it.

    It REORDERS and never hides, and the label under it says so,
    because a switch whose effect a reader cannot predict is a
    switch that gets pressed once. */
@Composable
fun AudienceSwitch(
    options: List<Pair<String, String>>,
    chosen: String?,
    onChoose: (String) -> Unit,
) {
    val c = LocalReiad.current
    Column {
        /* Hold and slide, like every other switch here. The
           groove is a tap taller than a tap, because what rides
           in it is the target: at `Gap.tap` with the channel's
           own padding the two thumbs came to 36dp each. */
        Segmented(
            options = options,
            chosen = options.firstOrNull { it.first == chosen },
            onChoose = { onChoose(it.first) },
            label = { it.second },
        ) { option, on ->
            Text(
                option.second,
                style = MaterialTheme.typography.labelLarge,
                color = if (on) c.paper else c.inkSoft,
            )
        }
        Spacer(Modifier.height(Gap.s4))
        Text(
            "This reorders the menu. Nothing is hidden either way.",
            style = MaterialTheme.typography.bodySmall,
            color = c.inkSoft,
        )
    }
}

/* ---------- odds ---------- */

/** The site sends an accent as a token name, `var(--blue)`, which
    is the stylesheet's own spelling. Resolved through the table
    in core so the app and the site cannot disagree about what
    blue is, and falling back to the page's own accent rather than
    to a colour typed here. */
@Composable
fun accentColour(token: String?, colours: ReiadColours): Color {
    val accent = token?.let { Accents.byToken(it) } ?: return colours.accent
    return coloursOf(accent, colours.isDark).accent
}

@Composable
private fun remembering() = androidx.compose.runtime.remember {
    androidx.compose.foundation.interaction.MutableInteractionSource()
}
