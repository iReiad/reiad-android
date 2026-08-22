package uk.co.reiad.library.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import uk.co.reiad.library.broker.Account212
import uk.co.reiad.library.broker.Standing
import uk.co.reiad.library.broker.Trouble
import uk.co.reiad.library.core.broker.Holding
import uk.co.reiad.library.core.broker.Month
import uk.co.reiad.library.core.broker.PublicPortfolio
import uk.co.reiad.library.core.broker.Totals
import uk.co.reiad.library.core.broker.dividendTotal
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs

/* ============================================================
   The live portfolio.

   One real account, three ways, exactly as the site shows it:

   | a stranger          | percentages. A weight and a return teach a lesson; a balance only says how much money somebody else has. |
   | a reader with a key | the same dashboard over their OWN account, in full |
   | nobody at all       | a page that says the site has not connected one yet, rather than an empty box |

   ---- what this file is careful about ----

   Money that is not the reader's. Every figure a stranger sees is
   a percentage, and that is enforced on the SERVER: this screen
   renders `PublicPortfolio`, which has no cash figures in it to
   render. A client that filtered would be a client that had
   already been sent the thing it was hiding.
   ============================================================ */

/** Everything the screen draws, and which of the three it is. */
data class LiveState(
    val site: PublicPortfolio? = null,
    val standing: Standing? = null,
    val own: Account212? = null,
    val ownTotals: Totals? = null,
    val ownHoldings: List<Holding> = emptyList(),
    val dividends: List<Month> = emptyList(),
    val trouble: Trouble? = null,
    val loading: Boolean = true,
)

@Composable
fun LiveScreen(
    state: LiveState,
    onConnect: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val c = LocalReiad.current

    LazyColumn(
        modifier.fillMaxWidth(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(Gap.s7),
    ) {
        item {
            Column(Modifier.padding(top = Gap.s6)) {
                Text(
                    "Live",
                    style = MaterialTheme.typography.labelMedium,
                    color = c.accent,
                )
                Text(
                    "One real portfolio",
                    style = MaterialTheme.typography.headlineSmall,
                    color = c.ink,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.height(Gap.s4))
                Text(
                    "Not a model and not a backtest: a Trading 212 account as it stands "
                        + "right now. Connect your own and this becomes your dashboard.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = c.inkSoft,
                )
            }
        }

        /* ---------- the reader's own, when there is one ---------- */
        val totals = state.ownTotals
        if (totals != null) {
            item { OwnAccount(totals, state.own?.at.orEmpty()) }

            if (state.ownHoldings.isNotEmpty()) {
                item {
                    Text(
                        "Holdings (${state.ownHoldings.size})",
                        style = MaterialTheme.typography.titleSmall,
                        color = c.ink,
                        modifier = Modifier.semantics { heading() },
                    )
                }
                items(state.ownHoldings) { h -> HoldingRow(h, totals.currency) }
            }

            if (state.dividends.isNotEmpty()) {
                item { Dividends(state.dividends, totals.currency) }
            }
        }

        /* ---------- and the site's own, which everyone sees ---------- */
        item {
            Text(
                if (totals == null) "The site's portfolio" else "The site's portfolio, for comparison",
                style = MaterialTheme.typography.titleSmall,
                color = c.ink,
                modifier = Modifier.semantics { heading() },
            )
        }

        val site = state.site
        when {
            site != null -> {
                item { SiteShare(site) }
                site.holdings?.let { rows ->
                    val largest = rows.maxOfOrNull { it.weightPct } ?: 0.0
                    items(rows) { h -> PublicHoldingRow(h, largest) }
                } ?: item {
                    Plate {
                        Text(
                            "The holdings are not published.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = c.inkSoft,
                        )
                    }
                }
            }
            state.loading -> item { Waiting() }
            else -> item { Sorry(state.trouble) }
        }

        /* ---------- the offer, where it is one ---------- */
        if (totals == null && !state.loading) {
            item { Connect(state.standing, state.trouble, onConnect) }
        }
    }
}

/* ---------- the reader's own account ---------- */

@Composable
private fun OwnAccount(t: Totals, at: String) {
    val c = LocalReiad.current
    Pane {
        Text(
            "Your account · ${whenText(at)}",
            style = MaterialTheme.typography.labelSmall,
            color = c.inkSoft,
        )
        Spacer(Modifier.height(Gap.s4))
        Text(
            money(t.total, t.currency),
            style = MaterialTheme.typography.headlineMedium,
            color = c.ink,
        )
        Text(
            "all in, ${t.currency}",
            style = MaterialTheme.typography.bodySmall,
            color = c.inkSoft,
        )
        Spacer(Modifier.height(Gap.s6))

        Figure("Invested", money(t.invested, t.currency), "cost ${money(t.cost, t.currency)}")
        Figure(
            "Unrealised", money(t.unrealised, t.currency),
            "${signed(t.unrealisedPct)} on cost", tone = sign(t.unrealised, c),
        )
        Figure(
            "Realised, all time", money(t.realised, t.currency), "",
            tone = sign(t.realised, c),
        )
        Figure(
            "Free cash", money(t.freeCash, t.currency),
            if (t.inPies > 0) "plus ${money(t.inPies, t.currency)} parked in pies" else "",
        )
    }
}

@Composable
private fun Figure(label: String, value: String, note: String, tone: Color? = null) {
    val c = LocalReiad.current
    Row(Modifier.padding(vertical = Gap.s3), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = c.inkSoft)
            if (note.isNotEmpty()) {
                Text(note, style = MaterialTheme.typography.labelSmall, color = c.inkSoft)
            }
        }
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            color = tone ?: c.ink,
            textAlign = TextAlign.End,
        )
    }
}

