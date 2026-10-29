# Dupe Digger: development plan

Every task has an ID (`P<phase>-<nn>`), a scope and acceptance criteria ("Done when"). Status is tracked only in [PROGRESS.md](PROGRESS.md). Design details are in [ARCHITECTURE.md](ARCHITECTURE.md), and decisions in [DECISIONS.md](DECISIONS.md).

**Milestones**

| Milestone | Phases | Outcome |
|---|---|---|
| M1: Foundation | P0, P1 | The app builds with the full stack, and the theme and navigation shell run |
| M2: Exact-duplicate MVP | P2, P3, P4 | Scan → exact groups → safe delete, end to end |
| M3: Near duplicates | P5 | Similar images, videos and documents |
| M4: AI semantic | P6 | Opt-in EmbeddingGemma similarity |
| M5: Release polish | P7 | Performance, accessibility, tests, release build |

---

## P0: Project setup and spikes

| ID | Task | Done when |
|---|---|---|
| P0-01 | **Build setup.** Replace Java 11 `compileOptions` with `kotlin { jvmToolchain(21) }`. Add to the catalog and the app: KSP, Hilt, androidx.hilt (viewmodel-compose, work), Room (+ schema export), DataStore, Navigation 3, lifecycle-viewmodel-navigation3, kotlinx.serialization, kotlinx-collections-immutable, Coil 3 (+ video frames), WorkManager, Paging 3, Timber, material-icons-extended. Pin `material3` above the BOM for the Expressive APIs. Keep minSdk 31 and the existing `optimization {}` release block. | `./gradlew assembleDebug assembleRelease` succeed. No `kotlin-android` plugin and no kapt. |
| P0-02 | **App skeleton.** `DupeDiggerApp` (@HiltAndroidApp, Timber DebugTree in debug, WorkManager `Configuration.Provider` with HiltWorkerFactory), and the manifest provider override. `MainActivity` is @AndroidEntryPoint and edge-to-edge. | The app launches on a device and Hilt injects into the Activity. |
| P0-03 | **Spike: hashing.** Benchmark SHA-256 (`MessageDigest`) against XXH3-128 (a small pure-Kotlin or JNI lib) on 1 GB of mixed files on a mid-range device. Pick one, and pick the partial-hash window size. | The result and the choice are recorded in DECISIONS.md (D-005). |
| P0-04 | **Spike: LiteRT-LM embeddings.** Pin an exact `litertlm-android` version. Push the 270M and 740M models to a device by hand. Confirm the API names (`EmbeddingEngineConfig`, `EmbeddingEngine`, `computeEmbedding`, `InputData.Text/Image/Audio`), how output dimensions are chosen (128/256/512/768), and whether the vectors come normalised. Measure init time, per-item latency and peak RAM on CPU and GPU. Check that the HF repo is ungated and has a stable download URL plus checksum. | The findings are in DECISIONS.md (D-006). ARCHITECTURE.md's AI section is corrected if anything differs. |
| P0-05 | **Spike: storage reach.** On API 31, 33, 34 and 35+ devices or emulators, verify what MediaStore, SAF trees and `MANAGE_EXTERNAL_STORAGE` each reach (Download/, Documents/, WhatsApp media, SD card). | A matrix in DECISIONS.md, and D-004 resolved. |

## P1: Core foundations

