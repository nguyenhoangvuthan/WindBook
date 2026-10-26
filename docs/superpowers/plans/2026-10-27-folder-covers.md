# Folder covers — owner-requested correction

The owner requests covers on the shelf immediately after folder import. Current
folder discovery writes filename placeholders; only ReaderViewModel extracts cover
metadata on first open. Reuse that bounded offline EPUB/PDF extractor during scan,
without opening a reading session or changing progress. EPUB uses embedded cover;
PDF uses the existing at-most600px first-page renderer. TXT/no-cover/corrupt files
retain their fallback. No new dependency, permission or schema migration.

Implementation decisions: discovery commits first; a single cancellable scan-owned
pass visits source IDs in bounded keyset pages. Keep scan RUNNING through cover
preparation and show a localized cover phase. Stop/new scan/disconnect invalidate
publication; old uncooperative native work cannot write late metadata. A scan retry
fills old missing covers, skips valid cached covers only with known unchanged source
stamps, and keeps original source files read-only. File snapshots use SourceAccess
leases; close each before the next. Revision/dedup policy remains authoritative;
metadata/cover commit is atomically guarded by run, membership, source and current
book identity. Unique cover paths avoid stale image cache and concurrent overwrite.

- [x] RED/GREEN core candidates, atomic guarded metadata/revision commit, stamp
      invalidation; unchanged reading data, hidden books, stopped/disconnected scans.
- [x] RED/GREEN scan phase ordering/cancellation; bounded sequential app loader,
      source lease/fingerprint checks, cache reuse, bad-file continuation and cleanup.
- [x] Wire DI and bilingual phase label; build0.1.4/code5. Test with fresh real
      EPUB/PDF source candidates, without opening them first: covers persist on shelf
      and after restart/rescan, source hashes unchanged, one active snapshot at a time.
- [x] Required full CI/lint/APK; actual API36 visual/memory checks; independent review
      and required fixes. Update AGENTS/checkpoint/checklist; local commit and export
      verified APK/checksum to main-checkout publish/. No merge/push.

Review rulings: cover preparation preserves initial candidate IDs even for duplicate
bytes; foreground verification retains its explicit dedup redirect. Changed known
revisions still preserve old reading data. Newly generated folder-* cover paths are
unique and never reused; replacements retire only unreferenced app-owned folder
images (including hidden/revision references). Readers reuse already verified valid
covers, avoiding re-extraction and orphaning a folder image on first open.