/* ---------- one holding, the reader's own ---------- */

@Composable
fun HoldingRow(h: Holding, currency: String) {
    val c = LocalReiad.current
    Rung {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    h.name.ifEmpty { "–" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.ink,
                    modifier = Modifier.weight(1f),
                )
                if (h.ticker.isNotEmpty()) {
                    Spacer(Modifier.width(Gap.s4))
                    Text(
                        h.ticker,
                        style = MaterialTheme.typography.labelSmall,
                        color = c.inkSoft,
                    )
                }
            }
            Spacer(Modifier.height(Gap.s2))
            /* The bar is against the LARGEST holding rather than
               against a hundred: a bar that never fills its row
               is a bar nobody can compare. */
            Groove((h.barPct / 100).toFloat(), height = 4.dp)
            Spacer(Modifier.height(Gap.s2))
            Text(
                "${qty(h.quantity)} at ${money(h.averagePaid, h.currency)} · "
                    + "now ${money(h.price, h.currency)}",
                style = MaterialTheme.typography.labelSmall,
                color = c.inkSoft,
            )
        }
        Spacer(Modifier.width(Gap.s5))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                money(h.value, currency),
                style = MaterialTheme.typography.labelLarge,
                color = c.ink,
            )
            Text(
                "${money(h.gain, currency)} (${signed(h.gainPct)})",
                style = MaterialTheme.typography.labelSmall,
                color = sign(h.gain, c) ?: c.inkSoft,
            )
            Text(
                "${plain(h.weightPct, 1)}%",
                style = MaterialTheme.typography.labelSmall,
                color = c.inkSoft,
            )
        }
    }
}

/* ---------- what a stranger sees ---------- */

