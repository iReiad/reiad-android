# reiad-android

The native Android app for [reiad.co.uk](https://reiad.co.uk).
Kotlin and Jetpack Compose, over the site's own public API.

`ANDROID.md` in the website repository is the plan: what the app
consumes, what carries over unchanged, and the order it gets
built in. This file is how to run what is here.

## Run the tests

```sh
./gradlew :core:test
```

No Android SDK needed for that, deliberately. See below.

## Build the app

```sh
./gradlew :app:assembleDebug
```

The APK lands at `app/build/outputs/apk/debug/app-debug.apk`.
Android Studio supplies the SDK; from a terminal put its path in
`local.properties` as `sdk.dir=...`, which is gitignored because
it is a fact about one machine.

**Use the wrapper, not whatever `gradle` is on the path.** The
version is pinned in `gradle/wrapper/gradle-wrapper.properties`
and the jar is committed, which is what makes a build here and a
build in CI the same build.

## CI

`.github/workflows/checks.yml`, and it runs both of the above on
every push. The APK is uploaded off every run, because a commit
somebody can install is worth more than a green tick saying it
would have compiled.

It runs on `push` as well as on `pull_request`, which is the
website repository's own lesson: `pull_request` quietly stopped
firing there for opens and pushes alike, and two pull requests sat
with green ticks and no test run at all.

## Layout

| | |
| --- | --- |
| `core/` | plain Kotlin. API models, the body parser, the storage keys, the sync arithmetic, the palette, the material |
| `core/src/test/resources/fixtures/` | real answers from the live API, captured rather than written |
| `app/` | Compose. The theme, the material, the deck, the body renderer, the four schools |
| `app/src/main/res/font/` | the site's six faces, bundled. See `docs/FONTS.md` |

## What the app does today

Four schools, end to end: the ladders off `/api/schools/<school>`,
a lesson opened and rendered through the parser, and a tick that
survives a restart. Deliberately a whole vertical rather than a
prettier ladder, because a ladder that renders over a lesson that
will not open is the exact shape of thing this project keeps
promising not to ship.

Opening is not finishing. The money school's tick is a button.
The other three schools mark a lesson on opening, which is their
own semantics and arrives with them.

**And it is made of the site's own material.** Six kinds of glass,
four numbers each, everything visible derived: the lit cut edge at
the bottom, the soft rim all the way round, the dispersion that
tints it toward the section's accent, the weave running continuous
across the page, the specular raked at 105 degrees, and the glow
that comes up in 190ms under a finger and takes 820ms to go out. A
groove is the inverse of the other five. `core/Material.kt` is the
arithmetic and `MaterialTest` is 21 assertions that it is still a
system rather than six rows of plausible numbers.

The faces are the site's six, bundled: Spectral, IBM Plex Sans and
Mono, Noto Sans and Serif Bengali, Caveat. `docs/FONTS.md` says
why bundled rather than downloaded, and what nearly shipped.

**And there is a shell around it now**, read from the site's one
nav table rather than from a copy: a bottom bar on a phone, a rail
on a tablet or an unfolded foldable, and a drawer holding the
whole menu. The audience switch reorders it and hides nothing.
Theme, glass finish, blur and veil are settings, stored under the
site's own `reader-prefs` key so a change here reaches the site.
Search runs against the manifest, so it works with no network.
Back is predictive: a sheet follows the gesture and comes back if
you change your mind.

Every row in every group opens. A school opens here; anything this
app cannot render yet opens on the site in a Custom Tab, which is
the reader's own browser and so keeps their session. The card says
which it will be before you press it.

**And the writing is here now.** Insights, Cooking and Travel off
`/api/articles`, with topic chips whose counts are counted from
what is on screen rather than remembered, and a chip that would
filter nothing is not offered. A piece renders through the same
parser the lessons use, with photos: Coil, honouring the site's
own crop classes, so `frame-square` and `focus-top` mean here what
they mean there and what the share card means by them.

**Read aloud carries on with the screen off**, which is the one
thing the site's own read-aloud cannot do: a browser stops
`speechSynthesis` the moment the tab is hidden. A foreground
service keeps the voice alive and puts a Stop in the shade and on
the lock screen. What gets read is `speakable()` in core, tested:
prose yes, tables and captions and key figures no, a block the
parser could not place yes, because losing a paragraph to an
unknown wrapper is worse than reading it in the wrong tone.

**A school is finished, less its book.** A progress ring that
counts lessons and nothing else, a resume card pointing at where
the reader last WAS rather than where they got to, and a state
line per stage that says what a stage reads best after without
ever locking it. Checkpoints inside a lesson body are real: a
`.checklist` in a school lesson becomes ticks filed
`<lesson id>#<n>`, numbered across the whole lesson the way the
site numbers them, and counted towards no ladder.

Not yet: the account and sync, the practice books (see
`ROADMAP.md`, they need a website change first), the tools. `ROADMAP.md` is the
twelve blocks that finish it, with a scorecard that says where it
stands rather than how it feels.

## Why two modules

`core` imports nothing from Android, so it compiles and tests on
any JVM. That matters because the two pieces most likely to be
wrong, the sync arithmetic and the body parser, are both in it:
they can be proved anywhere, by anyone, in about thirty seconds,
without a handset or an emulator.

## The rules this app inherits

They are the site's, and they are not negotiable here:

- **A storage key is a fact.** `learn-read`, `quran-done`,
  `english-day`. These strings are in real accounts. Renaming one
  does not move somebody's ticks, it loses them. `StorageKeyTest`
  names all twenty-one.
- **The account is the record, the device is a mirror.** Signing
  in adopts the account's rows including its deletions; nothing
  local is uploaded. Signing out takes the mirror off.
- **The ladder is the server's, the ticks are the device's.**
- **Opening is not finishing**, and each school means it
  differently: the money school's tick is a button, the other
  three mark a lesson on opening.
- **A checkpoint is not a lesson** and counts towards no ladder.
- **A preference key is a fact too.** `reader-prefs`, `theme`,
  `tool-lang`, `audience`, `track`. `PrefsTest` names all five.
- **The menu is said once**, on the site, and this app has no copy
  of it. `ManifestSurfaceTest` is what holds that: every field
  `/api/site` sends has to land somewhere here or be named with a
  reason, and it walks the whole tree rather than the top of it.

## The fixtures are the point

Every file under `fixtures/` came from the live API. The site's
own house rules put it best: a fixture kinder than the thing it
stands in for is not a test.

That earned its keep on the first run. `Lesson.written` was
declared `Int` here, because the row behind it is a SQL CASE
returning 0 or 1, and the API answers with a real `true`. Eight
tests went red at once on a fixture the site had actually sent.
A hand-written fixture would have agreed with the mistake.

`lesson-share.json` is in there for a second reason: it carries a
`<b>`, a tag the server's own allowlist does not include. Stored
prose predates the sanitiser that would have renamed it, and the
web renders it because a browser is forgiving. Nothing here is,
so the parser treats the allowlist as a floor: it maps the same
synonyms the site's editor maps, and anything still unknown keeps
its words and reports itself.

## Refreshing the fixtures

```sh
cd core/src/test/resources/fixtures
curl -sS -o site.json          https://reiad.co.uk/api/site
curl -sS -o money.json         https://reiad.co.uk/api/schools/money
curl -sS -o lesson-share.json  https://reiad.co.uk/api/schools/money/basics-1/share
```

Everything the app reads is public, so this needs no credential.
A refresh that turns a test red is the app finding out the site
changed, which is what they are for.
