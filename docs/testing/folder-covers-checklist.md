# Folder cover verification — 2026-10-27

The owner requests persistent shelf covers during folder import, before opening
books. Previous discovery only inserted filename placeholders; first reader open
performed metadata extraction. New scans retain the discovered entries, prepare
EPUB/PDF covers serially, then report completion. Existing folders can rescan to
fill missing covers. Embedded EPUB images and bounded PDF first pages are saved;
TXT, absent covers or optional extraction failures keep the existing title fallback.

## Behavior and regression evidence

- Core repository: 10 tests cover bounded active-run candidates, hidden/stopped/
  disconnected/stale rejection, metadata without marking opened, stable duplicate
  IDs until foreground redirect, changed revisions retaining old progress/covers,
  and source-stamp invalidation. Initial6RED→10GREEN, then review regression RED/GREEN.
- Coordinator: 2 new tests require discovery before cover preparation, completion
  afterward, visible cancellable cover phase and durable stop invalidation.
- App loader: 10 tests cover serial bounded snapshots, lease closure, cached/missing/
  unknown-stamp covers, bad-file continuation, provider changes, hidden/stopped late
  results, replacement cleanup and preservation of hidden/revision references.
- Reader: new regression requires reuse of an already verified cover without
  re-extraction or replacing its path. Existing source verification still precedes it.
- Mandatory final CI:408JVM tests (55model/132data/47reader/174app),0failures/errors;
  lint0errors/9unchanged warnings;0.1.4 debug APK builds. No migration/dependency added.

## Independent review and consolidated fix pass

Two Important findings were reproduced before fixing: background dedup could
retire a candidate ID already captured by a pending reader; repeated cover writes
could leave superseded image files. Background preparation now preserves initial
candidate IDs, leaving foreground dedup explicit. Publication returns the replaced
cover path; only unique loader-owned folder-* images with no book reference are
retired. Hidden/revision rows count as references. Readers reuse valid covers after
SHA verification. Three regression failures were observed, then GREEN; final scoped
re-review Approved with no remaining Important/Critical finding.

## Actual API36 verification

Read-only real-library fixtures: 24 distinct new books (12EPUB/12PDF,56.35MB) not
previously imported or opened. Private source mappings and page images stay ignored.

| Gate | Observation |
|---|---|
| Folder grant and scan |24checked,24found,24new,0discovery errors,COMPLETE |
| Cover before reader open |24saved WebP covers:12EPUB/12PDF; all dimensions≤600px |
| No reading side effects |All24 TO_READ, lastOpened=null, no progress rows before explicit open |
| Shelf and restart |Actual grid displays cover images; force-stop/relaunch retains them |
| Rescan/cache |24stable book IDs, hashes and cover paths; encoded sizes unchanged |
| Existing folder backfill |Old PDF covers1→8;25existing IDs/lastOpened/locators/progression preserved |
| Missing embedded image |8tiny legacy EPUB fixtures retain their title fallback; TXT unaffected |
| Reader after scan |One new EPUB opens; all24cover paths unchanged after returning |
| Cover-file ownership |31loader-owned files match31distinct referenced paths; no unreferenced replacement remains |
| Source preservation |All24host originals and device copies retain their SHA-256 |
| Temporary preparation |Reader-sessions directory empty after the pass |

Recorded scan durations:3,750ms first pass,299ms cached rescan, measured from the
persisted run start to completion on this fixture/emulator. Settled shelf PSS after
restart was139,141KB. These are observations, not a whole-library or large-scan
benchmark. No new platform API is used; prior API31 source/renderer evidence is
reused. Phone confirmation remains an owner check.

Evidence: ignored `.superpowers/checks/folder-covers/`, including full-ci-final.log,
review-fixes-red.log/review-fixes-green.log, review-final.md, private DB snapshots,
verification-summary.json, actual shelf screenshots and final-apk.json.

Delivery: main-checkout `publish/WindBook-0.1.4-folder-covers-debug.apk`, versionCode5,
versionName0.1.4; checksum/signature/package version verified. No merge or push.
