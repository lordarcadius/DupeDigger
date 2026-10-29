# Architecture

This is the target design. Where the code and this doc disagree, fix one of them in the same change.

## Package layout (single `:app` module)

```
com.vipuljha.dupedigger
├── DupeDiggerApp.kt            @HiltAndroidApp, Timber, WorkManager config
├── MainActivity.kt             edge-to-edge, theme, NavHost
├── core/
│   ├── common/                 Outcome, DomainError, runSuspendCatching, dispatcher qualifiers
│   ├── mvi/                    MviViewModel, UiState/UiIntent/UiEffect, ObserveAsEvents
│   ├── ui/                     UiText, ContentState, shared components, formatters
│   └── designsystem/           theme (color, type, shape, motion), M3 Expressive wrappers
├── domain/
│   ├── model/                  FileItem, MediaKind, DuplicateGroup, MatchTier, ScanScope, ScanProgress…
│   ├── repository/             interfaces: FileIndexRepository, DuplicateRepository, ScanRepository,
│   │                           EmbeddingModelRepository, SettingsRepository, QuarantineRepository
│   ├── detector/               pure algorithms: hamming, simhash, cosine, grouping (union-find)
│   └── usecase/                StartScan, ObserveGroups, SuggestKeeper, DeleteDuplicates, …
├── data/
│   ├── local/db/               Room DB, entities, DAOs, migrations
│   ├── local/prefs/            DataStore
│   ├── storage/                MediaStore + SAF + File walkers, StorageAccess, deletion
│   ├── hashing/                partial/full hash, image dHash/pHash, video keyframes, text extraction
│   ├── ai/                     LiteRT-LM EmbeddingEngine wrapper, model downloader
│   ├── worker/                 ScanWorker, ModelDownloadWorker (@HiltWorker)
│   └── repository/             implementations + mappers
├── di/                         Hilt modules (Database, DataStore, Dispatchers, Storage, Ai, Bindings)
└── presentation/
    ├── navigation/             Nav keys, NavDisplay host
    ├── onboarding/             permissions + storage access explainer
    ├── home/                   storage summary, quick scan cards
    ├── scan/                   scope + tier picker, live progress
    ├── results/                group list by category/tier, filters, bulk select
    ├── group/                  group detail, compare, keep/delete
    ├── quarantine/             undo / permanently delete
    └── settings/               thresholds, AI model management, exclusions, theme
```

Each screen package holds `XxxRoute.kt`, `XxxScreen.kt`, `XxxContract.kt` (State/Intent/Effect), `XxxViewModel.kt` and `XxxUiModel.kt`.

## Detection pipeline

A scan is a WorkManager job (`ScanWorker`, a foreground worker with progress). It runs in stages, and every stage is resumable and incremental.

```
1. Enumerate  ── walk the scope (MediaStore / SAF tree / File walk) → upsert into file_index
                 key: (volume, relativePath+name) or documentId; skip if size+mtime unchanged
2. Exact      ── group by size (drop unique sizes; drop files < minSize)
                 → partial hash: first 16 KiB + last 16 KiB + size
                 → full streaming hash (SHA-256, per D-005)
                 → groups of identical full hash  ⇒ MatchTier.EXACT
3. Near       ── images: decode a downscaled bitmap → dHash + pHash (64-bit)
                 video:  8 evenly spaced frames via MediaMetadataRetriever → per-frame pHash + duration
                 audio:  duration + title/artist tags (file name when untagged); acoustic matching is an AI-tier concern
                 docs:   extract text (PDF, txt, docx) → 64-bit SimHash
                 → multi-index hashing (banding, D-012) to find pairs within the Hamming threshold
                 → union-find into groups  ⇒ MatchTier.NEAR
4. Semantic   ── (opt-in, model downloaded) EmbeddingGemma → L2-normalised vector
                 → brute-force cosine within each MediaKind bucket (ANN only if measured too slow)
                 → pairs ≥ threshold → union-find  ⇒ MatchTier.SEMANTIC
5. Rank       ── SuggestKeeper picks the file to keep in each group:
                 highest resolution/bitrate > original location (DCIM over WhatsApp/Download) > oldest
```

