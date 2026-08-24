# From five out of a hundred to the best of them

`ANDROID.md` in the website repository says what the app is and
why. This says how it gets finished, in what order, and how we
tell whether it is actually good rather than merely further along.

Written 22 August 2026, the day the first build ran on a handset
and was fairly scored at 5/100.

## What that five is, honestly

One vertical slice: four school ladders, a lesson rendered
natively, a tick that persists, offline after first open. Under
it, work that will not need doing twice: the palette computed
from the site's own OKLCH and checked against the site's own
reference implementation, a body parser tested against real
lessons, the sync arithmetic ported and tested, the storage keys
named and asserted.

That is a foundation, not an app. What is missing is most of the
site and nearly all of the craft.

## What a hundred means, so it can be measured

Eight axes, scored out of ten, twice a phase. A number nobody can
argue about beats a feeling that it is coming along.

| | What full marks looks like | Now |
| --- | --- | --- |
| **Coverage** | every reader-facing thing the site does, the app does | 1 |
| **Craft** | the material, the motion and the type are the site's, not an approximation | 1 |
| **Native power** | it does things the site cannot: real offline, widgets, background audio, share targets | 1 |
| **Offline** | a school can be downloaded and read on a plane, and the app says what it holds | 2 |
| **Performance** | cold start under a second, no dropped frames on a mid-range handset, measured | 1 |
| **Accessibility** | TalkBack reads every screen in order, 200% font, no colour-only meaning | 1 |
| **Trust** | no analytics, no accounts required, export everything, erase everything | 4 |
| **Proof** | screenshot tests in both themes, UI tests, CI on every push | 1 |

12/80, which is the 5/100 rounded honestly upward.

## The rule that shapes all of it

**This is not a generic engagement app and must not become one.**
The site refuses things on purpose and the app inherits every
refusal: no flame, no streak nagging, no badge counts, no
notification asking somebody to come back, no analytics SDK, no
dark pattern anywhere near the account. `aab/src/streak.ts` says
it plainly and the year of days is the whole of what it draws.

"Best of the best" here means the best READING app somebody could
put a Bangla finance school into, not the most habit-forming one.
Every feature below is judged against that.

## The order, and why each block is where it is

Twelve blocks. Each ends with an installable build that is
visibly better than the one before, because a roadmap whose
middle is unreleasable is a roadmap that gets abandoned in the
middle.

### Block 1. The material, properly

The largest single jump in how it feels, so it goes first.

- The six kinds as real surfaces: chip, control, card, pane,
  plate, groove, with the four numbers each and the derived
  light. The lit cut edge at the bottom, the rim all the way
  round, the dispersion that splits the rim toward the section's
  accent.
- The light: up in 190ms, out in 820ms, on touch rather than on a
  pointer, following the finger where a finger is what there is.
- The textures: weave for sunk grounds, grain for large flats,
  sheen down anything raised, all tinted by the page's accent.
- Tilt on device orientation, at the site's own 1.4 degrees and
  26 degrees of handset travel, honouring reduced motion.
- The real faces bundled: Spectral, IBM Plex Sans and Mono, Noto
  Sans and Serif Bengali, Caveat for what a reader wrote
  themselves. Bangla at 1.9 leading, Latin at 1.65.
- `GoCard`, `InfoCard`, `SoonCard` as three components that
  cannot be each other by accident.

**Ends with:** the app stops looking like a Material sample and
starts looking like reiad.co.uk.

### Block 2. The shell

- Adaptive navigation: bottom bar on a phone, the rail on a
  tablet or an unfolded foldable, the drawer where the site has
  one.
- The audience switch, which reorders and never hides.
- Theme: system, light, dark, plus the glass finish, blur and
  veil the site offers.
- Search: the palette's index, built from the manifest, plus
  `/api/search` for body text, with the site's own grouping.
- Predictive back, shared element transitions between a card and
  the thing it opens.

### Block 3. Reading

- The three reading hubs and the article page, with the topic
  chips and their counts.
- Photos: Coil, with the crop classes (`frame-wide`, `frame-square`,
  `frame-tall`, `focus-top`, `focus-bottom`) honoured as the site
  and the share card honour them.
- The reading progress bar, prev and next, the byline.
- Read aloud: TextToSpeech, Bangla-aware voice selection, the
  same elements skipped, plus what the web cannot have, a media
  session with lock screen controls and background playback.
- The glossary term reader: the in-place rabbit hole with its own
  back stack, which on a handset is better than the web's modal.

### Block 4. The schools, finished

- Hubs with the progress ring, the resume card, the state labels
  where nothing is ever locked.
- Checkpoints inside lesson bodies, filed `<lesson id>#<n>`.
- The practice books: the day walker, typed answers autosaved
  under `deutsch-schrift` and `english-write` (device only, and
  they stay device only), the answer reveal, the day tick with
  each school's own id shape.

  **This one needs the website first, and the reason is the
  site's own rule.** The books are read on the server and
  deliberately never sent to a browser as data: every prompt has
  its answer beside it, so shipping a book as a prop would hand a
  reader the whole key whether or not they pressed the button.
  There is therefore no endpoint to consume.

  The three ways to fix it and what each costs:

  | | |
  | --- | --- |
  | move the books into `shared/` | 450KB of data bundled into the main Worker on every request to every endpoint. No. |
  | a Next route | `/api/*` is the other Worker's, so it needs a path Next already owns and a `NEXT_ROUTES` entry |
  | **the books into D1, beside the lessons** | a migration and an import script, and then `/api/schools/<school>/<stage>/book` is a row read like any other |

  The third is right, and it is right for the same reason the
  lessons are already there: a book is prose that gets edited,
  and prose that gets edited belongs in the database rather than
  in a file somebody has to rebuild. The endpoint sends days
  WITHOUT their answers and a second call returns one day's key,
  which is the same guarantee the web page has.
