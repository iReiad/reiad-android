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
| `core/src/test/resources/fixtures/` | real answers from the live API, captured rather than written. Six are not from the API: `stock.json`, `calculators.json`, `routine.json` and `diet.json` are written by the website's `scripts/export-*-fixtures.ts` out of the models themselves, which is what locks each Kotlin port to the site's arithmetic number for number; `courses.json` and `course-first.json` are the site's own course emitters run over an invented catalogue, and `CoursesTest` says at length why they cannot be captured |
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

**And it moves like a made thing.** Screens change with a breath
rather than a cut, the sheets rise over the page and stop below
the clock instead of behind it, every three-way choice is one
thumb sliding in its groove, a meter pours to its number, and a
pressed surface gives two per cent under the finger on the
light's own curve. A state change (a tick, a latch, a segment)
hums through the vibrator, and plain navigation stays silent,
because a reading app that buzzes on every row is a reading app
somebody turns the vibrator off for, and then the ticks lose
their voice too. Reduced motion snaps all of it, as everywhere.

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

The parser reads like a browser reads. Pretty-printed source no
longer breaks a sentence where the author's editor wrapped the
line, a dissolved block leaves a line break at its edges rather
than gluing a German sentence to its Bangla meaning, and the
German school's own furniture (the `.muster` pattern box, the
`.satz` sentence pairs, the `.merke` rail) renders as itself
rather than as loose grey prose, off the site's own rules.
`lesson-satzbau.json` is the fixture that holds all three. A
table sits on its own piece of glass, its columns as wide as the
widest thing in them, and scrolls inside the glass rather than
off the screen. And a link inside a lesson or a piece OPENS:
every href goes through the same resolver a shared link uses, so
a glossary term lands on the term's own page in the app, and
anything the app cannot draw opens on the site.

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

**And the practice books are here**, which took a website change
first. The books are read on the server and never sent as data,
because every prompt has its answer beside it, so there was
nothing to consume: `iReiad/reiad-website#204` adds
`/api/book/<stage>`, which sends the days with the answers
stripped, and `/api/book/<stage>/key/<day>`, which sends one day's
key when the reader presses Show. Until that deploys, the book
screen says so and offers the site.

The day walker is the whole book on one line. What a learner types
saves on a debounce to `deutsch-schrift` or `english-write`, and
neither key ever leaves the device. A day is ticked under its own
school's shape, `stufe-1/tag-3` against `term-1/day-3`, because
both are in real browsers and the site's shared engine built the
German shape for both once.

**And there is an account.** Sign in with Google or an email link,
through a Custom Tab rather than a WebView, coming back on the
app's own scheme. Signing in ADOPTS: the account's rows are
written on to the phone and any synced key the account does not
have is removed, because a phone is not a copy of an account and
may have been lent to somebody for five minutes. The screen says
that before the button rather than after.

A tick made on a train lands later without the app being open,
which is the half a browser cannot have: WorkManager runs the
exchange when a network comes back.

The account holds more than ticks: a reading list with notes on
it, targets, and a year of days. None of those has a local copy
and that is deliberate, not an omission: progress has one because
four schools have read localStorage since before there were
accounts, and nothing here has that history or works signed out.

A target's bar is COMPUTED for a course and a habit, from what the
phone already holds, so it moves the moment a lesson is ticked.
Only a metric shows a stored number, because only a metric has one
the site could not work out. A fourth kind has to pass that test
or the bar would be a decoration.

The year of days has no flame, nothing red and nothing counting
down. A year of quiet marks says "here is what you did"; a streak
counter says "do not stop", and those are different things to say
to somebody learning a language in their spare time.

**And the admin's course section opens on the phone**, which took
replacing the hand-off rather than fixing it.

The gold card on `/skills` opened a Custom Tab, and for the one
reader the section belongs to, nothing was there. The address was
right: `/skills/courses` is what the site serves, and that had
already been the fix for `/courses`, a 404 reported as "that
button doesn't open anything". The right address did not help,
because the address was never the fault. **The site's reader
session is a bearer token in the browser's own storage and this
app's session is its own**, so a tab opened from here always
arrives with no credential, the shell asks the endpoint, the
endpoint says sign in, and the page says "you are either signed
out or this is not your library": to somebody signed in on the
phone, opening a card that is drawn only BECAUSE the server has
already confirmed the section is theirs. No browser hand-off can
carry a session it cannot be given.

So the app asks `/api/courses` itself, with the token it holds,
and draws all five views: the shelf of certificates, a programme,
a course with the lesson you have not done at the top of it, a
module summary, and a lesson. Video is Media3 over the site's own
thirty-minute single-file tickets, with `Range` forwarded, so it
seeks; captions arrive as WebVTT on a second pass of their own,
because a ticket names one file and that is what makes it safe to
put in a URL. A pass that runs out mid-sitting is renewed and
playback resumes at the same second. Readings and quizzes come
down sanitised by the Worker and render through the same body
parser the lessons use.

Ticks are the site's own `courses-read`, `courses-last` and
`courses-answers`, in the same wire format, so a lesson ticked
here is ticked on the laptop. **Nothing course-shaped is in this
binary and none of it is written to disk**: the catalogue is one
person's private Drive folder, it arrives only over the
authenticated API, and it lives in memory for as long as the
screen does. That is the one place this section is deliberately
worse than a school, which can be downloaded and read on a plane,
and it is the right trade.

Two rules came with it and are not negotiable. **No player event
ever marks a lesson**: ExoPlayer would happily report `ended`, and
reading that as "watched" would be guessing about somebody who
left a video running. The button is the signal. And **a quiz marks
nothing**: the export carries no answer key, so what is picked is
recorded and never scored, and the screen says so rather than
implying a mark it cannot compute.

Not yet: the tools. `ROADMAP.md` is the
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
- **A course tick names no programme.** `courses-read` holds
  `<course>/<module>/<lesson>`, and the address grew a certificate
  segment while the id deliberately did not: filing a course under
  a certificate is not the same as a reader not having watched it.
- **The catalogue is never in the binary and never on the disk.**
  It is one person's private Drive folder behind an admin check,
  so it arrives only over the authenticated API and lives in
  memory. `CoursesTest` fails if a real Drive id is ever committed
  to this public repository.
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
curl -sS -o foods.json         https://reiad.co.uk/api/foods
curl -sS -o money.json         https://reiad.co.uk/api/schools/money
curl -sS -o lesson-share.json  https://reiad.co.uk/api/schools/money/basics-1/share
```

Everything in that list is public, so it needs no credential.
A refresh that turns a test red is the app finding out the site
changed, which is what they are for.

**`courses.json` and `course-first.json` are not on that list and
must not be.** `/api/courses` answers 200 to one admin and 401 to
everybody else, and what it sends is a catalogue of somebody
else's material in a private Drive folder. This repository is
public, so a captured answer would publish, in a history nobody
can take it back out of, exactly the thing the endpoint exists to
keep unpublished. The two fixtures were produced by running the
website's own `listForBrowser()` and `forBrowser()` over a
catalogue of four made-up lessons: every KEY is the site's
emitter's, only the values are invented, and they are invented
visibly. Regenerate them the same way against a newer website
checkout, which is what makes `CoursesSurfaceTest` fail when the
emitter grows a field.

`foods.json` earns that sentence more than any of them.
`FoodSurfaceTest` fails on a field the endpoint sends and this app
does not carry, and `FoodsTest` walks every numeric field on all
eighty-three rows: a nutrient added to `shared/foods.ts` has to
reach a screen here with no release, which is only true while
nothing in `Foods.kt` names one. Refresh it and the tests say
whether that is still so.
