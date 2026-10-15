# WindBook – Product & Technical Design

Date: 2026-10-13 · Status: draft for review
Inputs: `docs/WindBook_Technical_Blueprint.md`, `docs/research/2026-10-13-windbook-validation.md`, `docs/research/2026-10-13-reader-feature-benchmark.md`

## 1. Purpose

WindBook is a native Android reader for EPUB/PDF/CBZ files the user owns. It is **personal-first**: the owner daily-drives a sideloaded APK on a **Nubia Neo 3 GT**. If it sticks, it ships on Google Play. Nothing in the design may block that later release.

**Positioning:** ReadEra's simplicity + KOReader's learning tools + Kindle's polish, offline, on your own files, tuned for AMOLED eyes.

**Success criteria**
1. The owner stops opening any other reader app for EPUB/PDF.
2. WindBook never loses the reading position: not on app kill, rotation, font change, or MyOS background killing.
3. Cold start < 2 s and import of a 20 MB EPUB < 3 s on the Neo 3 GT (measured, release build).
4. Every feature works offline, except features whose job is the network (OPDS, Wikipedia, WebDAV/Readwise/KOReader sync) and the one-time translation-model download.

## 2. Scope

**In:** every feature in the benchmark marked v1 / v1.x / v2, grouped into milestones M1–M8 (§6).
**Out (explicit):** read aloud / TTS playback (benchmark §F), AI assistant (benchmark §H), and everything the benchmark marks ✗ (MOBI/AZW3/FB2/DjVu, Bionic Reading, Word Wise, awards, comic Bubble Zoom, TTS→MP3).
**Kept, by decision:** *pronounce a single looked-up word* through the system TTS engine. It is part of the lookup card, not book read-aloud.

## 3. Global constraints

| Item | Value |
|---|---|
| applicationId / namespace | `dev.kent.windbook` |
| minSdk / targetSdk / compileSdk | 31 / 36 / 37 |
| AGP / Gradle / JDK | 9.4.1 / 9.6.0 / 21 (toolchain), bytecode target Java 11 |
| Kotlin | 2.4.20 via AGP built-in Kotlin (no `org.jetbrains.kotlin.android` plugin) |
| KSP | 2.3.12 (KSP2) |
| Compose BOM / Material 3 | 2026.09.00 / 1.4.0 |
| Navigation | Navigation 3 1.2.0 + lifecycle-viewmodel-navigation3 2.11.0 |
| DI | Hilt 2.60.1, androidx.hilt 1.4.0 |
| Database | Room 3.0.3 (`androidx.room3`) + `androidx.sqlite:sqlite-bundled` 2.7.1 |
| Preferences | DataStore 1.2.1 |
| Reader engine | Readium Kotlin Toolkit 3.4.0 (EPUB navigator fragment, PDFium adapter); JitPack repo required for PdfiumAndroid |
| Background work | WorkManager 2.12.0 |
| Images | Coil 3.6.3 |
| Desugaring | `desugar_jdk_libs` 2.1.5 (required by Readium) |
| Translation | ML Kit `com.google.mlkit:translate` 17.0.3 (on-device models) |
| Tests | JUnit 4.13.2, Robolectric 4.17, Turbine 1.2.1, MockK 1.14.11, kotlinx-coroutines-test 1.11.0, androidx.test 1.7.0 |
| Permissions | No `MANAGE_EXTERNAL_STORAGE`, no `READ_EXTERNAL_STORAGE`. Files arrive only via SAF. `INTERNET` used only by network features, each user-triggered |
| Privacy | No account, no ads, no analytics, no crash upload. Nothing leaves the device without an explicit user action |
| UI languages | English (default, `values/`) and Vietnamese (`values-vi/`). Every user-visible string is a resource. Per-app language picker via generated locale config (Android 13+ system settings) |
| Licences | Only Apache-2.0 / BSD / MIT / OFL dependencies and fonts. No GPL/AGPL code |

## 4. Architecture

```
:app            Compose UI (package-by-feature), Hilt graph, Navigation 3, Activity, workers
:core:model     Pure Kotlin/JVM: domain models + pure logic (format sniffing, SRS, time-left, merge rules)
:core:data      Room 3 database, DAOs, DataStore, file storage, repositories, importers, snapshot (backup/sync)
:reader         ReaderEngine interface + Readium implementation, fragments that host Readium navigators
```

