# The faces, bundled

Thirteen files in `app/src/main/res/font/`, 1.3MB, and every one
of them is what
`FONTS` in the website's `shared/look.ts` asks a browser for:
Spectral 400/500/600, IBM Plex Sans 400/500/600, IBM Plex Mono
400/500, Noto Sans Bengali 400/500, Noto Serif Bengali 500/600,
Caveat 500.

## Why these are here rather than downloaded

The plan said Downloadable Fonts, which fetches a face through
Play Services and shares one copy between every app on the
device. Two things sank it, and the second is the one that
decided it.

The provider needs a certificate array to authenticate against,
`com_google_android_gms_fonts_certs`, and it is not in
`androidx.core`: it is a resource an app declares itself, holding
the signing hashes of Play Services. Writing base64 nobody has
verified is how you get a font that silently never loads.

And a downloaded face needs Play Services present and a network
on first run. This is an APK somebody sideloads, onto a handset
that may have neither, to read a site whose whole point is that
it works offline.

## Getting them again

The v1 CSS API serves TrueType where css2 serves woff2, which
Android cannot read, so an old user agent is what asks for the
right thing:

```sh
curl -sS "https://fonts.googleapis.com/css?family=Spectral:400" \
  -H "User-Agent: Mozilla/4.0"
```

**Name the subset for the Bengali two.** Without
`&subset=bengali,latin` the API answers with a Latin-only cut of
Noto Sans Bengali: 20KB, valid, installs, renders, and contains
no Bengali at all. Bangla is this site's learning language, so a
Bengali font with no Bengali in it is the whole feature missing
where nothing fails. The four files here were checked for অ, ৎ
and ৫ in the cmap after downloading, not before.

## Licence

All six families are under the SIL Open Font License 1.1, which
permits bundling in an application. `app/src/main/assets/OFL.txt`
is the licence text and the app shows it.
