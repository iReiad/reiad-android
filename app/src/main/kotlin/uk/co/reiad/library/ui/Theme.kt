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
import uk.co.reiad.library.core.Kind
import uk.co.reiad.library.core.Veil
import uk.co.reiad.library.core.rimTint
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

/** A veil is a colour and how much of it there is, so it becomes
    a Compose colour with that alpha on it. */
private fun Veil.compose(): Color = colour.compose().copy(alpha = alpha.toFloat())

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

    /* ---- the material's own three ----

       Achromatic, and see `Veil` in core for why that is a rule
       rather than a simplification. `paneTop` is a hairline and
       `glassFace` is the material's highlight: they are separate
       tokens because reading the reflection out of the hairline
       is what made the site's whole material invisible in dark
       mode while looking correct in light. */
    val paneTop: Color,
    val glassFace: Color,
    val glassUnder: Color,

    /** The texture's ink, which IS coloured by the page: a weave
        is in the paper rather than reflected off it. */
    val texInk: Color,
    val texA: Float,
    val texB: Float,

    /** The accent, unpremultiplied, for the parts that mix it in
        themselves: the cut edge disperses by `polish`, which is a
        per-kind number, so the mix cannot be done once here. */
    val accentRaw: Oklch,
    val glassFaceVeil: Veil,
)

/** The colour a cut edge of this kind comes back at. */
fun ReiadColours.rimFace(kind: Kind): Color {
    val veil = glassFaceVeil.tinted(accentRaw, rimTint(kind))
    return veil.colour.compose().copy(alpha = veil.alpha.toFloat())
}

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
        paneTop = s.paneTop.compose(),
        glassFace = s.glassFace.compose(),
        glassUnder = s.glassUnder.compose(),
        texInk = s.texInk.compose(),
        texA = s.texA.toFloat(),
        texB = s.texB.toFloat(),
        accentRaw = s.accent,
        glassFaceVeil = s.glassFace,
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
   multiplied out here rather than re-guessed, and the faces are
   the site's own: `Faces` bundles the six families `FONTS` in
   `shared/look.ts` asks a browser for.

   The site enforces that nothing below the top of the scale is a
   literal size, which is why `step()` exists and why the three
   headline sizes are the only figures written out: they are the
   top of the ladder, and on a handset they are smaller than the
   desktop's because a 30px heading on a 360dp screen is two
   words a line. */

private const val BODY_PX = 17.0

private fun step(n: Int) = (TypeScale.step(n) * BODY_PX).sp

private val Serif = Faces.serif
private val Sans = Faces.sans
private val Mono = Faces.mono

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

/* ---------- and the same ladder in Bangla ----------

   Two things change and both are the site's. The FACE changes,
   because Bangla is the learning language and a reader should not
   meet it set in a face chosen for English. And the LEADING goes
   from 1.65 to 1.9, because Bengali conjuncts and the matra stack
   further above and below the line than Latin does.

   Applied where a string is Bangla rather than globally: a mixed
   line should not go loose, and the site says the same by scoping
   its rule to `[lang="bn"]`. */

/** Bangla prose. */
val BanglaBody: TextStyle
    @Composable @ReadOnlyComposable
    get() = MaterialTheme.typography.bodyLarge.copy(
        fontFamily = Faces.bengali,
        lineHeight = (BODY_PX * 1.9).sp,
    )

/** A Bangla heading, which takes the Bengali SERIF: the site's
    `.bn-h` rule and `html[lang="bn"] :is(h1,h2,h3)`. */
val BanglaHeading: TextStyle
    @Composable @ReadOnlyComposable
    get() = MaterialTheme.typography.headlineSmall.copy(
        fontFamily = Faces.bengaliSerif,
        lineHeight = MaterialTheme.typography.headlineSmall.fontSize * 1.5f,
    )

/** A Bangla title inside a card or a row. */
val BanglaTitle: TextStyle
    @Composable @ReadOnlyComposable
    get() = MaterialTheme.typography.titleMedium.copy(
        fontFamily = Faces.bengaliSerif,
        lineHeight = MaterialTheme.typography.titleMedium.fontSize * 1.45f,
    )

/** Whether a string is Bangla, which decides which of the two
    ladders above a line is set in.

    Asked of the STRING rather than of a language flag, because
    this site's rows mix: a lesson's title is Bangla and its stage
    label beside it is English, and both are strings in the same
    object. The Bengali block is U+0980 to U+09FF and one
    character in it is enough, because a line with any Bangla in
    it is a line that needs the taller leading. */
fun isBangla(text: String): Boolean = text.any { it.code in 0x0980..0x09FF }

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
