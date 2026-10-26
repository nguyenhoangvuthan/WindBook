# Daily reading feedback verification

Scope: recursive root scans for EPUB/PDF/TXT, visible Settings defaults, stable title/time-added sorting, paged PDF defaults and PDF text annotations on API35+. No EPUB/scan notes, OCR/export or additional format readers.

## Focused evidence

- PDF default:6RED→9GREEN (portrait/mixed/empty defaultPAGED, explicit choices and saved zoom preserved).
- Folder/settings:41focusedtestsGREEN, plus compact selector10GREEN including2,376results/30savedroots regression. Invalid provider row preserves valid siblings with INCOMPLETE. Typed scan and picker callback retain the options visible at action time; exactgrant reuse cannot restore hidden stale filters.
- PDF notes: model/data/queue/app focused checks GREEN. API36 native selection extracts Vietnamese/multiline text, equivalent reversed endpoints, empty-space null, closes pages before render and retains pinned-source SHA. Full normalized anchors and bounded actor results are used; no all-page extraction.

## Integration gates (controller fills after actual observation)

| Gate | Result |
|---|---|
| Full mandatory JVM/lint and APK build | PASS:385tests,0failures/errors; lint0errors/10baseline warnings;0.1.1 APK built |
| Installed v6→v7 preserves books/progress/preferences/text history | PASS: exact row hashes retained (7,502books,5progress,0prefs,1TXT state); private backups only |
| Settings exposes three format choices and persists changes | PASS API36: EPUB/PDF/TXT and recursion remain checked after APK upgrade/relaunch |
| Typed root→system grant→automatic complete recursive scan | PASS API36: picker opens at typed device root; grant triggers complete43-row scan,24selected/16ignored,0errors |
| Rescan adds one new file; title and added-time ordering | PASS:44rows,25found,1new/24existing; newest shows ZZZ first, A–Z shows Book1 first |
| Open actual EPUB/PDF/TXT source, PDF startsPaged | PASS API36: all three opened directly; PDF starts in paged viewport |
| PDF text selection/handles/color/note popup, Save/Edit/Delete | PASS API36: Vietnamese word→multiline, reverse crossing,5color chooser, green highlight→note, Save/Edit/Delete confirmation |
| Restart retains notes; Notes reopens/repeats correct book/page/quote | PASS: force-stop/relaunch preserves2notes on pages1/2; edit/search, revisit1→2→1 with exact marks, delete reflected immediately |
| Crop/zoom/layout/rotation maintain anchor and visible status bar | PASS: paged and vertical marks; crop-enabled revisit temporarily fits full anchor;2.5× zoom/pan and landscape aligned; editor draft survives rotation; status bar visible |
| API31 reading unaffected, guarded text-selection capability | PASS final APK: same synthetic PDF renders, long-press explains Android15+ requirement, volume key still turns to page2 |
| Independent whole-change review and required fixes | Approved for b2e5910..ef3936b and reviewed narrow popup fix; actual handle RED→GREEN; no remaining Critical/Important finding |

All test documents are synthetic. Local source hashes, screenshots, window dumps, DB backups and logs live under ignored `.superpowers/checks/daily-feedback/`; no owner library writes or page images in git. Phone confirmation remains a separate owner check.

## Actual window regression and limits

On API36, long-press a word and drag the end handle outside the popup onto the next
line. Before the fix, Android delivered an outside-touch dismissal to the popup on
pointer down and cleared selection before the handle could drag. The actual adb
gate failed with missing handles. With outside-touch dismissal disabled, both
handles and two selected rectangles remain; tapping an unmarked page area still
clears selection. Dragging the start handle across the end also remains usable.
Robolectric alone did not model this window interaction, so retain this manual gate.

Verification logs: `full-ci-handle-fix.log` (385JVM tests,0failures/errors/skips;
lint0errors/10baseline warnings; APK build), `handle-gate-red.log`,
`handle-gate-green.log`, and `independent-review.md` under the ignored evidence
folder. An initial post-install attempt ran before the session was ready and failed
its setup assertion; the stable-session retry validates the drag itself.

The synthetic PDF SHA256 is unchanged before/after note CRUD. API36 PSS was
244,532KB after zoom/pan/rotation with a7,525-book synthetic library; this is an
observation, not a benchmark for large scanned books. Native extraction tests cover
Vietnamese/multiline/empty/reversed boundaries. Actual UI checks cover the fixture's
horizontal text in portrait/landscape; arbitrary publisher PDFs and the owner's
physical phone still require daily-use feedback. Raw selection may clear on rotation;
the editor draft survives rotation, but force-stop recovery of an unsaved draft is
not promised. Android12–14 text notes, EPUB notes, scan-region notes/OCR and export
remain outside this increment.
