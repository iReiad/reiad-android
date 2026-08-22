package uk.co.reiad.library.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import uk.co.reiad.library.core.Accent
import uk.co.reiad.library.core.Accents
import uk.co.reiad.library.core.Oklch
import uk.co.reiad.library.core.Radius
import uk.co.reiad.library.core.Space
import uk.co.reiad.library.core.Surfaces
import uk.co.reiad.library.core.TypeScale

/* ============================================================
   The site's design language, in Compose.

   Nothing here is a colour somebody typed. `Surfaces` computes
   the whole palette from the site's OKLCH tokens, and this file
   turns the result into a theme. That is the point: retune an
   accent on the site and the app follows on a rebuild, rather
   than on somebody remembering there is a second copy.

   **The page wears its section's colour.** The site sets one
   `--accent` on `<html>` and every component reads it. Here that
   is `LocalReiad`, and a screen that belongs to a school provides
   its own before drawing anything.

   Material 3 is underneath because its components already know
   about touch targets, ripples and accessibility services, and
   throwing that away to match a stylesheet would be trading real
   behaviour for a look. What is replaced is the palette, the
   corners, the type and the spacing, which is the whole of what
   makes the site look like itself.
   ============================================================ */

private fun Oklch.compose(): Color = Color(toArgb())

/** Everything a screen needs that Material 3 has no slot for.
    The site's own names are kept: a reader of `site.css` should
    recognise every one of these. */
data class ReiadColours(
    val paper: Color,
    val paperSunk: Color,
    val panel: Color,
    val hairline: Color,
    val ink: Color,
    val inkSoft: Color,
    val accent: Color,
    val accentSoft: Color,
    val accentLine: Color,
    val danger: Color,
    val isDark: Boolean,
)

fun coloursOf(accent: Accent, dark: Boolean): ReiadColours {
    val s = Surfaces.of(accent, dark)
    return ReiadColours(
        paper = s.paper.compose(),
        paperSunk = s.paperSunk.compose(),
        panel = s.panel.compose(),
        hairline = s.hairline.compose(),
        ink = s.ink.compose(),
        inkSoft = s.inkSoft.compose(),
        accent = s.accent.compose(),
        accentSoft = s.accentSoft.compose(),
        accentLine = s.accentLine.compose(),
        danger = (if (dark) Accents.DANGER.dark else Accents.DANGER.light).compose(),
        isDark = dark,
    )
}

val LocalReiad = compositionLocalOf { coloursOf(Accents.GREEN, dark = false) }

/** The corner ladder, never a number. A row and a control are
    pills, a card is `card`, a field is `field`. */
object Corner {
    val xs = Radius.XS.dp
    val field = Radius.SM.dp
    val card = Radius.CARD.dp
    val lg = Radius.LG.dp
    val pill = Radius.PILL.dp
}

/** The 2px ramp, by the site's own step numbers, so `Gap.s5` here
    and `--s-5` there are the same ten pixels. */
object Gap {
    val s1 = Space.step(1).dp
    val s2 = Space.step(2).dp
    val s3 = Space.step(3).dp
    val s4 = Space.step(4).dp
    val s5 = Space.step(5).dp
    val s6 = Space.step(6).dp
    val s7 = Space.step(7).dp
    val s8 = Space.step(8).dp
    val s9 = Space.step(9).dp
    val s10 = Space.step(10).dp
    val s11 = Space.step(11).dp

    /** The one height for anything pressed. */
    val tap = Space.TAP.dp
    val tapSmall = Space.TAP_SMALL.dp
}

/* ---------- type ----------

   The site's nine steps are relative to a 17px body, so they are
   multiplied out here rather than re-guessed. The faces are the
   site's intent stated in what a handset has: a serif for
   headings, the system sans for text, a monospace for the small
   uppercase labels. Bundling Spectral and the Noto Bengali faces
   is a later change and a large one, so this says plainly what it
   is standing in for rather than pretending to be finished. */

private const val BODY_PX = 17.0

private fun step(n: Int) = (TypeScale.step(n) * BODY_PX).sp

private val Serif = FontFamily.Serif
private val Sans = FontFamily.SansSerif
private val Mono = FontFamily.Monospace

val ReiadType = Typography(
    displaySmall = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Medium, fontSize = 30.sp, lineHeight = 36.sp),
    headlineMedium = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Medium, fontSize = 24.sp, lineHeight = 29.sp),
    headlineSmall = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Medium, fontSize = 20.sp, lineHeight = 25.sp),
    titleMedium = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Medium, fontSize = step(7), lineHeight = step(7) * 1.25f),
    titleSmall = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Medium, fontSize = step(6), lineHeight = step(6) * 1.3f),
    bodyLarge = TextStyle(fontFamily = Sans, fontSize = BODY_PX.sp, lineHeight = (BODY_PX * 1.65).sp),
    bodyMedium = TextStyle(fontFamily = Sans, fontSize = step(5), lineHeight = step(5) * 1.6f),
    bodySmall = TextStyle(fontFamily = Sans, fontSize = step(4), lineHeight = step(4) * 1.5f),
    labelLarge = TextStyle(fontFamily = Mono, fontSize = step(3), letterSpacing = 0.06.em, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontFamily = Mono, fontSize = step(2), letterSpacing = 0.05.em),
    labelSmall = TextStyle(fontFamily = Mono, fontSize = step(1), letterSpacing = 0.1.em),
)

/** Bangla wants looser leading than Latin: the site sets 1.9
    against 1.65. Applied where a string is Bangla rather than
    globally, because a mixed line should not go loose. */
val BanglaBody: TextStyle
    @Composable @ReadOnlyComposable
    get() = MaterialTheme.typography.bodyLarge.copy(lineHeight = (BODY_PX * 1.9).sp)

@Composable
fun ReiadTheme(
    accent: Accent = Accents.GREEN,
    dark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colours = remember(accent, dark) { coloursOf(accent, dark) }

    /* Material's own scheme is filled from the same computed
       palette, so a component this app has not restyled still
       lands on the site's colours rather than on purple. */
    val scheme = if (dark) {
        darkColorScheme(
            primary = colours.accent,
            onPrimary = colours.paper,
            background = colours.paper,
            onBackground = colours.ink,
            surface = colours.panel,
            onSurface = colours.ink,
            surfaceVariant = colours.paperSunk,
            onSurfaceVariant = colours.inkSoft,
            outline = colours.hairline,
            error = colours.danger,
        )
    } else {
        lightColorScheme(
            primary = colours.accent,
            onPrimary = colours.paper,
            background = colours.paper,
            onBackground = colours.ink,
            surface = colours.panel,
            onSurface = colours.ink,
            surfaceVariant = colours.paperSunk,
            onSurfaceVariant = colours.inkSoft,
            outline = colours.hairline,
            error = colours.danger,
        )
    }

    CompositionLocalProvider(LocalReiad provides colours) {
        MaterialTheme(colorScheme = scheme, typography = ReiadType, content = content)
    }
}