Rules:
- Each stage only processes files whose cached value is missing or stale (size/mtime changed).
- A file can appear in groups of several tiers. The UI shows the strongest tier first.
- Thresholds live in DataStore and are applied at query time where possible, so changing them doesn't force a rehash.
- Every stage checks `isStopped` / `ensureActive()` between files.
- Enumeration (`FileEnumerator`) is one cold `Flow` per scope:
  - WholeDevice walks every storage volume with `java.io.File`, iteratively and skipping symlinks.
  - MediaOnly is a single MediaStore cursor over images, video and audio.
  - Folders is a breadth-first `DocumentsContract` walk of each picked tree.

  All three apply the same `ScanFilter`: user exclusions, `Android/data` and `Android/obb` on every volume, and hidden entries when that setting is on.
- Indexing (`IndexFilesUseCase` → `FileIndexRepository`) writes 500 files per transaction:
  - an unchanged file (same size and mtime) keeps its row id and every cached hash and embedding;
  - a changed file is updated in place, and its `file_hash`, `perceptual_hash` and `embedding` rows are deleted;
  - unseen rows are pruned only after a completed enumeration, and only within the scanned scope's `sourceUri` prefixes (`file:`, `content://media/`, or each picked tree), so switching scope keeps the other scopes' cache.
- Later stages consider only files with `lastSeenScanId` equal to the current scan.
- The exact stage (`FindExactDuplicatesUseCase` + `ContentHasher`) buckets by size, then by (size, partial hash), then by (size, full hash):
  - four files hash at once, on `@DefaultDispatcher`;
  - a file of at most 32 KiB is read once and gets both hashes;
  - a file whose length no longer matches the index is skipped, never matched;
  - partial hashes are saved per 500 files, and each full hash as soon as it is computed.
- `ScanWorker` is unique work (`scan`, `KEEP`) started through the domain `ScanScheduler`, and calls `RunScanUseCase`:
  - it refuses a scope that is no longer readable, so revoked access never empties the index;
  - each execution gets a new `scan_run`; a run left `RUNNING` by process death is closed as `INTERRUPTED`;
  - a restart redoes nothing cached, because the caches are keyed by file, not by scan;
  - it runs as a `dataSync` foreground service when allowed, and as a plain job when restarted from the background;
  - progress (`stage`, `done`, `total`) reaches WorkManager and the notification at most every 500 ms per stage.
- Groups (`SaveExactGroupsUseCase`) replace the saved results in one transaction, only when a scan completes:
  - groups whose fingerprint is in `ignored_group` are not saved, nor counted in the summary (fingerprint and keeper rules: D-011);
  - marking a group "not duplicates" (P4-04) must add the fingerprint and delete the saved group in one transaction; un-ignoring brings it back on the next scan;
  - the keeper is computed on read by `SuggestKeeperUseCase`;
  - a later scan's index stage can prune members of the saved groups, so group queries return only groups with at least two members.
- Results pages saved groups with `GroupPagingSource` (offset paging, 50 per page, most space to free first, then `groupId`):
  - it invalidates itself through Room's invalidation tracker on `duplicate_group`, `group_member` and `keep_choice`, not `file_index`, which changes on every batch of a running scan; a deleted file still refreshes the list through the membership cascade;
  - a page's headers and members are read in one transaction, and each group is split into keeper and removable files by the shared `keeperOf` rule;
  - "Select duplicates" selects every removable file in the matching groups, not just the loaded pages; the selection clears when the filter changes, a new scan starts, or a keep choice or group changes.
- Group detail is keyed by fingerprint and reads one joined Flow query; "Open" hands other apps a typed MediaStore URI, the picked-folder document URI, or a `FileProvider` URI, never `file:`.

## Data model (Room)

**Index/cache tables.** These can be rebuilt by rescanning, but still use real migrations where that's cheap:

| Table | Key columns |
|---|---|
| `file_index` | id, sourceUri, path, name, mimeType, mediaKind, size, mtime, volume, lastSeenScanId |
| `file_hash` | fileId, partialHash, fullHash, computedAtMtime |
| `perceptual_hash` | fileId, algo (DHASH/PHASH/VIDEO_PHASH/VIDEO_DURATION/SIMHASH/AUDIO_DURATION/AUDIO_TITLE/AUDIO_ARTIST), frameIndex, value (Long) |
| `embedding` | fileId, modelId, dim, vector (BLOB, float32 LE, normalised) |
| `duplicate_group` / `group_member` | groupId, tier, score, scanId, fingerprint / groupId, fileId, similarity |
| `scan_run` | id, scope, tiers, startedAt, finishedAt, status, stats |