- **Data flow:** Compose screen → ViewModel (StateFlow UI state) → repository (core:data) / ReaderEngine (reader). ViewModels never touch Room or Readium types directly, except `Locator` JSON strings, which are opaque to everything outside `:reader`.
- **Reader embedding:** a `ReaderHostFragment` (our class, no-arg constructor) is embedded with `AndroidFragment`. It installs Readium's fragment factory on its `childFragmentManager` and adds `EpubNavigatorFragment` / `PdfNavigatorFragment`. After process death it first installs Readium's dummy factory, then reopens the book from the DB and replaces the dummy navigator, so the user lands on the saved Locator.
- **Positions:** progress is stored as Readium `Locator` JSON, plus `progression` (0–1) for display and sorting. Never page numbers.
- **Writes are immediate:** every `currentLocator` emission upserts `reading_progress` (single-row upsert, conflated). MyOS kills apps aggressively, so there is no "save on exit".
- **Sync-ready data:** every user-data row has `id` (UUID string), `updated_at`, `deleted_at` (tombstone). Hard deletes only happen on tombstone GC after a successful sync/backup.
- **One snapshot format:** `WindBookSnapshot` (kotlinx.serialization JSON, versioned) is used by manual backup, scheduled backup and WebDAV sync. Merge = last-writer-wins per row by `updated_at`, tombstones win ties.

## 5. Data model (target schema; each milestone adds its tables via Room auto-migration)

| Table | Columns (all TEXT ids are UUIDs, times are epoch ms) | Milestone |
|---|---|---|
| books | id PK, title, author?, format (EPUB/PDF/CBZ), file_path, file_size, sha256 UNIQUE, cover_path?, source_uri?, status (TO_READ/READING/FINISHED), added_at, last_opened_at?, updated_at, deleted_at? | M1 |
| reading_progress | book_id PK→books CASCADE, locator_json, progression REAL, updated_at | M1 |
| book_preferences | book_id PK→books CASCADE, json, updated_at | M2 |
| bookmarks | id PK, book_id→books CASCADE, locator_json, progression, label?, created_at, updated_at, deleted_at? | M3 |
| reading_sessions | id PK, book_id→books CASCADE, started_at, ended_at, active_seconds, start_progression, end_progression, positions_read | M3 |
| annotations | id PK, book_id→books CASCADE, locator_json, progression, chapter_title?, selected_text, note?, color, style (HIGHLIGHT/UNDERLINE), created_at, updated_at, deleted_at? | M4 |
| annotation_tags | annotation_id→annotations CASCADE, tag, PK(annotation_id, tag) | M4 |
| vocabulary | id PK, word, language, context_sentence?, book_id?→books SET NULL, locator_json?, definition?, translation?, created_at, updated_at, deleted_at? | M5 |
| review_cards | id PK, kind (WORD/HIGHLIGHT), ref_id, due_at, interval_days, ease, reps, lapses, updated_at, deleted_at? | M5 |
| dictionaries | id PK, name, source_language, target_language, dir_path, word_count, enabled | M5 |
| collections | id PK, name, created_at, updated_at, deleted_at? | M6 |
| book_collections | book_id, collection_id, updated_at, deleted_at?, PK(book_id, collection_id) | M6 |
| watched_folders | tree_uri PK, added_at, last_scan_at? | M6 |
| opds_catalogs | id PK, title, url, username?, secret_alias? | M6 |
| reading_goals | year PK, target_books, target_minutes? | M6 |

Indexes: `books(sha256)` unique, `books(last_opened_at DESC)`, `bookmarks(book_id)`, `annotations(book_id)`, `vocabulary(word)`, `review_cards(due_at)`. Foreign keys on.

Secrets (OPDS passwords, WebDAV password, Readwise token) are encrypted with an Android Keystore AES-GCM key and stored in DataStore. The DB only holds the alias.

## 6. Milestones & features

Each milestone ends with a signed APK the owner installs and uses. Each gets its own implementation plan, written when the milestone starts (later plans depend on real interfaces from earlier ones).