- The money school's contents page and its A to Z glossary.

### Block 5. The account

The sync engine is already written and tested; this is wiring and
screens.

- Sign in: Custom Tab, the custom scheme, both providers.
- Sync: adopt on sign in, the three merge rules, background
  exchange through WorkManager so a tick sent while offline lands
  later without the app being open.
- Reading list, notes, targets, scenarios, preferences, the year
  of days, export everything, erase everything, the profile.

### Block 6. The tools

- The five calculators, with shareable state in the site's own
  query encoding so a link opens on either.
- The stock check: 44 ratios, six pillars, the weight presets,
  vetoes and flags, Altman and Piotroski, fair value, the Shariah
  screen, the verdict bands, CSV out. Fixture-locked against
  `tools/stock.model.js` so the two implementations cannot drift.
- The live portfolio, all three audiences, the sealed key path
  and the paste-per-session path.

### Block 7. The routine tool

Its own block because `ROUTINE.md` is 523 lines and it deserves
the room: bands, tasks, marks, the year view, the jar, the
garden, the six seasons, quick entry.

### Block 7b. The diet tool

Not in the original list, and it earned its place by being the
biggest single gap between what the site does and what the app
did: `/tools/diet` is fourteen pages and the app had one, which
could show a day and could not add a thing to it. A log you
cannot write to is a screen that describes a tool rather than
being one.

Three of the fourteen now: today, what the day held, and what the
measurements say. The portion library arrives from `/api/foods`
and no food, no nutrient and no unit is named anywhere in this
repository, so a dish added on the site is on the phone at the
next fetch. What is still the site's is the barcode scanner, the
two public food databases, and the eleven reference pages.

The arithmetic is `shared/diet.ts`'s, ported: `totalFor`,
`readingFor` and the coverage floor, which is the rule the whole
tool turns on. Under half the day known, NOTHING is drawn: a
confident number that is missing a third of the day is more
dangerous than no number, and a phone that drew it anyway would
be the more dangerous of the two.

Coverage is weighted by ENERGY and never by the number of rows,
which is the one thing here that would have shipped wrong and
looked right. A 780 kcal restaurant plate beside a cup of rice is
not a half-known day.

### Block 8. Offline as a feature

- Download a school: every lesson body, every photo, with a size
  shown before and a way to remove it after.
- Room for the content cache, a real eviction policy, and a
  screen that says what the app is holding.
- Read on a plane, which is the point.

### Block 9. Native power

Things the site cannot do at all.

- Widgets: continue reading, the year of days.
- App shortcuts, share target, quick settings tile.
- ~~Video in the course player: Media3, with the ticket flow.~~
  **Done, and out of order**, because it stopped being a feature
  and became a bug: the gold card on `/skills` opened a Custom Tab
  and the one reader it belongs to got a page saying they were
  signed out. The address was right. The hand-off was not, and
  could not be made right: the site's session is a bearer token in
  the BROWSER's storage and the app's is its own, so a tab opened
  from here always arrives with no credential. All five views are
  native now, over `/api/courses` with the app's own token, and
  the video is Media3 over the site's thirty-minute single-file
  tickets with captions as WebVTT on a pass of their own.
- Still to do on it: picture in picture, background audio, and
  downloads. None is what was broken, and each is a real feature
  rather than a fix.
- Notifications: opt in, and only for a thing the reader asked
  for. Nothing that nags.

### Block 10. Accessibility and adaptivity

- A TalkBack pass over every screen, in order, with real labels.
- 200% font without a broken layout.
- Nothing meaningful carried by colour alone.
- Tablets, foldables, landscape, per-app language.

### Block 11. Performance, measured

- Baseline profiles, cold start under a second on a mid-range
  handset, measured with macrobenchmark rather than asserted.
- No dropped frames scrolling a long lesson.
- APK size looked at, not ignored.

### Block 12. Proof and release

- Screenshot tests of every component in both themes and all
  seven accents.
- UI tests over the journeys that matter.
- CI on every push, the way the website has.
- The keystore, the data safety form, a staged rollout.

## What this costs

Each block is one to three working sessions. Twelve blocks is
roughly **eighteen to thirty sessions**, and the pace is yours:
back to back that is a fortnight of solid days, one a day is
about a month, weekends only is a quarter.

Blocks 1 and 2 are the ones that change how it feels. If the
question is "when does it stop looking basic", the answer is the
end of block 2, and that is two or three sessions away.

## Three decisions worth taking early

**The seven case studies.** They are interactive financial models
in JavaScript, several of them large, and porting all seven to
Kotlin is a block on its own with no reader-facing gain over
opening them in a Custom Tab. Recommendation: link out for now,
port `three-statement` and `dcf` natively later if they earn it,
and say so in the app rather than hiding it.

**The courses player.** ~~Admin only, and it needs Media3,
tickets, captions and quiz parsing. Real work for one reader. It
sits in block 9 rather than earlier for that reason.~~

Overtaken. "Real work for one reader" was the right sum right up
until that one reader pressed the button and nothing opened, and
then the sum changed: this was not a feature waiting its turn in
block 9, it was a dead button on a screen that had already been
shipped. A section the app offers and cannot open is worse than a
section it does not offer.

The part of the reasoning that held is the boundary, and it has
not moved: nothing course-shaped is in the binary, nothing is
cached to disk, the catalogue arrives only over the authenticated
API, and the Worker checks `isAdmin()` on every route. What
changed is only who does the asking.

**Fonts.** Bundling five families is several megabytes. Downloadable
Fonts via Google Play Services keeps the APK small and costs a
first-run fetch. Recommendation: downloadable, with the system
faces as the fallback that already works.
