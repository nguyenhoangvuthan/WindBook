# Single-book AI implementation

Date: 2026-11-16. Branch: `single-book-ai`, from delivered0.1.8 `minimal-library`.
Authorization: owner approved the written design and explicitly requested both
planning and implementation in the same instruction. Continue sequentially without
another scope/plan approval round. Independent review remains required.

## Constraints and interfaces

No new Gradle dependency; use Readium3.4.0 native selection/decorations, framework
HTTPS and Android Keystore. Preserve OfflineHttpClient and reject external EPUB
resources even after the explicit AI-only INTERNET exception. Keep PDF rendering
on the existing worker. Never embed the desktop key into source/APK; Android gets
its own user-entered key encrypted under Keystore in noBackupFilesDir.
All strings EN/VI. All source books read-only. No push or main integration.
Only owner-selected private books may be transmitted; use synthetic fixtures while
waiting for that selection. Desktop key file is approved and nonempty; never print it.

## 1. Typed EPUB annotations

Files: core/model/.../EpubTextAnnotation.kt, PdfTextAnnotation.kt;
core/data/.../AnnotationRepository.kt, db/AnnotationDao.kt,
db/EpubAnnotationMigration.kt, db/WindBookDatabase.kt;
app/.../notes/NotesViewModel.kt and NotesScreen.kt.

Add EPUB_TEXT/PDF_TEXT kind and optional typed EPUB anchor without changing legacy
PDF constructors or payloads. Anchor holds locator JSON, exact quote and bounded
before/after context with normalized offset for duplicate passages. Add v8→v9 kind
column default PDF_TEXT. Unknown kind/invalid anchor remains visible, unrevealable.
Repository save validates format and immutable SHA in the write transaction.

RED: EpubTextAnnotationTest bounds/relative resources; AnnotationRepositoryTest
EPUB round-trip, stale SHA rejection, malformed kind retained; migration from v8
preserves original rows including PDF anchor bytes. GREEN before UI integration.

## 2. EPUB selection and notes UI

Files: reader/.../ReaderHostFragment.kt, EpubSelection.kt;
app/.../reader/epub/EpubNotesReader.kt, ReaderScreen.kt, ReaderControlsScreen.kt.
Expose selection and decoration-tap callbacks tied to the bound session. Capture
semantic locator with quote/context/offset; selection limited to one resource.
Clear ephemeral selection on chapter/typography/session changes. Existing note
editor owns draft/retry/rotation; native handles remain usable. Apply Readium
decorations and exact-source revisit using existing navigate verification.
RED/GREEN: duplicate save, old session callbacks, unknown anchors, duplicate quote
navigation; actual native selection/restart/rotation/font-change tests on emulator.

## 3. Gemini client, credential handling and desktop experiment

Files: core/model/.../learning/Learning.kt; core/data/.../learning/GeminiClient.kt,
GeminiKeyStore.kt; tools/learning_trial.py and tests; app/.../learning/AiSettings.kt.
Use generateContent REST with x-goog-api-key header, fixed Google HTTPS host,
redirects disabled, bounded payload/response, finite timeouts and output cap.
No request/response body or exception URL diagnostics. Cancellation does not retry;
429 returns actionable retry state; no silent paid-model fallback.
Verify official API/pricing and live model availability with supplied key. Start
with a small synthetic passage. Desktop extractor uses bounded ZIP/XML/HTML reads,
spine order, no remote fetch; preview before live calls; checkpoint summaries.
RED/GREEN: bounds, unsafe path, rate errors, malformed/empty/truncated model output,
context prompt separation and credential absence from artifacts. Key UI supports
save/remove without exposing stored key and does not survive Android backup.

## 4. Durable learning data and context preparation

Files: core/data/.../db/LearningDao.kt, LearningMigration.kt;
core/data/.../learning/LearningRepository.kt;
reader/.../BookTextReader.kt; app/.../learning/LearningViewModel.kt.
Add v9→v10 tables for accepted answers, vocabulary and derived section summaries.
Answers/vocabulary attach to an annotation so existing durable-source guards
continue to apply. Store source SHA, model and prompt/context versions. Transactions
validate annotation/book SHA; accepted answer never replaces handwritten body.
Explicit preparation extracts bounded sections serially, checkpoints successful
chunks, exposes partial/complete/failure state and resumes unchanged SHA/version.
Subsequent questions use bounded local context, summaries and original excerpts
with locations; do not claim unsupported whole-book knowledge.
RED/GREEN: stale revision, cancellation, resume, source mismatch, duplicate accept,
offline read, cascade behavior, serialized payload bounds and migration preservation.

## 5. Learning card and integration

Files: app/.../learning/LearningCard.kt, VocabularyScreen.kt;
app/.../reader/epub/EpubNotesReader.kt, reader/pdf/PdfNotesReader.kt,
reader/pdf/PdfAnnotationUi.kt, notes/NotesScreen.kt, settings/SettingsScreen.kt;
app/src/main/AndroidManifest.xml and EN/VI strings.
Selection AI action opens bounded quote/location card with collapsible translation,
grammar, idioms/vocabulary/context and follow-up. Explicit Generate and Prepare
book disclose sending text to Google/free-tier processing. Save accepted result
beside its highlight; chosen vocabulary gets a separate offline list and source
revisit. Offline/no key/quota/cancel retains drafts and selection. Add narrow
INTERNET exception while retaining remote-publication blocking.
RED/GREEN: request/session replacement, save twice, dismiss/cancel, offline saved
answer, key absent, vocabulary source, both formats, EN/VI large-font layout.

## 6. Verification, review and delivery

For each production checkpoint run:
`./gradlew :core:model:test :core:data:testDebugUnitTest :reader:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug`.
Use focused tests for RED/GREEN first. Capture logs in
`.superpowers/checks/single-book-ai/` and decisions in
`.superpowers/sdd/single-book-ai/progress.md`. Do not rerun old green checks without
a new change. Preserve existing emulator library and database before upgrade.
Run API36 native suite and API31 compatible suite plus real visual selection/card
checks, offline restart and migration row comparison. Record unrun gates honestly.
Independent whole-branch review and failing-test-first Important fixes precede
delivery. Bump to0.1.9/code10 after verified changes, assemble debug and export
`publish/WindBook-0.1.9-single-book-ai-debug.apk` with SHA256; files stay untracked.
Update AGENTS sections8/9, roadmap and testing checklist. Backup/export integration
is deliberately deferred until the following approved increment exists.

Official references checked2026-11-16: https://ai.google.dev/gemini-api/docs/text-generation,
https://ai.google.dev/gemini-api/docs/pricing,
https://ai.google.dev/gemini-api/docs/rate-limits and
https://ai.google.dev/gemini-api/terms. Free-tier limits remain project-dependent;
there is no guaranteed whole-book throughput or owner quality verdict yet.