### M1 – Core reading loop (alpha)
- F1.1 Import EPUB/PDF via SAF picker (multi-select). Validate magic bytes (`PK\x03\x04` + `mimetype` = `application/epub+zip`; `%PDF-`). Max 500 MB. Reject ZIPs with >10 000 entries or declared uncompressed total >2 GB. Stream-copy to `filesDir/books/<uuid>.<ext>` while hashing SHA-256.
- F1.2 Duplicate detection by SHA-256 → the existing book is returned, with a "Already in library" message.
- F1.3 Metadata + cover extraction via Readium. Cover saved as WebP in `filesDir/covers/<id>.webp`. Title falls back to the file name.
- F1.4 Library: grid/list toggle, sort (recent, title, author, added, progress), search by title/author, status filter chips (All / Reading / To read / Finished).
- F1.5 "Continue reading" hero card (most recently opened READING book) with progress.
- F1.6 Reading status: auto READING on first open; manual change via long-press menu. Progress ≥ 0.99 offers "Mark finished".
- F1.7 Reader: EPUB (reflowable + fixed layout) and PDF via Readium.
- F1.8 Save Locator on every change. Restore on open, after rotation, and after process death.
- F1.9 Immersive reading: system bars hidden (transient by swipe). Centre tap toggles a minimal overlay (top: back + title; bottom: progress % + chapter). Left/right edge taps turn pages (Readium `DirectionalNavigationAdapter`).
- F1.10 Volume keys turn pages while the reader is visible.
- F1.11 Keep screen on while the reader is visible.
- F1.12 Delete book: rows (cascade), book file and cover file removed. Confirmation dialog.
- F1.13 Engineering: release signing from env/`keystore.properties` (git-ignored), GitHub Actions CI (lint, unit tests, assembleDebug), install + smoke test on Waydroid.