@Composable
private fun SiteShare(p: PublicPortfolio) {
    val c = LocalReiad.current
    Pane {
        Text(
            "${p.count} holdings · ${whenText(p.at)}",
            style = MaterialTheme.typography.labelSmall,
            color = c.inkSoft,
        )
        Spacer(Modifier.height(Gap.s4))
        Figure("Invested", "${plain(p.investedPct, 1)}%", "of everything in the account")
        Figure("Cash", "${plain(p.cashPct, 1)}%", "waiting")
        Figure(
            "Return on holdings", signed(p.returnPct), "against what was paid",
            tone = sign(p.returnPct, c),
        )
        Spacer(Modifier.height(Gap.s4))
        /* Said out loud rather than left to be noticed. A page of
           percentages with no explanation reads as a page that
           failed to load its numbers. */
        Text(
            "Percentages, not amounts. A weight and a return teach something; "
                + "a balance only says how much money somebody else has.",
            style = MaterialTheme.typography.bodySmall,
            color = c.inkSoft,
        )
    }
}

@Composable
fun PublicHoldingRow(h: uk.co.reiad.library.core.broker.PublicHolding, largest: Double) {
    val c = LocalReiad.current
    Rung {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    h.name.ifEmpty { "–" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.ink,
                    modifier = Modifier.weight(1f),
                )
                if (h.ticker.isNotEmpty()) {
                    Text(
                        h.ticker,
                        style = MaterialTheme.typography.labelSmall,
                        color = c.inkSoft,
                    )
                }
            }
            Spacer(Modifier.height(Gap.s2))
            Groove(
                if (largest > 0) (h.weightPct / largest).toFloat() else 0f,
                height = 4.dp,
            )
        }
        Spacer(Modifier.width(Gap.s5))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                "${plain(h.weightPct, 1)}%",
                style = MaterialTheme.typography.labelLarge,
                color = c.ink,
            )
            /* Null is not nought. An admin can publish the weights
               and withhold the returns, and an empty space says
               that where a "0.0%" would be a claim. */
            h.returnPct?.let {
                Text(
                    signed(it),
                    style = MaterialTheme.typography.labelSmall,
                    color = sign(it, c) ?: c.inkSoft,
                )
            }
        }
    }
}

/* ---------- dividends ---------- */

@Composable
private fun Dividends(months: List<Month>, currency: String) {
    val c = LocalReiad.current
    val top = months.maxOf { it.amount }.coerceAtLeast(0.01)
    Pane {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Dividends, last twelve months",
                style = MaterialTheme.typography.titleSmall,
                color = c.ink,
                modifier = Modifier.weight(1f),
            )
            Text(
                money(dividendTotal(months), currency),
                style = MaterialTheme.typography.labelLarge,
                color = c.accent,
            )
        }
        Spacer(Modifier.height(Gap.s5))
        Row(
            Modifier.fillMaxWidth().height(90.dp),
            horizontalArrangement = Arrangement.spacedBy(Gap.s2),
            verticalAlignment = Alignment.Bottom,
        ) {
            for (m in months) {
                Column(
                    Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    /* A month that paid nothing still gets a
                       sliver, so twelve columns read as twelve
                       months rather than as four. */
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .weight(
                                (if (m.amount > 0) (m.amount / top).toFloat().coerceAtLeast(0.05f)
                                else 0.015f),
                            )
                            .clip(RoundedCornerShape(Corner.xs))
                            .background(if (m.amount > 0) c.accent else c.hairline),
                    )
                    Spacer(Modifier.height(Gap.s2))
                    Text(
                        m.key.takeLast(2),
                        style = MaterialTheme.typography.labelSmall,
                        color = c.inkSoft,
                    )
                }
            }
        }
    }
}

/* ---------- the offer, and the refusals ---------- */

