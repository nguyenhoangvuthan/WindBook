# Cover speed verification — 2026-10-29

Baseline: application0.1.5, planning commit1113d9e. Branch: cover-speed.
Approved scope: October29 reading-priorities design/plan, increment A.
Status: synthetic benchmark and reviewed code delivered cumulatively in0.1.7;
no separate0.1.6 APK. Main reading-controls checklist records final CI/build.

## Correctness and resource gates

- [x] First cover commits before a delayed final directory completes discovery.
  Regression failed before the progressive worker change.
- [x] The queue holds at most64 IDs; a consumer processes at most16 IDs per
  database batch, one snapshot/extractor at a time.140-item delivery retains every
  ID; normal, no-cover and unknown-stamp140-book regressions pass.
- [x] Stop with a blocked producer and noncooperative native consumer invalidates
  the run before join; no late cover or metadata commit, no leaked source lease.
- [x] Final sweep recovers a candidate changed during progressive preparation.
  A later row change invalidates the exact post-commit attempt proof.
- [x] Run-local attempt index uses the Android SDK SQLite API, separate from the
  durable Room DB:8MiB disk maximum,256KiB page cache, exact candidate signatures.
  It is retired on completion/cancellation and rebuilt after process interruption.
  Exhaustion conservatively repeats optional work rather than skipping candidates.
- [x] EPUB2/3 supported raster metadata, Unicode and escaped paths pass. Global
  Dublin Core metadata, contributors, unsupported structures/images and missing
  canonical spine use Readium. Unsafe archives remain rejected before routing.
- [x] Decoder requests a bounded proportional bitmap before output encoding;
  WebP quality80 and600px maximum remain. Native large-raster corner and JPEG
  EXIF-orientation checks pass; input source bytes remain unchanged.
- [x] Text-only archives skip the extra fast-path XML parse and retain Readium's
  metadata/validation behavior. This optimization changes routing only.
- [x] Cover progress shows no denominator until discovery closes; EN/VI strings
  and plural handling are present. Focused Compose regressions pass.
- [x] Independent whole review found3 Important issues. All received failing
  regressions and fixes; related contributor/canonical-parent cases also fixed.
  Narrow rereviews approved the final code and subsequent batching/routing changes.

## CI and lint

- [x] Prior full candidate CI passed440 JVM tests (55/132/65/188), no failures,
  errors or skips, before the last two overhead regressions were added.
- [x] Focused final overhead suites and app/test APK builds pass.
- [x] Final cumulative CI473 JVM(63/137/74/199),0 failures/errors/skips.
- [x] Same-environment original0.1.5 lint and candidate lint both report11 warnings,
  zero errors, with exactly equal issue IDs/severities/messages. Two additional
  tool/version notices relative to October27's historical9 are not caused by this
  patch. No suppression or dependency upgrade was used. Final current-environment baseline/candidate each13 warnings/0 errors; the
  online Gradle-new-version text differs, issue IDs/counts remain equal.

## Device and performance evidence

Devices: initial API37 exploratory run; final repo-local Neo3GT_API_36 AVD,
1080×2400,420dpi,4096MB configured RAM, SwiftShader. No physical-phone claim.

- [x] API36 native cover/source suite:21 passed before the overhead routing change,
  including full-extractor missing-spine rejection and contributor-author parity.
- [x] Final affected API36 native suite50 passed, including cover/source gates.
- [x] Initial extractor-only24-book5-pair observations retained separately. They
  did not include coordinator/Room/final sweep and cannot establish scan speed.
- [x] Actual UI/SAF240-EPUB corpus includes100 raster-cover books and140 no-cover
  books. Fresh DB seed retains the real folder grant/record; baseline/candidate
  installs use the same source bytes. Read-only app-UID diagnostics read run and
  publication timestamps from the live Room database.
- [x] First full-scan candidate FAILED the total-time gate:5-pair medians, fresh
  7021→8500ms; cached4068→5121ms; first cover1244→395ms. This identified extra
  lookup/progress/XML overhead. Raw failed measurements remain retained.
- [x] Five alternating full-scan pairs after overhead correction. First-cover
  median1218→454ms (baseline1091–1327, candidate361–673); fresh scan7752→6804ms
  (7227–8660 versus6345–8244); cached4063→3698ms (3501–4395 versus3388–4502).
  Every run retains240 books/100 covers, COMPLETE and0 errors. Raw results:
  `.superpowers/checks/cover-speed/full-scan/results.json`.
- [x] Baseline/candidate actual SAF scan/rescan and persisted covers are recorded
  in matched trials. Final upgrade preserves all240 original books/sources,240
  memberships, folder and4 scan rows. These synthetic originals have no reading
  rows; nonempty durable-data preservation is covered by migration regressions.
- [ ] Physical-phone scan/Stop/retry and real-library comparison remain manual
  follow-ups; cancellation/retry source and coordinator regressions pass.

Library drives documented by the project are absent in this checkout. Owner was
asked for a mounted path; real-library comparison remains unavailable until the
source is present. Synthetic results must not be described as owner-library or
phone measurements. Debug tooling/DB snapshots/screenshots remain ignored.

## Delivery

- [x] Cumulative0.1.7/code8 APK signature/hash/size verified; APK and checksum
  exported to main-root publish/. See reading-controls-checklist.md.
- [x] README/roadmap and AGENTS8/9 updated; backup/export is next.

The original delivered0.1.5 APK and signing key were not present here. This session
created a debug keystore; its compatibility with the owner's installed build is
not established. Owner was asked for the old key path. A same-environment control
APK is not evidence of compatibility with the previously delivered phone APK.
