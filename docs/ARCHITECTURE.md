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
                 → full streaming hash (SHA-256 or XXH3-128; decided in spike P0-03)
                 → groups of identical full hash  ⇒ MatchTier.EXACT
3. Near       ── images: decode a downscaled bitmap → dHash + pHash (64-bit)
                 video:  N keyframes via MediaMetadataRetriever → per-frame pHash
                 audio:  duration + tags first; acoustic fingerprint is an AI-tier concern
                 docs:   extract text (PDF, txt, docx) → 64-bit SimHash
                 → BK-tree / banding to find pairs within the Hamming threshold
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

## Data model (Room)

**Index/cache tables.** These can be rebuilt by rescanning, but still use real migrations where that's cheap:

| Table | Key columns |
|---|---|
| `file_index` | id, sourceUri, path, name, mimeType, mediaKind, size, mtime, volume, lastSeenScanId |
| `file_hash` | fileId, partialHash, fullHash, computedAtMtime |
| `perceptual_hash` | fileId, algo (DHASH/PHASH/SIMHASH/VIDEO_PHASH), frameIndex, value (Long) |
| `embedding` | fileId, modelId, dim, vector (BLOB, float32 LE, normalised) |
| `duplicate_group` / `group_member` | groupId, tier, score, scanId / groupId, fileId, similarity |
| `scan_run` | id, scope, tiers, startedAt, finishedAt, status, stats |

**User-decision tables.** These hold user data and need a real `Migration` plus a test:

| Table | Purpose |
|---|---|
| `ignored_group` | a group fingerprint the user marked "not duplicates" |
| `keep_choice` | the user's chosen keeper for a group fingerprint |
| `quarantine_item` | original path, quarantine path, size, movedAt (non-media soft delete) |
| `excluded_path` | folders that are never scanned |

## Storage access

| Need | Mechanism | Notes |
|---|---|---|
| Photos/videos/audio across the device | MediaStore + `READ_MEDIA_IMAGES/VIDEO/AUDIO` (33+), `READ_EXTERNAL_STORAGE` (31–32) | Handles 34+ partial access (`READ_MEDIA_VISUAL_USER_SELECTED`). |
| Targeted folders | SAF `OpenDocumentTree` + persisted URI permission | On API 30+, SAF can't pick the storage root or `Download/` itself. |
| Whole device, all file types | `MANAGE_EXTERNAL_STORAGE` + `java.io.File` walk | Google Play restricts it. See D-004 in DECISIONS.md. |

`StorageAccess` (domain interface) reports which modes are granted, and the scan scope picker only offers what is reachable.

## Deletion

- **Media:** `MediaStore.createTrashRequest` (system trash, recoverable for 30 days) through an `IntentSender` effect handled in the Route.
- **Non-media:** move to the app's quarantine dir → `quarantine_item`. Undo restores it. Permanent delete happens only from the Quarantine screen.
- `DeleteDuplicatesUseCase` refuses any request that would remove every member of a group.

## AI subsystem

- `EmbeddingModelRepository` handles available variants, download state, the active model and the delete-model action.
- The model is **downloaded on demand** by `ModelDownloadWorker` (resumable, checksum-verified) into `filesDir/models/`. It is never bundled in the APK.
- Candidate variants (sizes from the LiteRT-LM docs, to be verified in the P0-04 spike):
  - EmbeddingGemma 2 **740M** (text + image + audio + video, ~485 MB)
  - **440M** text + vision (~388 MB)
  - **270M** text only (~165 MB)
- `EmbeddingEngine` is lazily initialised (init can take ~10 s), held as an app singleton, and closed after an idle timeout or when memory is trimmed.
- Text inputs use the `task: sentence similarity | text: ` prefix, trimmed. Each vector is L2-normalised before it's stored.
- The backend preference is GPU → CPU, with NPU where available. The manifest declares `<uses-native-library android:name="libOpenCL.so" android:required="false"/>`.