**User-decision tables.** These hold user data and need a real `Migration` plus a test:

| Table | Purpose |
|---|---|
| `ignored_group` | a group fingerprint the user marked "not duplicates" |
| `keep_choice` | the user's chosen keeper for a group fingerprint, stored by its `sourceUri` |
| `quarantine_item` | original path, quarantine path, size, movedAt (non-media soft delete) |
| `excluded_path` | folders that are never scanned |

All tables live in one database, `dupedigger.db`, under the user-data policy (D-010). User-decision tables never reference the index tables, which may be rebuilt. Rows derived from a file cascade when its `file_index` row is deleted.

## Storage access

| Need | Mechanism | Notes |
|---|---|---|
| Photos/videos/audio across the device | MediaStore + `READ_MEDIA_IMAGES/VIDEO/AUDIO` (33+), `READ_EXTERNAL_STORAGE` (31–32) | Handles 34+ partial access (`READ_MEDIA_VISUAL_USER_SELECTED`). |
| Targeted folders | SAF `OpenDocumentTree` + persisted URI permission | On API 30+, SAF can't pick the storage root or `Download/` itself. |
| Whole device, all file types | `MANAGE_EXTERNAL_STORAGE` + `java.io.File` walk | Google Play restricts it. See D-004 in DECISIONS.md. |

`StorageAccess` (domain interface) reports which modes are granted, and the scan scope picker only offers what is reachable (`availableScopes()`: WholeDevice needs All files access, MediaOnly needs media access, Folders needs at least one picked folder).

- **Picked folders** are the app's persisted SAF tree grants (`contentResolver.persistedUriPermissions`). Nothing else stores them, so they survive restarts, and removing one releases its grant. Read and write are both kept when granted, so non-media duplicates can be quarantined later.
- **Excluded folders** are absolute paths in `excluded_path` (for example `/storage/emulated/0/Private`), mapped from the picked tree's ExternalStorageProvider document id. Walkers skip any file whose path starts with one of them.

## Deletion

- **Media:** the system trash (recoverable for 30 days). `AndroidFileRemover` finds the file's row in its images, video or audio collection (by id, or by path for `file:` and picked-folder files); the trash prompt rejects Files-collection URIs. It trashes directly where the platform allows (`IS_TRASHED = 1`, which works for media the app owns), otherwise it returns one `TrashConsent` whose `IntentSender` the Route launches. `ConfirmTrashUseCase` removes the files from the index after the user accepts.
- **Non-media,** and media MediaStore has no row for: quarantined in the app's external files folder. The order is copy, check against the indexed size, insert the `quarantine_item` row (with the `sourceUri`), then delete the source; if that delete fails, the row and copy are removed and the file is reported as failed. Restore copies the file back (to its `file:` path, never over a file now there, or as a new document in its picked folder's parent), checks the size, then drops the copy and row; permanent delete removes both.
- **Undo** after a delete takes directly trashed media out of the trash (`IS_TRASHED = 0`) and restores quarantined files. Media trashed through the consent prompt is restored from the system's trash UI.
- `DeleteDuplicatesUseCase` refuses, before anything moves, a file in no saved group or any request that would remove every member of a group it touches. Removed files leave the index (their memberships cascade); failed ones and ones awaiting consent stay.

## AI subsystem

- `EmbeddingModelRepository` handles available variants, download state, the active model and the delete-model action.
- The model is **downloaded on demand** by `ModelDownloadWorker` (resumable, checksum-verified) into `filesDir/models/`. It is never bundled in the APK.
- Variants: URLs, pinned revisions and SHA-256 are in D-006.
  - EmbeddingGemma 2 **740M** (text + image + audio + video, 485 MB)
  - **440M** text + vision (388 MB)
  - **270M** text only (165 MB)
- `EmbeddingEngine` is lazily initialised, held as an app singleton, and closed after an idle timeout or when memory is trimmed. Init takes 1–3 s on CPU and 7–15 s on GPU (D-006).
- Text inputs use the `task: sentence similarity | query: ` prefix, trimmed. Images use `visionTokensPerImage = 70`. The engine returns L2-normalised vectors, and they are stored as returned.
- The backend default is CPU. GPU is a user preference that falls back to CPU if init fails (D-006 measured GPU slower and far heavier on a mid-range Mali). The manifest declares `<uses-native-library android:name="libOpenCL.so" android:required="false"/>`.
