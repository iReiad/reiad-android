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
import uk.co.reiad.library.core.Sheer
import uk.co.reiad.library.core.rimTint
import uk.co.reiad.library.core.Oklch
import uk.co.reiad.library.core.Radius
import uk.co.reiad.library.core.Space
import uk.co.reiad.library.core.Surfaces
import uk.co.reiad.library.core.TypeScale
import androidx.compose.runtime.staticCompositionLocalOf
import uk.co.reiad.library.core.Measure
import uk.co.reiad.library.core.Scale
import androidx.compose.ui.unit.isSpecified

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

/** A sheer is a colour and how little of it there is, so it
    becomes a Compose colour with that alpha on it. */
private fun Sheer.compose(): Color = colour.compose().copy(alpha = alpha.toFloat())

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
    /** The site's `--gold`, which is what a warning is drawn in
        there. A third tone rather than a paler danger, because
        "worth knowing" and "this is wrong" are different claims
        and the stock check makes both on the same list. */
    val gold: Color,
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
    val glassFaceSheer: Sheer,
)

/** The colour a cut edge of this kind comes back at. */
fun ReiadColours.rimFace(kind: Kind): Color {
    val sheer = glassFaceSheer.tinted(accentRaw, rimTint(kind))
    return sheer.colour.compose().copy(alpha = sheer.alpha.toFloat())
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
        gold = (if (dark) Accents.GOLD.dark else Accents.GOLD.light).compose(),
        isDark = dark,
        paneTop = s.paneTop.compose(),
        glassFace = s.glassFace.compose(),
        glassUnder = s.glassUnder.compose(),
        texInk = s.texInk.compose(),
        texA = s.texA.toFloat(),
        texB = s.texB.toFloat(),
        accentRaw = s.accent,
        glassFaceSheer = s.glassFace,
    )
}

val LocalReiad = compositionLocalOf { coloursOf(Accents.GREEN, dark = false) }

/** The accent a token names.

    The site sends an accent as the stylesheet's own spelling,
    `var(--blue)`, which is the one place that mapping lives. This
    resolves it through the table computed from the site's OKLCH
    rather than through a colour typed here, so a retuned accent
    reaches the app on a rebuild rather than on somebody
    remembering there is a second copy. Anything unrecognised is
    the site's own green, which is what a destination with no
    accent of its own gets there too. */
fun accentOf(token: String?): Accent =
    token?.let { Accents.byToken(it) } ?: Accents.GREEN

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

/** Whether a string carries Arabic, which the Qur'an school's
    every lesson does. Asked the same way as `isBangla` and for
    the same reason: a pair row's lead is one string, and a lead
    with harakat needs more line than a Latin one or the fatha
    over the first letter is shaved by the row above. */
fun isArabic(text: String): Boolean = text.any {
    it.code in 0x0600..0x06FF || it.code in 0xFB50..0xFDFF || it.code in 0xFE70..0xFEFF
}

/* The two above, chosen per string. A screen whose words arrive
   from `/api/tools` in whichever language the reader picked
   cannot decide this at the call site the way a hard-coded label
   can, so it asks the string. */

@Composable
@ReadOnlyComposable
fun bodyStyle(text: String): TextStyle =
    if (isBangla(text)) BanglaBody else MaterialTheme.typography.bodyLarge

@Composable
@ReadOnlyComposable
fun headingStyle(text: String): TextStyle =
    if (isBangla(text)) BanglaHeading else MaterialTheme.typography.headlineSmall

@Composable
fun ReiadTheme(
    accent: Accent = Accents.GREEN,
    dark: Boolean = isSystemInDarkTheme(),
    /* How wide a column of prose gets. Read by the reading
       screens through `LocalMeasure`, which is a composition
       local rather than a parameter threaded through nine
       composables for the same reason the palette is. */
    measure: Measure = Measure.NORMAL,
    /* The reader's type size, which is a preference this app
       stored, synced and IGNORED. Every screen goes through this
       one function, so scaling here is the whole of applying it:
       nothing else has to know, and nothing else can forget. */
    scale: Scale = Scale.NORMAL,
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

    /* The whole ramp, not a font-size on the body.

       The site scales `--t-0` and every step follows, so a
       heading grows with the prose under it. Scaling one style
       would make Comfortable a page of normal headings over
       larger paragraphs, which is not larger type, it is a
       different design. */
    val type = remember(scale) {
        if (scale == Scale.NORMAL) ReiadType else ReiadType.scaledBy(scale.factor)
    }

    CompositionLocalProvider(
        LocalReiad provides colours,
        LocalScale provides scale,
        LocalMeasure provides measure,
    ) {
        MaterialTheme(colorScheme = scheme, typography = type, content = content)
    }
}

