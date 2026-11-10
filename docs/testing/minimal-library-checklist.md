# Minimal library feedback verification — 2026-11-11

Delivered0.1.8/code9 on minimal-library, based on0.1.7/db6ee0e. Scope is the
owner's Add-folder/cover-only/minimal-slider request and explicit confirmation
that cover-only applies to Library and Reading. DB remains8; no new dependency.

## Functional and regression evidence

- [x] Add books opens files/folder choice on empty and populated libraries; cancel
  dispatches neither action. Files retains the existing multi-document importer.
  Folder opens the Android tree picker directly, using the existing grant/scan
  implementation. No Settings path entry is required.
- [x] Independent review found one Important cold-entry race: picker creation used
  placeholder scan defaults before persisted settings arrived. Behavioral RED
  demonstrates EPUB/PDF+recursive instead of saved PDF-only/nonrecursive choices.
  The fix waits for initialized defaults, retains explicit click-time options,
  and rejects changed selection tokens across the wait. A second RED demonstrated
  a location edit opening a stale picker. Full focused GREEN and narrow approval
  are retained in `.superpowers/checks/minimal-library/`.
- [x] Library grid/list, continue feature and Reading omit title/author captions.
  Metadata remains available for search, sort and accessible book names. More and
  delete retain book identification; status/open/continue actions still work.
  Missing, loading and invalid art uses a neutral book outline. Covers retain Fit.
- [x] Slider has a2dp track and12dp round thumb,48dp minimum interaction layout,
  with Material accessibility semantics. It follows observed locators, including
  same-coarse-progress anchor changes, and uses matching first/last seek endpoints.
  Drag preview produces no jump; release commits once; cancel returns to the newest
  actual position. Session/layout changes dispose the old draft. History return
  uses a compact icon; PDF page entry remains reachable.
- [x] Shared raster decoder bounds the actual bitmap before allocation for both
  the EPUB fast path and canonical Readium fallback. Canonical cover resources are
  closed; cancellation propagates; bad/over16MiB compressed covers remain optional.
  Metadata,600px maximum, WebP80, serial source work and PDF framework rendering
  remain unchanged. Native cases cover canonical metadata, corruption, size limit,
  non-square corners and the existing EXIF/orientation regressions.

## Final checks

- [x] Mandatory CI484 JVM(63 model/137 data/76 reader/208 app), no failures/errors/
  skips. Lint0 errors and13 existing same-environment warning IDs/counts. Removed
  obsolete EN/VI finished-caption resources rather than suppressing the new unused
  warning caused by removing captions.
- [x] API36 full native54/54. API31 compatible native51/51, excluding the existing
  API35-only framework text-selection class. Full native runs include original
  source/cover/reader/selection and huge-PDF safety regressions.
- [x] Actual API31/36 files/folder choices and system tree grant; API36 cold picker
  cancellation/rotation and source scan. Initial saved defaults scan2 EPUB/PDF;
  choosing All in the folder screen then rescans3 EPUB/PDF/TXT,0 errors.
- [x] Before further reading tests, upgrade/new-folder scan preserves every original
  row:242 books/sources,2 progress,2 prefs,1 bookmark,1 contents cache,240 memberships,
  1 folder and4 runs. Three new books/sources/memberships and the new folder/runs
  are additive. Original rows match exactly; private snapshots stay ignored.
- [x] Actual shelf/Reading covers and PDF pages1→2→3; slider release reaches last
  page and a subsequent backward page turn updates the thumb. EPUB paging moves
  the slider from0% to20%; TXT paging and appearance checks are retained as UI
  evidence. English and Vietnamese Add choices inspected, including large fonts.
- [x] APK signature/version/ABIs/permissions/hash checked; signer matches the
  previously exported0.1.7 exactly. Four ABIs; min31/target36; no INTERNET.

## Measured cover path, with limits

Synthetic4000×6000 PNG cover whose metadata requires the canonical parser. Five
paired observations compare the old full-bitmap API then the bounded path in the
same resource/session; decode+WebP encode and a PSS sample are timed. Median214ms
(range209–565) versus151ms (148–158). Decoded bitmap allocation96,000,000 versus
960,000 bytes; every encoded output is1208 bytes. Pixel/corner checks pass.

This isolates the affected large-cover path. It is not a whole-folder scan result,
not a physical-phone/owner-library measurement, and the fixed legacy-first order
can favor the second cached read. No universal fastest-performance claim is made.
The earlier240-book whole-scan observations remain separate in cover-speed-checklist.
One live TXT-reader process PSS sample is206269KiB, not a comparative memory result.

## Delivery and remaining manual follow-ups

`publish/WindBook-0.1.8-minimal-library-debug.apk`,81,652,970 bytes, adjacent SHA256:
`8ccabbf3906124b34483efe3765314b13367dbfef5713f9f39e854a4f4e41de2`.
Delivery files remain untracked. Independent review and consolidated fix rereview
are Approved; no unresolved Critical/Important in reviewed changes. No push.

Physical-phone validation and real-library scan timing remain follow-ups. Backup/
export, EPUB notes, paused formats and PDF full-text search are outside this fix.