| ID | Task | Done when |
|---|---|---|
| P1-01 | `core/common`: `Outcome`, `DomainError` (sealed: PermissionDenied, NotFound, Io, ModelUnavailable, Unknown), `runSuspendCatching`, dispatcher qualifiers + `DispatchersModule` + `@ApplicationScope`. | Unit tests cover `runSuspendCatching` rethrowing `CancellationException`. |
| P1-02 | `core/mvi`: `MviViewModel<S, I, E>`, `UiState/UiIntent/UiEffect`, `ObserveAsEvents`. `core/ui`: `UiText`, `ContentState`. | Unit test: `setState` is atomic, and effects are delivered once. |
| P1-03 | **Design system (M3 Expressive):** dynamic color + a branded fallback scheme, expressive `MotionScheme`, a shape scale, emphasized typography, and wrappers for `LoadingIndicator`, wavy progress, split/button groups and the extended FAB. Light and dark, with previews. | A preview gallery renders all tokens and components in light and dark. |
| P1-04 | **Navigation 3 host:** `@Serializable` keys, a `NavDisplay` with saveable-state + ViewModel-store decorators, `open/back/backTo` helpers, adaptive bottom bar or nav rail (Home, Results, Quarantine, Settings). | Tabs switch, back works, and state survives rotation. |
| P1-05 | **Room database:** entities from ARCHITECTURE.md, DAOs, `exportSchema = true`, migration test harness (`room-testing`). **DataStore:** settings (thresholds, tiers enabled, min file size, excluded paths, theme). | The DB builds, a schema JSON is exported, and the DAO tests pass. |

## P2: Storage access and file indexing

| ID | Task | Done when |
|---|---|---|
| P2-01 | Onboarding: explain why storage access is needed, request runtime media permissions by API level, handle partial access (34+), and offer "All files access" per D-004. | Every grant/deny path lands in a sensible state. Permanent denial deep-links to Settings. |
| P2-02 | `StorageAccess` + `ScanScope` (WholeDevice, MediaOnly, Folders(list of tree URIs or paths)). SAF folder picker with persisted permissions. Excluded paths. | Picked folders survive app restart. |
| P2-03 | File enumerators: MediaStore, SAF `DocumentFile` tree, and `File` walk. All three are Flow-based and cancellable. They skip `Android/data`, `Android/obb`, hidden dirs (configurable) and symlink escapes. | They enumerate a 50k-file tree without OOM. Cancelling stops them within 1 s. |
| P2-04 | Incremental indexer: upsert into `file_index` and mark stale rows by `lastSeenScanId`. Unchanged files (size + mtime) keep their cached hashes. | A rescan of an unchanged tree recomputes 0 hashes. |

## P3: Exact-duplicate engine

| ID | Task | Done when |
|---|---|---|
| P3-01 | Size bucketing → partial hash → full streaming hash (algorithm per D-005), with a buffered stream and bounded parallelism on `@DefaultDispatcher`. | Unit tests with temp files: identical, same-size-different, and empty files, plus 0-byte handling per settings. |
| P3-02 | `ScanWorker`: a foreground worker (notification with progress and cancel), unique work, `setProgress` per stage, and resume after process death. | Kill the app mid-scan; the worker restarts and finishes without redoing work it already cached. |
| P3-03 | Grouping + `SuggestKeeper` use case (resolution, original location, oldest). Persist `duplicate_group` / `group_member`. Respect `ignored_group`. | Unit tests for the keeper ranking and ignore rules. |
| P3-04 | `DeleteDuplicates` use case: refuses to empty a group, uses MediaStore trash for media, and quarantines non-media. | Unit test: deleting every member returns an error. Instrumented: a trashed media file disappears from MediaStore and can be restored. |

## P4: MVP UI

| ID | Task | Done when |
|---|---|---|
| P4-01 | **Home:** storage summary (reclaimable space by category), last scan info, and quick actions (Scan everything / Photos / Pick folders). | Loading, empty and error states are shown. |
| P4-02 | **Scan screen:** scope + tier picker, live progress per stage (wavy progress, files/s, found so far), cancel. | Progress updates smoothly without recomposing the whole screen (lambda-read progress). |
| P4-03 | **Results:** groups by category (Images, Videos, Audio, Docs, Other) and tier. Sort by reclaimable size. Paged list with stable keys. Bulk "select all but the suggested keeper". | Scrolls smoothly with 5k groups. |
| P4-04 | **Group detail:** thumbnails (Coil 3, video frames), metadata compare (size, resolution, date, path), choose keeper, open file, mark "not duplicates". | Keeper and ignore choices persist across rescans. |
| P4-05 | **Delete flow:** confirmation (count + size), system trash request through an `IntentSender` effect, undo snackbar for quarantine. **Quarantine screen**: restore or delete permanently. | Deleting from Results updates Home's reclaimable total. |

