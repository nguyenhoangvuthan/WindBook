# Remove PDF Reflow

Date: 2026-10-20
Scope: the owner's explicit request to remove Reflow.

## Result

PDF reading offers Vertical scroll and Page by page. Remove the Reflow menu
choice, its A−/A+ controls and pinch-to-step behavior, page analysis cache,
word/line/figure analysis and bitmap rearrangement.

Keep PDF import, margin cropping, zoom, horizontal position, volume-key
navigation and the existing locator format.

## Existing books

Decode a saved `pdfLayout: "REFLOW"` as `VERTICAL`, including landscape books.
This fallback is an implementation choice to provide one predictable
replacement for the removed mode. Preserve reading progress, crop and zoom
preferences. A later preference write stores the supported layout.

The old `pdfReflowSize` key is inert and follows the codec's existing
unknown-key preservation policy. No database migration or book reimport is
needed.

## Implementation and verification

1. Add failing regression tests for legacy preferences, reopening at the saved
   page, and the remaining menu choices.
2. Remove the UI and analysis pipeline, their exclusive fixtures/tests, and
   English/Vietnamese strings. Retain the luminance thread regression test
   because cropping still uses that renderer path.
3. Run the repository JVM/lint gate, build the APK, run reader device tests and
   inspect the menu and remaining layouts on `Neo3GT_API_36`.
4. Independently review the change and update the roadmap, checklist and
   `AGENTS.md`. Keep the work on its branch for the owner's merge decision.

Historical PDF comfort documents remain as records; this decision supersedes
their Reflow requirements. Notes, typography and folder import remain separate
brainstorming work.
