# Minimal library feedback implementation

Approved scope: owner's three requested changes and explicit cover-only reply.
One implementer and the AGENTS independent whole-change reviewer. TDD for changed
navigation/gesture contracts and decode bounds; current green suites are reused
until changes require a final mandatory run. No push/dependency changes.

1. Add-books dialog on populated/empty libraries, EN/VI options. A distinct
   FolderKey flag requests a new folder once through FolderViewModel's existing
   picker ownership; tests cover file/folder callback choice and recreation.
2. Cover-only Library grid/list/continue card and Reading shelf, generic no-cover
   outline, stable image request and accessible labels; preserve click/menu/status
   and delete actions. Focused Compose tests for no text and correct book IDs.
3. Minimal controlled slider. Reproduce external-position/draft-cancel conflicts;
   verify one release, semantics, first/last and idle external updates. Make the
   active locator an observed state rather than relying on incidental recompose.
4. Shared bounded raster cover decode; read the canonical cover resource parsed
   by Readium, bounded compressed bytes and target bitmap. Native tests include
   fallback EPUB with large raster, malformed/oversize and non-square artwork.
   Compare same-fixture baseline/candidate decode timing and memory observations.
5. Actual API31/36 Add-folder picker/cancel/grant, cover-only shelves, PDF/EPUB/TXT
   page/slider changes, EN/VI and large fonts. Independent review and consolidated
   fixes; mandatory CI/lint/build. Update checklist, roadmap, AGENTS8/9 and ledger;
   local commit and publish/ APK+SHA256. Keep signing compatibility limits explicit.