## P5: Near-duplicate detection

| ID | Task | Done when |
|---|---|---|
| P5-01 | Image dHash + pHash (downscaled decode via `ImageDecoder`, EXIF orientation applied). Hamming threshold setting. | The same photo resized, recompressed or saved as JPEG/PNG/WebP matches. A different photo doesn't (golden-set test). |
| P5-02 | BK-tree or LSH banding for candidate pairs, plus union-find grouping (pure Kotlin in `domain/detector`). | Unit tests. 50k hashes are grouped in under 5 s on a mid-range device. |
| P5-03 | Video: N evenly spaced keyframes → pHash sequence, compared with duration tolerance. | A re-encoded video matches its original. |
| P5-04 | Documents: text extraction (txt/md, PDF via `PdfRenderer` text APIs where available, docx via zip/XML), normalisation, 64-bit SimHash. | Two PDFs exported from the same source with different metadata match. |
| P5-05 | Audio: duration + tag (title/artist/album) heuristics for the NEAR tier. Acoustic similarity is deferred to P6. | The same song in MP3 and M4A is flagged as a candidate. |
| P5-06 | UI: tier chips, similarity %, side-by-side compare with a zoom/diff slider for images. | Near groups are clearly distinguished from exact ones. |

## P6: AI semantic similarity (EmbeddingGemma via LiteRT-LM)

| ID | Task | Done when |
|---|---|---|
| P6-01 | Model catalog (variants from D-006) and `ModelDownloadWorker`: resumable (HTTP Range), checksum verification, storage space check, Wi-Fi-only option, progress notification. | Kill and restart mid-download resumes it. A corrupt file is rejected. |
| P6-02 | `EmbeddingEngine` wrapper (data/ai): lazy init on a background dispatcher, backend fallback GPU → CPU, idle close, `onTrimMemory` close, one in-flight batch at a time. | An instrumented test embeds text and an image and returns vectors of the expected dim. |
| P6-03 | Embedding stage in `ScanWorker` (opt-in per tier). Inputs: images (downscaled bytes), audio (clip bytes), doc text (prefix + trim, truncated to the model's token window). Store normalised float32 BLOBs keyed by modelId. | Switching models invalidates only that model's vectors. |
| P6-04 | Similarity search: brute-force cosine per MediaKind bucket with a threshold, union-find into SEMANTIC groups. Measure it, and add ANN only if 20k vectors take more than 10 s. | Similar shots (burst photos) are grouped. Unrelated images aren't (golden set). |
| P6-05 | Settings UI: download/delete model, variant picker with size and RAM notes, backend preference, semantic threshold slider, and battery/charging-only option. | The whole AI flow can be operated and undone from the UI. |

## P7: Quality and release

| ID | Task | Done when |
|---|---|---|
| P7-01 | Performance: Baseline Profile, macrobenchmark for startup and results scroll, a scan throughput benchmark. | Numbers recorded in PROGRESS.md. |
| P7-02 | Adaptive layouts: list-detail on tablets and foldables (Results ↔ Group detail), and keyboard/mouse support. | Works on phone, foldable and tablet emulators. |
| P7-03 | Accessibility: TalkBack labels, touch targets, contrast, and font scaling up to 200%. | An Accessibility Scanner pass has no critical issues. |
| P7-04 | Tests: use-case unit tests ≥ 80% of the domain, Room migration tests, Compose UI tests for the delete-safety flow. | CI-equivalent `./gradlew testDebugUnitTest lint` is green. |
| P7-05 | Release: R8 keep rules (LiteRT, Room, serialization), app icon, privacy note (fully offline except the model download), and a signed release build. | The release APK installs and passes a smoke test. |

---

## Later (not scheduled)

- Scheduled background rescans and a "new duplicates" notification.
- Similar-but-not-duplicate cleanup (blurry, screenshots, large files).
- Cloud folders through SAF providers (Drive and similar). These are slow, so treat them as a separate scope.
- Multi-module split (`:core:*`, `:feature:*`), if build times grow.
