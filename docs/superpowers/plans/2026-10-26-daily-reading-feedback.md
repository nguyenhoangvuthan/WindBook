# Daily Reading Feedback Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans task-by-task. Steps use checkbox syntax for tracking. Independent folder and PDF-notes domains may execute in parallel under dispatching-parallel-agents; controller owns integration, final CI and commit.

**Goal:** Ship recursive folder-task scanning/settings, clear title/time sorting, paged PDF defaults, and usable PDF text notes.
**Architecture:** Reuse SAF scanner/session leases and existing framework PDF worker. Add bounded platform selection and additive annotation storage; preserve source revisions and current locators.
**Tech Stack:** Existing Kotlin/Compose/Room3/Readium/PdfRenderer, no new dependencies.
**Spec:** docs/superpowers/specs/2026-10-26-daily-reading-feedback-design.md (owner requests and explicit scoped note confirmation).

## Global Constraints

API31 minimum; text selection API35+ guarded. EPUB/PDF/TXT only current discovery controls. No source writes, INTERNET, MANAGE_EXTERNAL_STORAGE, new dependencies, paused adapters, EPUB/scan annotation expansion, push or merge. EN/VI strings in both strings.xml. Preserve system status bar. Historical DB schemas1–6 immutable; notes migration6→7. Root owns final full CI/commit and AGENTS8/9; workers return focused evidence without staging peers' files.

## Review Focus

Typed Linux/example paths never pretend to grant Android access; only actual grant starts reading. Saved options survive settings/route changes. Invalid/partial provider data is reported without dropping valid siblings unnoticed. PDF selection and marks align under crop/zoom/rotation and cannot leak across sessions. Repeat Notes navigation to an open book must use an explicit request rather than initialPage alone; changed source revisions retain old notes safely.

### Task1: Paged default (controller)

Files: core/model/.../PdfDisplay.kt, PdfDisplayTest.kt; downstream expectations only where behavior changed.
- [x] RED: portrait/mixed/empty page lists expect PAGED; saved zoom survives and explicit VERTICAL wins (6 failures observed).
- [x] Return PAGED for the default; targeted9tests GREEN. Preserve saved/legacy decoding; final suite will check downstream assumptions.

### Task2: Folder path task, settings and sort

Files: app/folders/*, settings/*, navigation/WindBookNavHost.kt (coordinate controller), core/data/LibraryPreferences.kt or focused scan preferences, FolderRepository/DAO entry timestamps if needed; model folder-sort/location types if pure. Tests: FolderViewModelTest, FolderScreenTest, SettingsScreen/ViewModel tests, preference persistence, path mapping, stable sort and nested scan behavior.
Interfaces: persisted scan defaults (ScanOptions) and per-folder snapshots; typed-path initial URI resolver returns only validated Android document/tree URIs; scan request either starts verified existing root or emits picker request, granted picker result connects then scans. Folder entry time is explicitly book addedAt; sort options title asc/desc and added newest/oldest.
- [x] Add behavioral RED regressions for typed-path Scan being actionable, only three visible extensions, settings persistence, options above saved list, auto scan after grant, sorting and stale-root request protection.
- [x] Implement the scoped workflow; preserve all scanner identity checks, interrupted-run and read-only behavior.
- [x] Focused GREEN; actual synthetic nested EPUB/PDF/TXT tree scans every selected file with no unintended formats. Verify Settings, editable path/picker, counts, sorting and reopen.

### Task3: PDF text notes

Files: core/model/PdfTextAnnotation.kt + coordinate/validation tests; core/data/db annotation entity/DAO and WindBookDatabase v7; AnnotationRepository and tests; BookSourcesRepository durable-data checks; reader/pdf/PdfDocumentHandle/PdfPages text-selection seam + device tests; app/reader/pdf selection UI and overlay/editor; app/notes list/viewmodel; reader/navigation routes and repeatable note reveal; bilingual resources. No folder/settings files except coordinated NavHost or string append.
Interfaces: immutable PdfTextSelection (page, full dimensions, normalized rectangles, quote/endpoints); suspend Pdf text source selection on existing worker; persistent annotation ID/bookId/anchor/color/body/timestamps; repository flow drives marks and notes; annotation-aware ReaderKey/reveal request identifies a note independent of last-read locator.
- [x] RED model validation/hand-derived crop transforms, note CRUD/migration/soft-delete/revision durability, newest pending selection and repeat navigation.
- [x] Implement bounded API35+ selection, disposable per-page interaction, persisted highlights/notes and repeatable source reveal. No native calls on main/no all-page extraction.
- [x] GREEN focused tests; actual API36 selection with Vietnamese/multiline/empty/reversed handles; save/edit/delete/restart and Notes revisit, both layouts/crop/zoom/rotation. API31 reader remains guarded. Record extraction limitations honestly.

### Task4: Integration and delivery (controller)

- [x] Required CI: ./gradlew :core:model:test :core:data:testDebugUnitTest :reader:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug plus :app:assembleDebug. No added lint warnings.
- [x] Independent whole-change review and required RED/GREEN fixes; reuse already-green historical folder/TXT gates when unchanged.
- [x] Update AGENTS8/9, roadmap/checklists, ignored checkpoint/report; commit local conventional changes, export checksum-verified debug APK. Owner controls merge/push.
