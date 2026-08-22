# reiad-android

The native Android app for [reiad.co.uk](https://reiad.co.uk).
Kotlin and Jetpack Compose, over the site's own public API.

`ANDROID.md` in the website repository is the plan: what the app
consumes, what carries over unchanged, and the order it gets
built in. This file is how to run what is here.

## Run the tests

```sh
gradle :core:test
```

No Android SDK needed for that, deliberately. See below.

## Build the app

```sh
gradle :app:assembleDebug
```

The APK lands at `app/build/outputs/apk/debug/app-debug.apk`.
Android Studio supplies the SDK; from a terminal put its path in
`local.properties` as `sdk.dir=...`, which is gitignored because
it is a fact about one machine.

## Layout

| | |
| --- | --- |
| `core/` | plain Kotlin. API models, the body parser, the storage keys, the sync arithmetic, the palette |
| `core/src/test/resources/fixtures/` | real answers from the live API, captured rather than written |
| `app/` | Compose. The theme, the material, the body renderer, the money school |

## What the app does today

The money school, end to end: the ladder off `/api/schools/money`,
a lesson opened and rendered through the parser, and a tick that
survives a restart. Deliberately a whole vertical rather than a
prettier ladder, because a ladder that renders over a lesson that
will not open is the exact shape of thing this project keeps
promising not to ship.

Opening is not finishing. The money school's tick is a button.
The other three schools mark a lesson on opening, which is their
own semantics and arrives with them.

Not yet: the account and sync, the other three schools, photos,
the practice books, the tools. `ANDROID.md` has the order.

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
