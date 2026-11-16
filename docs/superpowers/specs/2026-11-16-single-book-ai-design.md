# Single-book learning trial — option B

Date: 2026-11-16
Status: approved by the owner. The owner also approved the desktop Google trial,
provided a key outside the repo, selected /home/kent/Library as the trial source,
and explicitly requested planning plus implementation on2026-11-16.
Baseline: delivered 0.1.8 on minimal-library, database v8.

## 1. Scope and order

1. Implement the already-approved reflowable EPUB selection/highlights/notes
   contract from the October29 design section5 and plan D1–D2.
2. Deliver a personal single-book learning trial for EPUB and selectable-text PDF.
   Explain selected English text in Vietnamese: translation, grammar, vocabulary,
   idioms and connections within the current book.
3. Resume offline backup/restore and note export, including the newly introduced
   durable records. Backup is postponed, not cancelled.

Library-wide questions, related books, spaced repetition, OCR, scanned-PDF
selection, TXT learning, fixed-layout EPUB annotations and paused readers remain
outside this trial. Existing PDF text selection still requires Android15+;
ordinary PDF reading remains available on Android12+.

## 2. Reader experience

Long-press and adjust the existing/native selection, then choose AI Translate.
The draft card shows the original quote and location, Vietnamese translation,
grammar explanation, vocabulary/idioms and an optional within-book context section.
Keep sections collapsible and allow a follow-up about this passage. The earlier
card mockup mentioned in the supplied conversation is not available here; this
layout is a proposal, not a claim to reproduce it.

Proposed persistence: save accepted answers beside the source highlight in Notes
for offline reading; add chosen words/phrases to a separate vocabulary list with
their meaning and source passage. Do not automatically save every intermediate
response or overwrite a handwritten note. Reopening a saved answer needs no
network. Label generated explanations distinctly from quoted source text.

Reading, selection and manual notes work without an API key or connection.
Offline requests explain that generation needs connectivity and offer saved
results. Loading, cancellation, quota exhaustion and retry retain the selected
passage and unsaved handwritten text.

## 3. Prepare a book once, reuse its context

Book preparation is an explicit per-book action, separate from the library's
local folder scan. Never upload the library automatically on scan/import/open.
Extract bounded text in reading order from a verified snapshot. Store a compact
book overview and section summaries with local chapter/page references, tied to
the source SHA, extraction version and preparation version.

“Once” means reusable preparation for unchanged content, not necessarily one API
request. Split long books into bounded sections and checkpoint completed work.
Interrupted or quota-limited preparation can resume without repeating accepted
sections. Partial coverage must be visible. A source revision invalidates its
derived context; it does not silently move saved notes onto different text.

For a passage question, send the selection, bounded surrounding text and relevant
summaries. Summaries are lossy: when a claim needs another passage, retrieve a
bounded original excerpt from this book and attach its location. If supporting
text is unavailable, say so instead of inventing a connection or citation. Offer
passage-only explanation while preparation is absent/incomplete. Treat book text
as source material, never as instructions to execute tools or disclose secrets.

The supplied conversation reports a 40-English-EPUB sample: median about100,000
words, p90 about242,000, maximum about349,000, with estimated135k/327k/471k tokens.
These are supplied estimates, not measurements reproduced in this session, model
tokenizer counts, or proof that every library book fits one request. Model input
limits, prompt/output headroom and the actual project's request/token quotas must
be checked before fixing chunk sizes. No free-tier latency or throughput promise.

## 4. Network boundary and optional desktop experiment

The shipped app currently has no INTERNET permission. The proposed AI trial needs
an explicit specification exception: online generation initiated by the owner,
while reading and saved data stay offline-capable. Keep Readium's OfflineHttpClient
and external-resource blocking; enabling AI must not enable EPUB remote assets.
No cloud sync, automatic book upload or hosted WindBook backend is proposed.

Gemini Flash/Flash-Lite are candidates from the discussion, not a pinned model.
The implementation plan must verify the current official API, model availability,
quotas and data-use terms before choosing one, and define credential storage,
bounded request sizes/timeouts, retry/backoff and redacted diagnostics. Never put
keys in source control, the APK, exported notes or backup archives. Any new Gradle
dependency still requires explicit owner approval.

An optional disposable desktop experiment can precede AI integration. It needs
separate agreement to send the selected book's text to Google, an owner-selected
book and a key supplied via an owner-managed file outside the repo. Do not search
unrelated files for credentials. Prepare a local extraction/payload preview first;
show which text coverage will be sent before the live run. Do not log the key,
private title or full text into tracked evidence.

Measure preparation time, actual reported token usage, retries/quota failures,
follow-up latency and the owner's assessment of Vietnamese explanations. Use
passages the owner chooses, including an idiom and a connection requiring source
evidence. Report results for that book/key/model only. A weak result should revise
the AI design without blocking useful offline EPUB notes.

## 5. Persistence and verification contracts

Preserve the October29 EPUB anchor contract: versioned locator, exact quote and
bounded context, pinned SHA, semantic decorations and exact-source revisit after
rotation/re-pagination. Use the additive v8→v9 annotation migration already
planned, preserving every PDF payload. Allocate subsequent AI schema versions
only in the implementation plan after confirming the actual database baseline.

Saved explanations and vocabulary are durable user data; derived preparation is
rebuildable. Keep model/prompt/context versions with saved answers and reject stale
session results. Extend later backup/export to round-trip both annotation kinds,
accepted answers and vocabulary, with source references; omit credentials.
The earlier plan D3 depends on backup/export that will now follow this work:
perform EPUB persistence/device/review gates first and defer only the backup and
export round-trip portion until that implementation exists.

Implementation remains TDD with mandatory CI, no added lint warnings, EN/VI UI
strings, synthetic fixtures, independent review and real API31/API36 device gates.
Cover native selection/handles, repeated quotes, restart, typography changes,
stale callbacks, cancellation/quota errors, offline access, migration preservation
and PDF/EPUB coexistence. Keep the existing renderer and source guards intact.

## 6. Approval checkpoint

- Approved: specification, card, saved-answer/vocabulary behavior and scoped network exception.
- Approved: optional desktop Google experiment; source folder supplied by owner.

The existing EPUB-note scope approval is retained. The owner explicitly requested the revised plan and implementation together;
see ../plans/2026-11-16-single-book-ai.md. Do not ask for the same approval again.
