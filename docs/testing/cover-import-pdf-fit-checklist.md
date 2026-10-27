# Cover import and PDF fit verification — 2026-10-27

Branch: `cover-import-pdf-fit`, baseline `2ae7699` (0.1.4). Design and plan approved.
Delivery target: debug 0.1.5/code6, main-checkout `publish/` (untracked).

## Verified behavior

- [x] SHA-256 computed during the bounded external snapshot copy, reused on its live
  lease. Owned sources still read actual bytes. Empty/unknown length, partial-copy
  cancellation, quotas/space, closed/cancelled lease and provider replacement pass.
- [x] API36 ExternalSourceDeviceTest:14 passed, including existing blocked pipes,
  single-open ownership and cleanup. No provider reopens, source changes or quotas
  bypassed. JVM hashed-stage contract RED before implementation.
- [x] Actual API36 cover corner regression RED→GREEN: portrait/square/narrow/
  landscape all show four colored corner markers, centered in stable2:3 frames.
  Square/narrow previously lost all four. Existing generated-cover/menu semantics
  and accessibility retained. No Crop-to-Fit state change after load.
- [x] PDF View menu offers Fit page / Vừa trang. One combined PAGED/crop=false
  write; repeatable reset token. Per-book vertical zoom/X and unknown JSON keys
  retained. Current locator and notes unchanged; successful fit hides reader HUD.
- [x] Actual PDF pixels: portrait aspect.75, landscape2, tall.25, all four corners
  visible; equal-size corner squares show no distortion. Tall-page rotation from
 1080×2400 to2400×1080 retains.25. Status bar remains visible.
- [x] Repeated zoom/drag→Fit resets the same tall page. Native VERTICAL→Fit keeps
  page2, saved vertical zoom2.5/X0 and the exact saved note record. Per-page native
  measurement precedes pixels/overlay, including delayed mixed-size unit fixture.
- [x] Native PDF text note saved; highlight plus note indicator stays at identical
  bounds [153,664,227,696] and1337 gold pixels after fit/restart. Anchor points to
  the original selected word, not the resized bitmap. Synthetic content only.

## Performance evidence and limits

Three alternating baseline/candidate trials use the same restored pre-import
emulator DB and source bytes:25 books,80,837,091bytes, including a24.49MB scan.
Existing SHA/cover rows are absent from each trial, so these are fresh imports.
Numbers below concern cover preparation, excluding discovery and parsing's own
I/O; timing diagnostics were removed from delivery sources.

| Metric | Baseline0.1.4 | Candidate |
|---|---:|---:|
| Extra snapshot bytes read for SHA |80,837,091|0|
| Cover-pass median |3515ms|3634ms|
| Cover-pass range |3460–3549ms|3500–3648ms|
| Metadata/cover extraction median sum |2817ms|2886ms|
| First-cover median (exploratory) |305ms|395ms|

No wall-time acceleration is demonstrated. Source iteration uses newly generated
IDs, so first-cover order varies; that metric is exploratory. Copy plus hash CPU
cost remains; parsing/rendering dominates. No parallel parser, lower-quality cover
or new extractor was added. These results do not predict full-library phone speed.
Post-pass PSS is sampled in detailed reports (not a peak benchmark): baseline median171784KB, candidate172213KB.
All25 covers publish successfully per trial; historical0.1.4 restart/rescan/reading
invariants are reused for unaffected paths.

Private manifests/DBs/screenshots and detailed timing/memory reports are ignored
under the active worktree `.superpowers/checks/cover-import-pdf-fit/`. JVM RED/GREEN,
required CI, source device tests, synthetic pixel scripts and review evidence live
there. Original app DB backup is restored after device testing; source books remain
read-only. Phone testing remains the owner's next step.

## Final release gate

- [x] Independent whole-branch review Approved, no Critical/Important. Minor loading/error tap recovery fixed with failing regression then GREEN; narrow follow-up Approved.
- [x] Final required CI417JVM, zero failures/errors/skips; lint0errors/9baseline warnings.
- [x] Final0.1.5 APK/signature/version/hash checked and exported with SHA-256 to publish/.
- [x] Original emulator library restored; source hashes and reading/notes data preserved.

Final evidence: `final-ci-build-reviewed.log` BUILD SUCCESSFUL (39s),417JVM
(55model/132data/51reader/179app),0failures/errors/skips; lint0errors/9baseline.
Final real smoke:25/25 bounded covers survive restart/rescan, exact book IDs/paths/
SHA/reading/notes/preferences unchanged, COMPLETE with0errors and empty workspace.
Original7600book rows and all table row hashes restored;57 referenced original
cover files intact.178 known test covers and the duplicate large sample retired.

Delivered APK: `publish/WindBook-0.1.5-cover-fit-debug.apk`,80,261,071bytes,
SHA256 `d94a5c622a288693a21c3af1d2f12bcedffe23660827a29ce17d3a13d5550aa2`.
Package dev.kent.windbook, code6/name0.1.5; signature matches0.1.4.
No main merge, remote push or tag push for this increment.
