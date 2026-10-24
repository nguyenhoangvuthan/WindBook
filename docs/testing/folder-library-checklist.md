# Folder library verification

This increment discovers EPUB, PDF, MOBI, AZW3, FB2, CBZ, CBR and TXT through a read-only Android folder grant. EPUB/PDF/TXT reading is implemented and reviewed. On 2026-10-24 the owner paused the five other readers in `2026-10-21-ebook-format-readers.md`; discovery of those formats does not enable reading.

Use synthetic files only for recorded evidence. Never modify the owner's external library. Each source must retain its bytes after scanning, reading, disconnecting and reconnecting.

| Check | Expected result | Evidence / status |
|---|---|---|
| Choose a folder through Android | Actual returned read grant; typed paths alone cannot scan | Task 7 API36 / Task 8 API31+36 real picker; FolderScreenTest |
| Recursive first/second scan, 2,500 files | 2,500 matches; first adds 2,500, next knows 2,500; no duplicate identities | Task 8 API31 first4.694s/second4.882s; API36 clean first5.754s/second6.091s |
| Metadata-only discovery | No source-content opens while scanning | API36 real SAF directory watcher + file-read positive control:0 content opens; API31 counting protocol-provider probe:0 opens/positive1 |
| Stop during enumeration | Durable Interrupted; cursor closes; unseen existing files remain present | Task 7 API 36 Stop 263 ms; Task 8 API 31 Stop to durable Interrupted 150 ms; FolderScanCoordinatorTest cancellation and partial-batch cases |
| Hide and rescan / explicit Restore | Hidden book stays hidden until explicit action | FolderScanCoordinatorTest repeated/overlapping case; FolderScreenTest |
| EPUB/PDF from folder results | Existing stable book ID opens; source remains outside permanent library storage | Task 7 API 36 rendered synthetic EPUB/PDF; Task 5 source acquisition device tests |
| Position, restart, exact-content reconnect | Verified same bytes retain position; changed bytes require explicit new revision | ReaderViewModelTest verifiedSnapshot/reconnectSameBytes/changedBytes; Task 6 real API 36 SAF reconnect/revision evidence |
| Imported-book regression | Owned files still open directly; import dedupe and v1/v2 migration preserve records | ReaderViewModelTest; BookImporterTest; FolderMigrationTest; RevisionMigrationTest |
| Revoked / detached source | Access message; no stale locator applied; other healthy sources can open | ReaderViewModelTest deniedSource; Task 5 synthetic provider-denial device gate; Task 8 API31/API36 actual persisted revoke and synthetic detach both incomplete/errors1, retained entries; picker regrant/restore rescanned2500 |
| Full temporary workspace / blocked open cancellation | Bounded failure, responsive Main, no leaked descriptors or staged files | Task 5 API 31/36 ExternalSourceDeviceTest and SourceCleanupDeviceTest |
| Scan FD / temporary files / PSS / responsiveness | No retained session resources or >1 s UI freeze | Task 8 API31 max83ms/API36 max175ms; temp empty8KiB; extra FD targets are Room DB/WAL pool; PDF cleanup settles to empty; API36 warm FD159→162 active→159 settled |
| Offline and source integrity | No INTERNET permission or persisted write grant; source checksums identical | Manifest/grant tests; Task 8 SHA256 manifests: all2500 synthetic files unchanged onAPI31/API36 |
| Narrow screen / landscape / 150% font | Controls remain scrollable and usable | Task 7 API 36 screenshots |
| Accessibility | Native controls accept actual accessibility focus with TalkBack bound | Task 7 instrumentation; speech and directional gesture order not verified |

Required automated command:

```bash
./gradlew :core:model:test :core:data:testDebugUnitTest :reader:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug :reader:connectedDebugAndroidTest :app:assembleDebug
```

Do not mark the increment complete before the independent whole-branch review and any explicitly remaining device gates in the Task 8 handoff. Do not mark all-format reading complete before the companion plan.