### M2 – Reading comfort (eye-care pack)
- F2.1 Themes: Paper (#F8F4EC / #2B2B2B), Sepia (#F1E7D0 / #3B2F22), Dusk (#1E1E1E / #D8D4CC), OLED Black (#000000 / #BDB8AE). Readium `theme`/`backgroundColor`/`textColor`.
- F2.2 Auto theme: follow system dark mode, or a time window (default 19:00–07:00 → Dusk).
- F2.3 Bundled fonts (OFL): Literata (default), Atkinson Hyperlegible Next, Inter. Plus "Publisher font".
- F2.4 Typography: font size, line height (1.2–2.0, default 1.5), page margins, paragraph spacing, paragraph indent, text align (justify only when hyphenation is on), hyphenation, publisher styles toggle.
- F2.5 Font-weight boost (Readium `fontWeight`), default +0.1 in Dusk/OLED themes.
- F2.6 Paginated / continuous scroll. Page-turn animation none/slide (Readium 3.2 animated go), default none.
- F2.7 Brightness: vertical swipe on the left edge (inside safe insets) sets window brightness.
- F2.8 Low-flicker dimming (toggle): window brightness stays ≥ 0.35 and further dimming is a black Compose overlay (alpha 0–0.8).
- F2.9 Warm tint overlay (amber, alpha 0–0.3), evening schedule shared with auto theme. Copy never claims eye protection.
- F2.10 Settings bottom sheet with live preview: presets row (Comfort / Compact / Large) + Advanced section.
- F2.11 Per-book overrides (`book_preferences`), "Reset to global" action.
- F2.12 In-app orientation lock (portrait / landscape / auto). Two-page spread in landscape (Readium `spread`/`columnCount`).
- F2.13 E-ink mode: pure B/W theme, animations off, paginated forced.
- F2.14 Break nudge (opt-in): after N minutes (default 20) of continuous reading, a quiet snackbar "Look 6 m away for 20 s".
- F2.15 Respect system font scale on all non-reader screens and the system "Remove animations" setting.

### M3 – Navigation & reading flow
- F3.1 TOC sheet: nested chapters, current chapter marked, per-chapter progress.
- F3.2 Bookmarks: toggle from the overlay, list with chapter + %, swipe to delete (tombstone).
- F3.3 Footnote pop-up: Readium `shouldFollowInternalLink` with footnote context → bottom card, no jump.
- F3.4 "Back to where I was" chip after following any internal link (keeps the last 10 jumps).
- F3.5 Reading sessions: start on reader resume, end on pause or 2 min idle; track active seconds and positions read.
- F3.6 Status footer (toggleable): clock, battery, chapter progress, **time left in chapter / book**, from the personal speed (median seconds per Readium position over the last 20 sessions; default 60 s/position until then).
- F3.7 In-book search for EPUB via Readium search service. Results list with context snippets. Jump + highlight result.
- F3.8 Full-screen image viewer with pinch zoom (EPUB tap on image, Readium `TapEvent.targetElement`).
- F3.9 Auto-scroll in scroll mode: speed slider, tap to pause.
- F3.10 Custom tap zones (3×3 grid → action) and key bindings ("press a key to assign"). This covers volume keys and hardware page-turn keys.
- ~~F3.11 Shoulder triggers~~: dropped by the owner on 2026-10-15. WindBook does not support the Neo 3 GT shoulder triggers.

### M4 – Highlights & notes
- F4.1 Text selection menu: Highlight (5 colours), Underline, Note, Copy, Look up (→ M5), Share.
- F4.2 Highlights rendered as Readium decorations (highlights behind text, experimental positioning). Tap a highlight → edit colour/note/delete.
- F4.3 Per-book notes list (sorted by position, filter by colour) → jump.
- F4.4 Global Quotes hub: all highlights across books, search text/note, filter by book/colour/tag.
- F4.5 Tags on highlights (free text, autocomplete).
- F4.6 Markdown export (per book or all), Obsidian-friendly: front-matter (title, author, exported_at), `## Chapter` headings, `> quote`, note, `(xx%)`. Via share sheet or SAF "save as".
- F4.7 Share a quote as an image (styled card: quote, title, author, theme colours).

### M5 – Language & learning
- F5.1 Lookup card (long-press word or "Look up" on a selection): definition · translation · pronounce · highlight · save word.
- F5.2 Offline dictionaries: import StarDict (`.ifo/.idx/.dict` or `.dict.dz`, also zipped) via SAF, binary-search the idx. Fallback "Open in…" to installed dictionary apps (`ACTION_PROCESS_TEXT`).
- F5.3 Offline translation (ML Kit): word, sentence, paragraph. Source language auto from book metadata, target from settings (default Vietnamese). Model download asks first (Wi-Fi only by default).
- F5.4 Pronounce the word (system TextToSpeech, single word only).
- F5.5 Wikipedia summary (online, on tap only) in the lookup card.
- F5.6 Vocabulary: saved words keep the context sentence, book and Locator. Auto-save on lookup is a setting (default off).
- F5.7 Review (SM-2 spaced repetition) for words and highlights: daily queue, Again/Hard/Good/Easy. Highlight resurfacing uses the same engine.
- F5.8 Parallel bilingual view (EPUB): a Compose reader over Readium Content API paragraphs with original + ML Kit translation (side-by-side in landscape, interleaved in portrait), synced back to the Locator.

### M6 – Library power features
- F6.1 Collections (a book can be in many). Manage from the library long-press and book details.
- F6.2 Watched folders (SAF tree URI, persisted). WorkManager scan on app start + every 12 h imports new EPUB/PDF/CBZ.
- F6.3 Book details screen: metadata edit (title, author), cover replace from image picker, file info, collections, status.
- F6.4 CBZ support (Readium image publications).
- F6.5 Home-screen "Continue reading" widget (Glance).
- F6.6 OPDS 1/2 catalogs (Readium OPDS): browse, search, download into the library. Basic auth. A Calibre content server works as an OPDS catalog.
- F6.7 Stats screen: daily minutes (bar chart), streak, calendar heat-map, books finished per month, total time per book.
- F6.8 Yearly goal (books and/or minutes) with progress.
- F6.9 User fonts: import TTF/OTF via SAF and use them in the reader.

### M7 – Data portability & sync
- F7.1 Manual backup: `.windbook` zip = `snapshot.json` (+ optional `books/` and `covers/`). Saved via SAF.
- F7.2 Restore: validate version, merge (LWW) or replace, then re-link book files by SHA-256.
- F7.3 Scheduled auto-backup to a chosen SAF folder (daily/weekly, keep last N=5).
- F7.4 WebDAV sync (Nextcloud etc.). Each device writes `WindBook/devices/<deviceId>.json`, then reads all device files and merges LWW. Books matched by SHA-256; book files are not uploaded.
- F7.5 KOReader progress sync (kosync protocol): document key = MD5 of file per kosync spec; WindBook pushes/pulls `percentage` (PDF: page); xpointer progress from KOReader is mapped by percentage.
- F7.6 Readwise push: send highlights via Readwise API (token in Keystore), incremental by `updated_at`.
- F7.7 Anki export: CSV (front, back, context, source) for words and highlights.

### M8 – PDF annotations, accessibility, Play readiness
- F8.1 PDF search + highlights + ink notes via `androidx.pdf` (`PdfViewer` / `EditablePdfViewerFragment`, `androidx.ink`). **Gate:** adopt when androidx.pdf 1.0.0 is stable. If still beta at M8 start, the owner decides (beta is acceptable for personal use).
- F8.2 Accessibility audit: TalkBack on every screen, ≥ 48 dp targets, contrast ≥ 4.5:1 for UI and ≥ 7:1 for reading themes, font scale 200% layout check.
- F8.3 Baseline profiles + Macrobenchmark (startup, open book, page turn) measured on the Neo 3 GT.
- F8.4 Play readiness: AAB, R8 full mode, data-safety form (no data collected), privacy policy page, store listing assets, Play App Signing with upload key.

## 7. UX rules (apply to every milestone)

1. The page is the product: no chrome while reading. One tap reveals minimal controls.
2. All reader controls are in the bottom third (tall 20:9 phone, one hand).
3. Presets first, "Advanced" second.
4. Material 3 dynamic colour for library/settings only. The reading surface always uses WindBook reading themes.
5. Motion ≤ 250 ms and interruptible. Respect "Remove animations".
6. Gestures stay out of the system back-gesture edges (use `WindowInsets.systemGestures`).
7. No onboarding carousel. An empty library shows one big "Add books" button.
8. Error copy says what happened and what to do ("This file isn't a valid EPUB. Try re-downloading it.").

## 8. Error handling

| Situation | Behaviour |
|---|---|
| Invalid/corrupt file on import | Rejected with a reason. Partial copy deleted. Other files in a multi-import continue |
| Duplicate import | Existing book returned. Snackbar "Already in your library" with an "Open" action |
| Book file missing on open | Book row stays. Error screen offers "Remove from library" (M1). "Locate file" re-link (SAF) is added with the book details screen (M6) |
| Readium fails to open | Error screen with a reason and "Back". Book not marked READING |
| Saved Locator invalid (e.g., book replaced) | Open at start, keep the old progress row until the first new save |
| Storage full while copying | Import aborted, temp file deleted, message shows needed vs free space |
| Process death in reader | Reopen at the last saved Locator without user action |
| Network feature offline | Inline message in that card only. Never blocks reading |

## 9. Testing strategy

- **JVM unit tests (`:core:model`)**: pure logic: format sniffing, SM-2, time-left estimator, LWW merge, Markdown export formatter.
- **Robolectric tests (`:core:data`, `:reader`, `:app`)**: Room DAOs (in-memory, `AndroidSQLiteDriver`), importer with temp files, Readium metadata on generated EPUBs, ViewModels with Turbine, Compose UI tests via `createComposeRule`.
- **Instrumented smoke tests** on Waydroid (x86_64), plus a manual checklist per milestone on the Neo 3 GT (process death via `adb shell am kill`, rotation, MyOS background kill, real books).
- **Benchmarks** (M8): Macrobenchmark on the Neo 3 GT.
- **Test corpus**: `testdata/` with generated minimal EPUB/PDF in tests. Real-book manual corpus stays outside git (copyright).

## 10. Risks

| Risk | Mitigation |
|---|---|
| Readium Compose navigators are alpha | Use fragment navigators embedded via `AndroidFragment`. Revisit when stable |
| PdfiumAndroid unmaintained, comes from JitPack | Isolated behind `:reader`. androidx.pdf migration path in M8 |
| ML Kit translation needs Google Play services for the model download; Waydroid has none | Test translation on the real phone. Feature degrades with a clear message |
| KOReader xpointer ↔ Readium Locator mismatch | Percentage-based mapping. Documented as approximate |
| AGP 9 built-in Kotlin with Hilt/KSP plugin compatibility | Pinned versions verified together in M1 Task 1. Readium 3.4.0 itself builds with the same Kotlin/KSP line |
