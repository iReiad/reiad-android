package uk.co.reiad.library.ui

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import uk.co.reiad.library.R

/* ============================================================
   The site's six faces, bundled.

   `FONTS` in `shared/look.ts` is the site's own request and these
   are the same families at the same weights: Spectral 400/500/600
   for headings and quotes, IBM Plex Sans 400/500/600 for prose,
   IBM Plex Mono 400/500 for the small uppercase labels, Noto Sans
   and Noto Serif Bengali for the learning language, and Caveat
   500 for the one hand-written thing.

   ---- bundled rather than downloaded, and why that changed ----

   The plan said Downloadable Fonts: one copy per device, shared
   between apps, nothing in the APK. Two things sank it.

   The provider authenticates against a certificate array holding
   the signing hashes of Play Services, and it is NOT in
   `androidx.core`: an app declares it. Writing base64 nobody has
   verified is how you ship a font that silently never loads, on
   a page that renders perfectly in the fallback face.

   And a downloaded face needs Play Services present and a
   network on first run. This is an APK somebody sideloads, onto
   a handset that may have neither, to read a site whose whole
   argument is that it works with no network.

   1.3MB is what that costs, and it buys every face working on
   every device, offline, from the first launch.

   `res/font/README.md` has the rest, including the one that
   nearly shipped: the Bengali files are 20KB and contain no
   Bengali unless the subset is named.
   ============================================================ */

/** The six faces this site sets text in.

    A family per role rather than per file, because the role is
    what a caller means: `Faces.serif` is what a heading is set
    in, and which of the three weights answers is Compose's job. */
object Faces {

    /** Headings, the lead paragraph, a pulled quote. */
    val serif = FontFamily(
        Font(R.font.spectral_400, FontWeight.Normal),
        Font(R.font.spectral_500, FontWeight.Medium),
        Font(R.font.spectral_600, FontWeight.SemiBold),
    )

    /** The prose face. */
    val sans = FontFamily(
        Font(R.font.plex_sans_400, FontWeight.Normal),
        Font(R.font.plex_sans_500, FontWeight.Medium),
        Font(R.font.plex_sans_600, FontWeight.SemiBold),
    )

    /** The small uppercase labels, and any figure a reader is
        meant to line up under another figure. */
    val mono = FontFamily(
        Font(R.font.plex_mono_400, FontWeight.Normal),
        Font(R.font.plex_mono_500, FontWeight.Medium),
    )

    /** Bangla is the site's learning language, so it gets both a
        serif and a sans exactly as the Latin side does. A reader
        who should never have to read English to find out
        something exists in their own language should not have to
        read it in a face chosen for English either. */
    val bengali = FontFamily(
        Font(R.font.noto_bengali_400, FontWeight.Normal),
        Font(R.font.noto_bengali_500, FontWeight.Medium),
    )

    val bengaliSerif = FontFamily(
        Font(R.font.noto_bengali_serif_500, FontWeight.Medium),
        Font(R.font.noto_bengali_serif_600, FontWeight.SemiBold),
    )

    /** One hand, for the one thing that is written rather than
        set. */
    val hand = FontFamily(Font(R.font.caveat_500, FontWeight.Medium))
}