@Composable
private fun Connect(standing: Standing?, trouble: Trouble?, onConnect: () -> Unit) {
    val c = LocalReiad.current
    Pane {
        Text(
            "Your own account",
            style = MaterialTheme.typography.titleSmall,
            color = c.ink,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(Gap.s3))
        val says = when {
            standing == null || trouble?.reason == "sign-in-required" ->
                "Sign in and connect a Trading 212 key to see this dashboard over your own " +
                    "account instead."
            !standing.sealing ->
                "This site cannot store a key at the moment, so connecting one is off. " +
                    "The portfolio above is still live."
            standing.savedLabel != null ->
                "A key is saved (${standing.savedLabel}) and your account could not be " +
                    "read just now. It is usually the broker's rate limit; try again in a " +
                    "minute."
            else ->
                "Connect a Trading 212 key on the site and this becomes your dashboard: " +
                    "your holdings, your dividends, your numbers. The key is sealed before " +
                    "it is stored and this app never sees it."
        }
        Text(says, style = MaterialTheme.typography.bodyMedium, color = c.inkSoft)

        Spacer(Modifier.height(Gap.s5))
        /* The key is entered on the SITE, not here, and that is
           deliberate: a broker credential is the one thing worth
           making somebody type where they can see the address bar
           and the padlock. */
        Control(Modifier.clickable(onClick = onConnect)) {
            Text(
                "Open on the site",
                style = MaterialTheme.typography.labelLarge,
                color = c.ink,
            )
        }
    }
}

@Composable
private fun Sorry(trouble: Trouble?) {
    val c = LocalReiad.current
    Plate {
        Text(
            when (trouble?.reason) {
                "not-configured" ->
                    "The site has not connected a portfolio yet."
                "too-many" ->
                    "The broker's rate limit was reached. It clears in about a minute."
                "offline" ->
                    "No connection. This is the one page here that cannot be read offline: " +
                        "a live portfolio that is not live is a screenshot."
                else -> trouble?.message ?: "That did not load."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = c.inkSoft,
        )
    }
}

/* ---------- printing ---------- */

/** The site's own `MONEY`: no decimals past a thousand, because
    the pennies on a five-figure balance are noise. */
internal fun money(v: Double, currency: String): String {
    if (!v.isFinite()) return "–"
    val symbol = when (currency.uppercase()) {
        "GBP" -> "£"
        "USD" -> "$"
        "EUR" -> "€"
        "BDT" -> "৳"
        else -> ""
    }
    val digits = if (abs(v) >= 1000) 0 else 2
    val body = plain(abs(v), digits)
    val sign = if (v < 0) "-" else ""
    return if (symbol.isNotEmpty()) "$sign$symbol$body"
    else "$sign$body ${currency.uppercase()}".trim()
}

/** A percentage that carries its sign, because a return without
    one is a number a reader has to look twice at. */
internal fun signed(v: Double): String {
    if (!v.isFinite()) return "–"
    val digits = if (abs(v) >= 100) 0 else 1
    return "${if (v > 0) "+" else ""}${plain(v, digits)}%"
}

/** A share count, which is often fractional and often not. */
internal fun qty(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString()
    else BigDecimal.valueOf(v).setScale(4, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()

/** Grouped in threes with a fixed number of decimals. */
internal fun plain(v: Double, digits: Int): String {
    val rounded = BigDecimal.valueOf(v).setScale(digits, RoundingMode.HALF_UP)
    val text = rounded.abs().toPlainString()
    val whole = text.substringBefore('.')
    val frac = text.substringAfter('.', "")
    val grouped = whole.reversed().chunked(3).joinToString(",").reversed()
    val body = if (frac.isEmpty()) grouped else "$grouped.$frac"
    return if (v < 0) "-$body" else body
}

/** Nought is neither up nor down, and colouring it either way is
    a claim the number does not make. */
private fun sign(v: Double, c: ReiadColours): Color? =
    if (v > 0) c.accent else if (v < 0) c.danger else null

/** `22 Aug, 18:04`, or nothing at all rather than a wrong date. */
private fun whenText(iso: String): String {
    if (iso.length < 16) return ""
    val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun",
        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    val month = iso.substring(5, 7).toIntOrNull() ?: return ""
    if (month !in 1..12) return ""
    val day = iso.substring(8, 10).trimStart('0')
    return "$day ${months[month - 1]}, ${iso.substring(11, 16)}"
}
