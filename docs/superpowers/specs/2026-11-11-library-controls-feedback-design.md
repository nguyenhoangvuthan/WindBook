# Library and reader controls feedback — 2026-11-11

Owner request: expose folder selection under Add books; show only cover art on
Library and Reading; make the position slider minimal and follow page changes.
The owner explicitly confirmed the cover-only scope on both tabs. This directly
requested correction takes priority over the next backup/export increment.

- Add books opens a choice: select EPUB/PDF files or select a book folder. Folder
  choice opens Android's tree picker immediately using the existing grant/scan
  flow and visible/default format options. Cancellation leaves existing folders
  and the library untouched; rotation must not relaunch an active picker.
- Library grid/list, continue-reading feature and Reading tab omit title/author
  captions. Keep metadata for search/sort and accessible book labels; the More button
  accessible label and delete confirmation still identify the selected book. Missing/error covers show a
  neutral book outline, never a repeated visible title. Keep full-cover Fit.
- Reader position follows the latest visible locator while idle. A thin track and
  small round thumb retain a48dp interaction area and accessibility SetProgress.
  Drag preview does not navigate; release submits one jump. Parent updates do not
  overwrite an active gesture; cancellation resumes the newest actual position.
- Bound raster decode for Readium-parsed EPUB covers before bitmap allocation,
  sharing the existing600px maximum/WebP80 encoder with the fast path. Preserve
  parser metadata, source identity guards, serial work and PDF framework path.
  Do not claim universal optimal speed; measure the affected large-cover path.
- No new dependency, database migration, source-file mutation or paused format.
  Export cumulative0.1.8/code9 APK and checksum to main-root publish/.
