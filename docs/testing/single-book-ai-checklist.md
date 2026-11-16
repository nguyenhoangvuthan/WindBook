# Single-book learning trial verification

Date: 2026-11-16. Branch: single-book-ai. Delivery candidate0.1.9/code10; see the evidence and manual limits below. Evidence: `.superpowers/checks/single-book-ai/`.

- [x] EPUB typed anchors, SHA validation, unknown kind readable/unrevealable.
- [x] Additive v8→v9 annotation kind and v9→v10 learning tables; legacy PDF bytes
  and prior durable rows preserved by migration regressions.
- [x] Accepted answers do not replace handwritten body, repeat acceptance/word save
  is idempotent, stale source does not create durable records.
- [x] Request/output bounds, quota error mapping, no automatic provider/model retry.
- [x] Missing key retains selection; stale cancelled response cannot replace it.
- [x] Recreation retains answer; follow-ups reuse the source annotation and have a
  separately enabled Save state for each response.
- [x] Bodyless EPUB highlight can retain ID/anchor while changing color.
- [x] Valid URI-encoded resource names accepted; traversal rejected.
- [x] Python extractor tests: spine order, script exclusion, unsafe ZIP paths,
  bounded chunk coverage, truncated response rejected.
- [x] Initial API36 semantic selection/clear/revisit native test before exact
  decoration change (one test, `integration-first.log`).
- [x] Exact range decoration for repeated passage, tap bridge and overlay rendering
  native gate passes on API36 and API31. Actual legacy-WebView fallback selection
  was simulated on current WebView, not tested on a historical WebView binary.
- [x] Actual MainActivity EPUB long-press/native action menu → AI card → real Gemini
  HTTP200 → accepted answer/vocabulary persisted (one row each). Card survives
  rotation; saved explanation and vocabulary work offline after force-stop/restart.
- [ ] Actual MainActivity PDF AI workflow, repeated EPUB highlight edit, and full
  EN/VI large-font matrix remain manual follow-ups; native/PDF regression suite passes.
- [x] API31 image installed in isolated AVD: compatible native52 tests pass; API36
  full55 tests pass. API35-only PDF selection class excluded on API31.
- [x] Mandatory CI:502 JVM(66/145/78/213),0 failures/errors/skips; lint0errors/13
  baseline warnings after fixing two new PluralsCandidate messages.
- [x] Independent narrow source rereview Approved (`rereview-final.md`); six Important
  findings fixed. Final APK/checksum export recorded in AGENTS.

## Live Gemini experiment

The owner approved Google transmission and supplied a read-only trial folder and
external key file. One English EPUB was selected locally; no private title, path,
content or key is committed. Model-list authentication succeeded; 2.5-flash-lite
returned404 on generation; 3.5-flash-lite successfully answered the synthetic
idiom trial in2.98s/86tokens. No paid model fallback or billing change.

Real sample: approximately9.8k words /52,182 extracted characters, five chunks
of at most12,000characters. Preparation:5 successful requests,23.38s summed API
latency,14,443 reported total tokens. One passage follow-up:6.361s,3,058tokens.
Raw private evidence is ignored. This small-book result is not representative
throughput for100k-word books, a free-quota guarantee, or owner quality approval.

## Implementation rulings and outstanding limits

Readium3.4.0's standard decoration resolver ignores WindBook's offset extension.
Exact EPUB highlights therefore use the same validated semantic range resolver as
revisit, with CSS Custom Highlight when supported and noninteractive rectangle
overlays otherwise. Source files remain unchanged; rendered DOM receives only
presentation nodes. This replaces the planned Readium decoration rendering path
to avoid marking the first identical occurrence. Native exact-range/bridge/overlay verification passes on API31/36.

Rotation cancels in-flight generation while retaining answer/question and accepted
preparation checkpoints; retry is explicit. Long preparation resumes accepted
section summaries; interrupted overview aggregation may repeat that derived work.
Relevance uses bounded summaries/excerpts and can be weak when English selections
are compared with Vietnamese summaries; unsupported connections must be stated as
uncertain. Backup/export integration follows this increment and is not implemented.

The initial follow-up/rotation regressions had a failing test before fixes. Some
later review fixes were applied before their behavioral regressions were run;
those do not have a proven behavioral RED observation. The concurrent Gradle
attempt caused a transient incremental-cache failure and one lint-analysis crash;
subsequent sequential mandatory CI passed. Key entry on the device was also
verified: app-private82-byte encrypted file, no plaintext key; after correcting ADB
entry/save timing the decrypted key fingerprint matched the desktop key and a real
Android request returned200. Temporary diagnostics were removed before delivery. Do not count those infrastructure
failures as behavioral RED evidence.

## Main integration

On2026-11-16 the owner authorized "push to main". Main fast-forwarded from
b17c541 through620fc6a without changing tested application content. Existing green
checks apply; manual follow-ups above remain open. Main-only remote equality
verification is recorded in ignored evidence `main-push.txt`; APK/key/private
trial evidence are excluded from git.
