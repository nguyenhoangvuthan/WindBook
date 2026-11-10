# Reading controls verification — 2026-11-11

Scope: approved October29 reading-priorities increment B and November10 amendment.
Delivered: version0.1.7/code8, branch reading-controls. Includes the retained
cover-speed changes; see cover-speed-checklist.md for the separate benchmark.
This is a focused M2/M3 subset, not completion of those milestones.

## Behavior and persistence

- [x] Back/title/Contents/overflow, left modal contents drawer, release-only position
  slider and PDF physical page entry. PDF double-tap remains zoom.
- [x] EPUB/TXT typography: publisher/Literata/serif/sans, size, spacing, margins and
  color presets. Global and per-book nullable overrides preserve unknown JSON/PDF
  fields through atomic latest-value transforms.
- [x] Bookmarks are revision/encoding-bound, editable and tombstoned. They count
  as durable data when splitting changed sources. Additive DB7→8 introduces only
  bookmarks and derived PDF contents; schema and migration preservation tests pass.
- [x] PDF outlines are captured at import or prepared for an existing book through
  metadata-only NoCover parsing, serialized with source/import cover extraction.
  Cache is SHA-bound; absent, known-empty and unavailable states remain distinct.
  Reading PDF pages still uses the framework renderer only.
- [x] Search emits50 hits per page, with a2MiB resource limit and explicit limit
  message; no all-hit collection. Vietnamese text and contextual locators survive.
- [x] Successful deliberate jumps retain at most10 full origins. Failed, unchanged,
  stale and ordinary page turns add no origin. Newest seek wins.

## Regression and independent review

- [x] Model/data/UI regressions were run RED before implementation.
- [x] Independent review found six Important issues; fixes cover cursor cleanup
  after ViewModel clearing, rapid appearance edits, stale seek resolution, exact
  quote arrival, bounded incremental search and cancelled-slider recovery.
- [x] A PDF note-reveal/page-jump reset was reproduced and fixed.
- [x] Native long-paragraph regression first returned UNCHANGED for an offscreen
  match. Quote/context/offset range validation now distinguishes repeated hits.
- [x] Native repeated-anchor recreation initially restored the first occurrence.
  Initial session-owned correction now suppresses transient location publication
  and restores the saved range before reporting position.
- [x] Final narrow reviewer approval after recreation and live Aa corrections; no
  unresolved Critical/Important findings in reviewed changes.
- [x] Final mandatory CI/build:473 JVM(63 model/137 data/74 reader/199 app), no
  failures/errors/skips. Lint0 errors/13 warnings, same issue IDs/counts as the
  current-environment baseline; one online Gradle-version notice text differs.
- [x] Actual app Aa initially persisted without updating the live navigator; the
  Compose→host effect now applies it in place. Percentage/multiplier and asset
  prefix errors were also reproduced/fixed. Live computed-style evidence confirms
  Sans→Literata updates, CSS150% and the actual955132-byte font loaded.
  Native/mandatory suites were repeated after these adapter fixes.

## Devices and delivery

Only synthetic content is used in this checkout; screenshots/DBs remain ignored.
No physical-phone or owner-library performance claim is made.

- [x] API36 PDF outline parser/import test verifies first/nested/last physical pages,
  framework rendering and bounded cover behavior; known-empty PDF is cached.
- [x] Final API36 full native50/50; API31 compatible native47/47. The API35-only
  PDF text-selection class cannot load its framework types on31 and is excluded
  there. Both suites include actual TXT re-pagination/return and repeated-quote
  recreation. Native huge2654×4329pt synthetic PDF opens within its1500ms budget.
- [x] Actual app drawer/Back and nested PDF→physical page3, return→page1, numeric
  page2 and invalid page9; bookmarks save/rename persist. EPUB search for3-100
  visibly shows the exact Vietnamese paragraph, with return and live Aa.
  Release-only/cancelled slider behavior is additionally verified by Compose tests.
- [x] Actual paged/vertical PDF tap/zoom screenshots; API31 physical labels/drawer
  also inspected. Native recreation/semantic position tests pass; PDF note-reveal
  and page-jump retention/selection popup regressions pass in JVM tests.
- [x] EN/VI controls and Vietnamese130%/200%, English200% contents inspected; Aa
  remains scrollable. Existing covered PDF with absent derived cache prepares
  nested contents successfully; after force-stop the READY cache is durable and
  preferences/bookmarks/annotations remain byte-for-byte unchanged.
- [x] APK signature verification passes:0.1.7/code8, min31/target36, four ABIs,
  no INTERNET permission.82,122,654 bytes. Exported with adjacent checksum to
  `publish/WindBook-0.1.7-reading-controls-debug.apk`. SHA256:
  `56ba131dd7aa82c370c1b9461f9e11c5a2df17ddbdd49c2931b50ff74351ba72`.

Follow-ups not claimed here: physical-phone/signature compatibility, a full
large-page-count PDF slider stress profile, fixed-layout EPUB visual matrix,
and repeated end-to-end annotation handle drags across every layout/font setting.
Existing native large-scan/selection and focused gesture tests remain green.

The original phone signing key/0.1.5 APK is unavailable in this checkout. The local
candidate uses this environment's debug key; in-place update compatibility with
the owner's installed APK is unverified. Never uninstall an existing phone app
as an installation workaround without protecting its reading data.