/** What the reader's three glass answers come to on a handset.

    `glass`, `blur` and `veil` have been in `reader-prefs` since
    the sheet was written, they sync with the account, and until
    this NOTHING IN THE APP READ THEM: the bars' frost was a
    hard-coded 22dp over a hard-coded tint. That is the exact
    stored-and-ignored failure `PrefsReachTest` was written
    against, one shelf along.

    The numbers are the site's own arithmetic (`Prefs.blurRadius`
    and `veilAlpha`), scaled so that frost-at-normal lands on the
    22dp the bars shipped with: a reader who has never opened the
    sheet sees exactly what they saw yesterday, and every step
    away from normal now actually moves the glass. `plain` comes
    back with no blur and a solid veil, because plain is not
    glass. */
data class GlassLook(val blur: androidx.compose.ui.unit.Dp, val veil: Float)

fun glassLookOf(prefs: uk.co.reiad.library.core.Prefs): GlassLook = GlassLook(
    /* Half again as deep as the bars first shipped with, because
       the paint over the blur went from three tenths to one:
       glass reads as glass when the BLUR carries it and the tint
       only colours it, and a thin tint over a shallow blur is a
       dirty window. */
    blur = (prefs.blurRadius() * 32.0 / 14.0).dp,
    veil = if (prefs.finish == uk.co.reiad.library.core.Finish.PLAIN) 1f
        else (prefs.veilAlpha() * 0.52 / 0.72).toFloat(),
)

val LocalGlassLook = staticCompositionLocalOf { GlassLook(32.dp, 0.52f) }

/** Which type size is on, for the few things that size
    themselves rather than reading a `TextStyle`: the icon beside
    a heading, the width of a column of prose. */
val LocalScale = staticCompositionLocalOf { Scale.NORMAL }

/** How wide a column of prose gets, in characters.

    Only bites where there is room: a handset in portrait is
    about 45 characters wide at the site's body size, which is
    already narrower than the narrowest setting. It is a tablet,
    a landscape phone and an unfolded foldable that this is for,
    and on those the difference between 56 and 78 is the
    difference between reading and scanning. */
val LocalMeasure = staticCompositionLocalOf { Measure.NORMAL }

/**
 * Every style in the ramp, multiplied.
 *
 * Written out rather than mapped over a list of names, because
 * `Typography` has no iteration and a `copy` that forgot one
 * style would leave a single line of the app at the old size,
 * which is exactly the sort of thing nobody sees.
 */
private fun Typography.scaledBy(k: Float): Typography {
    /* UNSPECIFIED IS NOT ZERO and it is not multipliable.

       `TextUnit.Unspecified` throws on any arithmetic, and three
       styles in this ramp set a size and leave the leading to
       Compose. Scaling them crashed the whole app on any size but
       Normal, which is to say: for every reader who used the
       preference this change exists to make work. Caught by a
       snapshot, because a crash in a theme is a crash before
       anything is drawn and there is nothing else to see. */
    fun TextStyle.up() = copy(
        fontSize = if (fontSize.isSpecified) fontSize * k else fontSize,
        lineHeight = if (lineHeight.isSpecified) lineHeight * k else lineHeight,
    )
    return copy(
        displayLarge = displayLarge.up(), displayMedium = displayMedium.up(),
        displaySmall = displaySmall.up(),
        headlineLarge = headlineLarge.up(), headlineMedium = headlineMedium.up(),
        headlineSmall = headlineSmall.up(),
        titleLarge = titleLarge.up(), titleMedium = titleMedium.up(),
        titleSmall = titleSmall.up(),
        bodyLarge = bodyLarge.up(), bodyMedium = bodyMedium.up(), bodySmall = bodySmall.up(),
        labelLarge = labelLarge.up(), labelMedium = labelMedium.up(),
        labelSmall = labelSmall.up(),
    )
}
