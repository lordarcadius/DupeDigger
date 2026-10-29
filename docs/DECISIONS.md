# Decisions

ADR-lite. Each entry gives the decision, why it was made and its status (`accepted`, `open` or `superseded by D-xxx`). Add new entries at the bottom.

## D-001: Single module, layers as packages (accepted)
**Decision:** a single `:app` module with `core/`, `domain/`, `data/`, `di/` and `presentation/` packages.
**Why:** a solo project of moderate size. Multi-module adds build-logic overhead without a payoff yet. Domain purity is enforced by review and the grep check in AGENTS.md.
**Revisit:** if clean builds exceed ~2 min or a second app or module needs the engine.

## D-002: MVI on a shared `MviViewModel<S, I, E>` base (accepted)
**Why:** scan, results and delete flows have many user intents and one-shot effects (system trash `IntentSender`, snackbars). A single `onIntent` entry point keeps them testable.

## D-003: Three detection tiers; AI is opt-in and comes last (accepted)
**Decision:** Exact → Near (perceptual hashes) → Semantic (EmbeddingGemma).
**Why:** exact hashing is cheap and certain, and covers most reclaimable space. Perceptual hashes catch re-encodes more cheaply and more reliably than embeddings. Embeddings add "similar shots" and cross-format matches, but cost a 165–485 MB download and hundreds of MB of RAM, so they must never block the MVP.

## D-004: Storage access strategy (accepted, P0-05)
Options:
1. **MediaStore + SAF only.** Play-safe. Covers media device-wide and user-picked folders, but SAF can't pick the storage root or `Download/` on API 30+.
2. **`MANAGE_EXTERNAL_STORAGE`.** True whole-device scan of every file type. Play allows it only for approved use cases (file managers and similar), so a declaration is needed and it may be rejected.
3. **Hybrid (leaning):** default to 1. Offer 2 as an explicit "Deep scan" toggle with an explainer. If Play distribution needs it, ship a flavor without it.

**Owner's answer:** PRM2-8737 says the app "is meant to be published on Google Play in the future".

**Decision: option 3, hybrid.**
- **Default access** is MediaStore with the media permissions, plus SAF folders the user picks. These cover photos, video and audio anywhere on the device, and every file type inside a picked folder.
- **"Deep scan"** is an explicit opt-in that requests `MANAGE_EXTERNAL_STORAGE` behind an explainer. It is the only way to reach documents, archives and other non-media files outside a picked folder, including those directly in `Download/`, which SAF cannot pick.
- **A Play build without it:** if the Play declaration is rejected, ship a flavor without the permission. Deep scan then hides itself, because `StorageAccess` reports it unavailable.

**Measured matrix (P0-05).** Method:
- A throwaway instrumented test (not kept) probed 14 files that another uid (the shell) had created.
- It checked each file three ways, and the three always agreed: a row in `MediaStore.Files`, opening that row's content URI, and reading it with `java.io.File`.
- Emulators: API 31, 33, 34 (`google_apis`), and 35, 36 (`google_apis_playstore`).
- Media permissions were `READ_EXTERNAL_STORAGE` on 31–32 and `READ_MEDIA_IMAGES/VIDEO/AUDIO` on 33+.
- "Partial" means only `READ_MEDIA_VISUAL_USER_SELECTED` granted, with nothing selected.

| File (created by another app) | No permission | Media permissions (31, 33, 34, 35, 36) | Partial, 34+ (nothing selected) | `MANAGE_EXTERNAL_STORAGE` (31–36) |
|---|---|---|---|---|
| `DCIM/Camera`, `Pictures` jpg; `Movies` mp4; `Music` mp3 | no | **yes** | no | yes |
| `Download/` jpg | no | **yes** | no | yes |
| `Download/` pdf, zip, unknown `.dat` | no | no | no | **yes** |
| `Documents/` pdf, txt | no | no | no | **yes** |
| `Android/media/<pkg>/…` jpg (WhatsApp-style path) | no | **yes** | no | yes |
| `Android/media/<pkg>/…` pdf | no | no | no | **yes** |
| Custom top-level folder: jpg / pdf | no / no | **yes** / no | no / no | yes / **yes** |
| List `Android/data`, `Android/obb` | denied | denied | denied | **denied** |

The SAF tree picker (`ACTION_OPEN_DOCUMENT_TREE`), checked by UI on the API 35 emulator:
- **"Can't use this folder"** (blocked): the storage root, `Download/`, and `Android/`, where only `media` is listed.
- **Allowed:** `Android/media`, a subfolder of `Download/`, `DCIM`, `Documents` and a custom top-level folder.

**Findings that shape P2:**
- **Media permissions cover all media, anywhere.** That includes `Download/` and app media folders, on every level from 31 to 36. Non-media files are never visible to MediaStore without `MANAGE_EXTERNAL_STORAGE`.
- **`MANAGE_EXTERNAL_STORAGE` still cannot reach `Android/data` or `Android/obb`.** The exclusion rule in AGENTS.md costs nothing.
- **Partial access (34+) exposes only what the user selected.** Onboarding must say so and offer to widen it.
- **MediaStore can lag behind the file system.** On a freshly booted API 36 emulator, files copied in 3 s earlier were readable through `File` but had no MediaStore row yet; after a 60 s wait they did. The enumerator must not treat MediaStore as instantly complete, so a rescan picks up late rows.

**Gaps:**
- **SD card not tested:** none of the emulators, nor the Primebook, had a secondary volume. `MediaStore.getExternalVolumeNames` returned only `external_primary` everywhere.
- **The real device couldn't be probed:** on the Primebook (API 35, PrimeOS) the shell cannot create files on `/sdcard` ("Bad address"). There I could only confirm the `Android/data` and `Android/obb` denials and directory counts that grow with each permission tier.
- **SAF on other API levels:** the picker was checked only on API 35. The same restrictions are documented for API 30+.

## D-005: Content hash algorithm (accepted, P0-03)
Candidates: SHA-256 (built-in `MessageDigest`, no dependency) or XXH3-128 (faster, needs a library). Collision safety isn't a concern for either at this scale, so speed decides.

**Decision:** SHA-256 through `MessageDigest` for the full hash, and also for the partial hash. The partial-hash window is **16 KiB head + 16 KiB tail**, plus the size.

**Why:**
- On the device, the platform SHA-256 (the `AndroidOpenSSL` provider, BoringSSL with hardware SHA2) hashes about 1.3 GB/s from memory. Cold storage reads only manage about 430 MiB/s, so full hashing is bound by storage, not CPU.
- XXH3-128 has no faster option on Android:
  - hash4j 0.31.0 (pure Java) ran at about 10 MiB/s, both debuggable and non-debuggable. I did not investigate the cause; its byte-array VarHandle reads may not be optimised on ART.
  - hash4j also needs VarHandle, which ART only has from API 33, below this app's minSdk 31.
- 16 KiB keeps the window ARCHITECTURE.md already specifies. 4 KiB saves about 0.3 ms per file, but media headers (EXIF, container metadata) often fill the first few KiB, so a smaller window filters less. Filtering power was not measured, because the test files were random bytes.

**Measured** on a Primebook (MediaTek MT8781, 2×A76 + 6×A55, 6 GB RAM, Android 15). Method:
- An instrumented test, not kept in the repo, ran over 1000 MiB of files: 2000 × 256 KiB and 5 × 100 MiB.
- The page cache was dropped before every run, through `adb root` and `drop_caches`.
- Everything ran on one thread, with a 256 KiB read buffer.

Results, each run twice:

| Run | Result |
|---|---|
| Read only, no hash | 2.27–2.50 s (400–440 MiB/s) |
| SHA-256, full | 3.11–3.34 s (300–322 MiB/s) |
| XXH3-128 (hash4j), full | 104–111 s (9–10 MiB/s) |
| In memory, 64 MiB | SHA-256 1311–1330 MiB/s; XXH3-128 10–11 MiB/s |
| Partial head+tail, 2000 files | 4 KiB: 0.57 ms/file; 16 KiB: 0.85 ms/file; 64 KiB: 1.25 ms/file |

**Caveats:**
- One device, measured on real hardware, not an emulator.
- The files were synthetic random data on internal storage. SD cards and USB storage will be slower to read, which makes hashing even more storage-bound.

## D-006: LiteRT-LM embedding integration (accepted, P0-04)
**Decision:**
- Use `litertlm-android` **0.18.1** with the generic (CPU/GPU) `.litertlm` files. Download them from the pinned Hugging Face revisions below and check them against the listed SHA-256.
- The **CPU backend is the default**. GPU is an opt-in preference that falls back to CPU if it fails to initialise.
- Images use `visionTokensPerImage = 70`.

**API, confirmed against the 0.18.1 AAR:**
- `EmbeddingEngineConfig(modelPath, backend, visionBackend?, audioBackend?, cacheDir?, modelFd?, maxInputLength?, visionTokensPerImage?, activationDataType?)`.
- `EmbeddingEngine(config)`, then `initialize()` / `isInitialized()` / `close()` (it is `AutoCloseable`).
- `close()` throws if `initialize()` never succeeded, so guard it with `isInitialized()`.
- Embedding calls:
  - `computeEmbedding(List<InputData>, EmbeddingOptions)` returns an `EmbeddingResponse` with `embedding: FloatArray`;
  - `computeEmbeddingBatch(...)` and suspend `computeEmbeddingAsync(...)` also exist.
- Inputs are `InputData.Text(String)`, `InputData.Image(ByteArray)` and `InputData.Audio(ByteArray)`:
  - encoded JPEG bytes worked for images;
  - 16 kHz mono PCM16 WAV bytes worked for audio.
- Backends are `Backend.CPU(threadCount?)` and `Backend.GPU()`. `Backend.NPU` and `Backend.GOOGLE_TENSOR` also exist, but they need SoC-specific model files.
- `EmbeddingOptions(normalize?, insertSpecialTokens?, outputSize?, visionTokensPerImage?)`:
  - the output dimension comes from `outputSize` (128/256/512/768, each verified);
  - the default is 768.
- **Vectors come back L2-normalised by default** (norm 1.0000), including truncated sizes. With `normalize = false` the norm is about 874.

**Text prefix:**
- The model card's symmetric-similarity prompt is `task: sentence similarity | query: {content}`, not `| text:`.
- Matryoshka truncation is near-lossless down to 256 dimensions, per the card. 128 is not recommended for multimodal.
- `maxInputLength = 128` rejects audio, which needed 129 tokens for a 5 s clip, so use 512 or more when audio is enabled.

**Models.** All three are ungated and Apache-2.0. The URL pattern is `https://huggingface.co/litert-community/<repo>/resolve/<revision>/<file>`.

| Variant | Repo @ revision | File | Size | SHA-256 |
|---|---|---|---|---|
| 270M text | `embeddinggemma-2-text-270m-litert-lm` @ `9be6e8b90982095dc05c2bd162e4b954ee4dbac7` | `embeddinggemma-2-text-270m.litertlm` | 164,626,432 | `2d079ee2f6f066b1f368e8d7c819f55214eaef1d0513b312321901f30ab286fb` |
| 440M text + vision | `embeddinggemma-2-text-vision-440m-litert-lm` @ `e301f74d5551b0c2641bd5cb4652a76239d5c5f8` | `embeddinggemma-2-text-vision-440m.litertlm` | 387,710,976 | `92dcbea108899e5d6e30d919b0744f90d9967e80c67a4ab5503ac16d54f62eb0` |
| 740M text + vision + audio | `embeddinggemma-2-740m-litert-lm` @ `94408f31843f8bd0adb11c262292627287f5fda4` | `embeddinggemma-2-740m.litertlm` | 484,622,336 | `e7a8a2204b91e0f96e92960e84a09a89212e1633dcb7575a9bf3378b4df77f4c` |

The 270M and 740M downloads matched their SHA-256 exactly. The 440M file's size and hash came from the Hugging Face API, and a P6-02 download later matched them.

**Measured** on a Primebook: MediaTek MT8781 (2×A76 + 6×A55, Mali GPU), 6 GB RAM, Android 15.
- The model was loaded from internal `filesDir`. The app cannot read a file the shell pushed into its external files dir.
- Each configuration ran in a fresh process. The test process sat at about 269 MB RSS before `initialize()`; "peak" below is the process `VmHWM`.

| Model / backend | Init | Text, per item | Image | Audio (5 s) | Peak RSS |
|---|---|---|---|---|---|
| 270M CPU | 1.1–1.2 s | 316–319 ms | — | — | 514–522 MB |
| 270M GPU | 7.1 s | 459 ms | — | — | 2310 MB |
| 740M CPU, 70 vision tokens | 1.3–1.8 s | 313–315 ms | 2.86 s | 1.48 s | 969–1029 MB |
| 740M CPU, default (140) vision tokens | 1.3–3.1 s | 314–315 ms | 6.83 s | 1.47 s | 1163–1208 MB |
| 740M GPU, default vision tokens | 15.3 s | 570 ms | 5.36 s | 1.86 s | 2662 MB |

- **`maxInputLength`:** 128, 512 or the default did not change text latency.
- **CPU threads:** 1 thread gave 492 ms per text and 3.0 s per image; 2 gave 408 ms; 4 gave 313 ms, about the same as the default; 8 gave 287 ms and 2.8 s.
- **Similarity sanity check:** synthetic cases only.
  - Text: "cat on a sofa" against "kitten on a couch" scored 0.933; against "quarterly tax report" it scored 0.716. Unrelated text scores high, so the semantic threshold needs to sit around 0.9.
  - Images: a shifted copy scored 0.995–0.999; a different image scored 0.81–0.84.

**Why CPU first:**
- On this Mali GPU the GPU backend was slower than CPU for text.
- It took 7–15 s to initialise and pushed process RSS to 2.3–2.7 GB on a 6 GB device.
- GPU was faster only for images, by about 20%.

**Consequences for P6:**
- At about 2.9 s per image, about 0.3 s per document and about 1.5 s per audio clip, a 20k-photo library takes on the order of 16 hours on this device.
- So the semantic stage must be:
  - opt-in and incremental, embedding only files whose vectors are missing or stale;
  - in a resumable worker, with the charging-only option (P6-05);
  - limited to the selected MediaKinds.

**Not verified:**
- Real photos, video and real-world audio.
- The 440M file.
- `insertSpecialTokens`.
- NPU backends.
- Behaviour on API 31–34.
- Whether Hugging Face keeps these revisions available long term.

**Downloads (P6-01):**
- **Catalog:** `ModelCatalog` in the domain lists the three variants above (URL pinned to the revision, size, SHA-256, capabilities) with the measured peak RSS on CPU. It is null for 440M, which wasn't measured.
- **Storage:** files go to `filesDir/models`. The download writes `<file>.<sha prefix>.part`, resumes with `Range: bytes=<size>-`, and is renamed to the final name only after the size and SHA-256 match. So a final file is always a verified one.
  - Hugging Face `resolve/` URLs redirect (302) to a CDN that answers a Range request with 206 and the right `Content-Range`.
  - A 200 or a mismatched 206 restarts the file.
- **Failures:**
  - A checksum mismatch deletes the partial file and never retries.
  - Network errors, 5xx and 429 retry with exponential backoff (30 s start, up to 6 runs) and resume.
  - Other 4xx and too little space (the rest of the file plus 50 MB) fail for good, with a reason.
  - A stopped or cancelled worker keeps its partial file, for example when Wi-Fi drops or Cancel is tapped in the notification. Only `ModelDownloads.cancel()` deletes it.
- **One download at a time:** one unique work name with KEEP, so two large files never compete for space.
- **Wi-Fi only (default on):** maps to `NetworkType.UNMETERED`, which also allows Ethernet and excludes metered Wi-Fi. The constraint is fixed when the work is queued, so a settings change has to queue again (P6-05).
- **Foreground notification:** `dataSync` type with bytes, a progress bar and Cancel.
- **Permission:** the app now declares `INTERNET`; this is its only network use.

**Engine wrapper (P6-02):**
- **Interface:** the domain `Embedder` returns one L2-normalised vector per input at the asked size. It reports `ModelUnavailable` when the model isn't downloaded and `Unknown` when the engine fails; a failed engine is dropped.
- **Lifecycle:** `LiteRtEmbedder` (data/ai) loads lazily on the IO dispatcher and keeps one model and backend loaded.
  - It closes 60 s after the last batch, or when the app is trimmed in the background (`TRIM_MEMORY_BACKGROUND`, forwarded from `DupeDiggerApp`), and loads again on the next call.
  - A mutex keeps one batch in flight.
- **GPU → CPU fallback:** covers a GPU that throws while starting, and the fallback holds until the model or backend changes.
  - It can't cover a native crash. The emulator exited during the first device run; its log ended just after a Dawn Vulkan device was created, so the GPU start is the likely cause (not confirmed). GPU stays an opt-in preference (CPU default).
- **`LiteRtModelLoader`:** vision and audio encoders run only for variants that have them; audio sets `maxInputLength = 512`; images use 70 vision tokens.
- **440M verified:** its SHA-256 now matches an actual download (P6-02 device test).
- **Measured** on an API 36 x86_64 emulator, CPU, 440M text + vision: three texts and one 512 × 384 JPEG took 10.2 s including the engine load, and 1.6 s again with it loaded. All vectors had norm 1.0000 at 256 and 768 dimensions. "A cat sleeping on a sofa" against "a kitten asleep on a couch" scored 0.971, against a tax report 0.711.

**Semantic tier (P6-03, P6-04):**
- **When it runs:** a SEMANTIC scan stage, after near, when the AI tier is on and a model is chosen (`aiModelId`; backend from `aiBackend`, default CPU). Without a loadable model it is skipped and the scan carries on.
- **Inputs, by what the model reads:**
  - images re-encoded as JPEG with a 512 px long side;
  - audio as a 10 s clip from a quarter of the way in, mixed to mono and resampled to 16 kHz PCM16 WAV;
  - document text with `task: sentence similarity | query: ` in front, whitespace collapsed, cut to 1,500 characters. Documents under 20 words are skipped.
  - Videos aren't embedded.
- **Storage:**
  - Vectors are 256 floats (D-006), stored as float32 little-endian BLOBs in `embedding` keyed by `<variant>@<dim>`.
  - Switching model leaves the other model's vectors in place and never compares across them.
  - A changed file loses all its vectors (P2-04).
- **Grouping:**
  - Per media kind, cosine ≥ the semantic threshold (default 0.90), joined with union-find.
  - Each exact group takes part through one member. A semantic group whose files all sit in one near group is dropped.
  - Score is the mean cosine to the first member; the fingerprint is `SEMANTIC:` plus the SHA-256 of the sorted URIs.
- **Search:**
  - Comparing every pair took 39 s for 20,000 vectors on a 16-core desktop (single thread), over the 10 s limit, so candidates come from 64-bit random-hyperplane signatures through the multi-index Hamming search.
  - The bit budget sits 2.5 standard deviations above the expected distance at the threshold (17 bits at 0.90), and every candidate is confirmed with the exact cosine.
  - It matched all-pairs exactly on three seeded test sets, including pairs near the threshold.
- **Measured:**
  - 20,000 vectors: 0.55 s on the desktop, 2.1 s on an API 36 x86_64 emulator (debuggable). Not measured on the Primebook.
  - Golden set with the real 440M model on the emulator: three burst shots of one scene scored 0.992–0.995 against each other, and an unrelated photo scored 0.873–0.877 against them. The default 0.90 groups the bursts only, but the margin to unrelated photos is small; P6-05's threshold slider should not go much below 0.90.
- **Engine failures:** a batch the engine rejects is retried file by file, so one bad file costs only itself.

**AI settings (P6-05):**
- **Where:** Settings → "AI matches" opens a screen that:
  - turns the tier on (only once a downloaded model is chosen);
  - lists the three models with what they compare, download size and measured memory ("not measured" for 440M), and per-model state: download, waiting (for Wi-Fi), progress with Cancel, failure with Retry, Use, Delete;
  - holds the options: Wi-Fi only, only while charging, processor or graphics chip, and the threshold.
- **One download at a time:** the other models' Download buttons are disabled while one is queued or running.
- **Notifications:** permission is asked before a download starts.
- **Wi-Fi only:** changing it queues a waiting or running download again (`ExistingWorkPolicy.REPLACE`, keeping the partial file) so the new rule applies.
- **Threshold:** offered from 0.88 to 0.98, because unrelated photos scored up to 0.877 in the golden set. Saved on release; applies from the next scan.
- **Only while charging:** a scan on battery skips the semantic stage (`BatteryManager.isCharging`).
- **Deleting a model** cancels its download, removes the file and its stored vectors, and turns the tier off if it was the chosen model, so the flow can be undone from the UI. Semantic groups from earlier scans go with the next scan.
- **Scan screen:** the "AI matches" row is a working switch once a model is chosen.

## D-007: Never auto-delete; always keep one (accepted)
Deletion is always user-initiated and confirmed. Media goes to the system trash, and non-media to an in-app quarantine. A group can never be emptied. This is enforced in both the use case and the UI.

**Known limit (P3-04):** the quarantine lives in the app's external files folder, so uninstalling the app or clearing its storage deletes quarantined files for good. The Quarantine screen (P4-05) should say so. `android:hasFragileUserData="true"` would make the uninstall dialog offer to keep app data; I have not verified that it covers this folder, and it isn't set yet.

## D-008: Build baseline (accepted)
Keep the template's minSdk 31, compile/target 37, AGP 9.5 built-in Kotlin and the `optimization {}` release DSL. Move to a Java 21 toolchain in P0-01. Hilt and Room use KSP only.

## D-009: Design system baseline (accepted, P1-03; brand seed is a placeholder)
**Decision:**
- **Placement:** the theme, tokens and Expressive wrappers live in `core/designsystem/`, per ARCHITECTURE.md. That overrides the house design skill's `presentation/theme/`.
- **Theme:** `MaterialExpressiveTheme` with `MotionScheme.expressive()`, the explicit eight-value `AppShapes`, and Inter applied to every style.
- **Dynamic colour is the default.** It is always available at minSdk 31. The branded static scheme is used only when a caller passes `dynamicColor = false`; today that is only the preview gallery.

**Brand seed `#8A5A2B` (warm amber, "digging").** This is a placeholder, not an owner choice:
- No brand colour was supplied, so I picked it. The owner should replace it.
- The 48 light and 48 dark roles in `Color.kt` were generated from it, not hand-picked. The tool was material-color-utilities (the Python port `materialyoucolor` 3.0.4, the same algorithm Material Theme Builder uses), with the TonalSpot variant, standard contrast and the 2021 spec.
- To change the brand: export a Jetpack Compose scheme for the new seed from Material Theme Builder, and replace the values in `Color.kt` verbatim.

**Font:**
- Inter 4.1 variable roman and italic come from the official release (`rsms/inter` v4.1), as `res/font/inter_variable*.ttf`, registered at weights 300–700.
- The OFL 1.1 licence ships as `res/raw/inter_license.txt`.
- It still needs a place on an open-source notices screen, which P7-05 adds with the privacy note.

**Wrappers:**
- `AppLoadingIndicator` (plain or contained), `AppProgressBar` (wavy, determinate or indeterminate), `AppConnectedButtonGroup` (connected toggle buttons, one or many selected) and `AppExtendedFab`.
- Split buttons are not wrapped yet; no planned screen needs one.
- Shape morphing comes only from these stock components, never from app code.

## D-010: One Room database and the settings store (accepted, P1-05)
**Decision:** all tables live in one database, `dupedigger.db`, under the user-data policy:
- the schema is exported and committed (`app/schemas/`);
- every version bump ships a real `Migration` in `ALL_MIGRATIONS`, and `DatabaseMigrationTest` must pass;
- there is never a destructive fallback.

Version 1 has not shipped, so it was changed in place: `quarantine_item.sourceUri` was added in P3-04. From the first release on, every schema change bumps the version with a `Migration`.

**Why one database:**
- Splitting the index/cache tables into a disposable database would let them use a destructive fallback. But ARCHITECTURE.md asks for real migrations on them too where that's cheap.
- A single database keeps the group and member queries in one place. It also lets `file_index` deletions cascade to the hashes, embeddings and memberships derived from it.

**Keys:**
- User-decision rows never reference the rebuildable index tables.
- `ignored_group` and `keep_choice` match on a group fingerprint, defined in D-011. `keep_choice` stores the keeper's `sourceUri`, not a row id.

**Settings:** settings are in Preferences DataStore (`settings` file): near and semantic tier toggles, the Hamming and semantic thresholds, the minimum file size and hidden-file skipping.
- Defaults: near on, semantic off, Hamming 10, semantic 0.90 (D-006), minimum size 1 byte (empty files skipped), hidden files skipped.
- Excluded paths are in Room `excluded_path`.
- PLAN.md listed a theme setting. It was dropped, because the house design has the theme follow the system with no in-app toggle (D-009).

## D-011: Group fingerprint and default keeper (accepted, P3-03)
**Fingerprint:** an exact group is identified by its content: `EXACT:<size>:<SHA-256>` (`exactFingerprint`).
- It survives moves, renames, index rows being rebuilt, and new copies of the same content appearing, so "not duplicates" and keep choices hold across rescans.
- Marking a group "not duplicates" therefore also covers future copies of that content.
- Saved user choices are keyed by this string, so changing its format needs a migration of `ignored_group` and `keep_choice`.
- Near and semantic groups (P5, P6) will need a member-based fingerprint, defined then.

**Default keeper:** computed when groups are read, not stored, so a keep choice or a later metadata measurement needs no rewrite. The ranking (`rankKeeper`), best first:
1. higher quality (pixels for images and videos, bitrate for audio, stored as `QUALITY` rows since P5-06), only when every member has a value; files hashed before P5-06 have none until they change, so their groups fall through to location. Bitrate isn't comparable across codecs (128 kbps AAC vs 160 kbps MP3);
2. location: a file under `DCIM/` beats one in an unremarkable folder, which beats a copy under `WhatsApp/`, `Telegram/`, `Download(s)/`, `Messenger/`, `Android/media/`, `.thumbnails/` or `cache/`, matched on whole folder names;
3. the oldest modification time, with an unknown time (0) ranked last;
4. `sourceUri`, so the result is always the same.

A saved keep choice wins while that file is still in the group.

## D-012: Near-duplicate images (accepted, P5-01/P5-02)
**Hashes:** each image is decoded with `ImageDecoder` at a power-of-two subsample that keeps the short side at least 64 px (EXIF orientation applied by the decoder; transparent pixels count as white), converted to luminance and area-averaged down to:
- 9 × 8 for a 64-bit dHash (left brighter than right);
- 32 × 32 for a 64-bit pHash (2-D DCT, the 8 × 8 lowest frequencies against their median, DC left out of the median).

Both are cached in `perceptual_hash` (`DHASH`, `PHASH`, frame 0) and dropped with the file's other derived rows when it changes.

**Matching:** two images match when both pHash and dHash are within the Hamming threshold setting (default 10). Groups are the transitive closure of matches (union-find), so a chain of close images forms one group.

**Candidate search:** multi-index hashing, a banding scheme. For threshold `t` a probe radius `r` is picked (0 up to t = 4, 1 up to 8, else 2) and the 64 bits are split into `⌊t/(r+1)⌋ + 1` bands; any pair within `t` bits then differs by at most `r` bits in some band, so probing each band's keys within `r` finds every pair. Only the default threshold (10) was timed; above about 12 the bands get narrow and crowded and the search slows toward all-pairs, and matches chain most images into one group. The P5-06 threshold control should cap its range or retime it. Measured on the Primebook (debuggable build), 50,000 hashes at threshold 10:
- BK-tree: 41.5 s uniform random, 33.1 s clustered. Plain all-pairs: 16.9 s. Both were rejected.
- Multi-index with boxed map buckets: 14.8 s. With primitive, direct-addressed buckets and no per-candidate lambdas: 3.5 s uniform random, 1.7 s clustered.

**Exact and near together:** each exact group takes part in near grouping through one member (its lowest file id), so byte-identical copies stay in the exact group and no file's space is counted twice. "Select duplicates" never picks a file that is the keeper of another group it covers, so a bulk delete can't be refused for emptying a group.

**Fingerprint:** a near group is `NEAR:` plus the SHA-256 of its members' sorted `sourceUri`s. Unlike an exact fingerprint it changes whenever the membership does, so "not duplicates" and a keep choice stop applying once a copy is added, removed or moved; the group then shows again for the user to decide.

**Known gap:** the keeper ranking doesn't use resolution yet, so a near group can suggest keeping the smaller copy. Rank by pixels when P5-06 adds near-group detail (Group detail already reads dimensions on demand).

**Videos (P5-03):** a video's signature is its duration and the pHash of 8 frames, each from the middle of an equal slice of the video, so the very first and last frames (often black) are skipped.
- Frames come from `MediaMetadataRetriever.getScaledFrameAtTime` (128 px box) with `OPTION_CLOSEST`. That gives the exact frame rather than the nearest keyframe, so a re-encode with other keyframe spacing samples the same picture. The cost is a decode from the previous keyframe per sample, and videos are signed one at a time.
- Stored in `perceptual_hash` as `VIDEO_PHASH` rows (frame 0–7) plus one `VIDEO_DURATION` row (value in ms), so there is no schema change. A signature missing any part is computed again.
- Two videos match when their durations differ by at most 1 s or 2 % of the longer, whichever is more, and at least 6 of the 8 frames, compared in order, are within the Hamming threshold. Candidates come from a duration-sorted sliding window; groups from union-find, like images.
- Whether the retriever applies the rotation metadata to the frames was not checked. A re-encode that keeps the metadata hashes the same either way; one that bakes the rotation into the pixels may not match.
- On an API 35 emulator with software codecs, a 3 s 640 × 360 clip re-encoded at 320 × 180, 300 kbps and other keyframe spacing matched at per-frame distances 0–2. A clip with a different layout and motion was at 24–40, and the same scene at twice the length was rejected on duration. Each signature took 0.2–0.3 s.
- Limit seen while building the test: a clip with the same moving shapes over a changed background scored 4–12 per frame and would have matched. Synthetic clips with little detail are weak evidence for real footage.

**Documents (P5-04):**
- **Text:** read from text files (`text/*` and txt/md/csv/log/rst, the first 1M characters), .docx (the runs of `word/document.xml`, at most 20 MB of XML), and PDFs through `PdfRenderer.Page.getTextContents()` (API 35+, the first 200 pages).
  - Other formats (.doc, .odt, spreadsheets), and PDFs on API 30–34, are skipped rather than counted as unreadable.
- **SimHash:** text is normalised to lower-case words of letters and digits in any script, then a 64-bit SimHash is taken over 3-word shingles (FNV-1a with a MurmurHash3 finaliser). It is stored as `SIMHASH`.
  - Under 20 words is stored as 0 and never matches, so short or empty files can't all group together.
- **Fixed threshold of 3 bits**, not the image setting, because 10 bits is far too loose for 64-bit SimHash.
  - In practice this matches the same text in another layout, format or set of metadata. On an API 36 emulator, two PDFs of one report on A4 and Letter (different bytes) scored 0, and an unrelated text 41.
  - Edits move more than that. One changed sentence in an 83-word text scored 9; two template letters differing only in name and amount scored 7. Neither matches, which is the conservative side.
- **Album tags are not used:** the same song often sits on a single and an album.

**Audio (P5-05):**
- **Signature:** duration plus keys of the title and artist tags (artist, else album artist), read with `MediaMetadataRetriever`. Stored as `AUDIO_DURATION`, `AUDIO_TITLE` and `AUDIO_ARTIST` rows; an untagged file stores only its duration.
- **Title:** a file with no title tag uses its file name.
  - Tag titles and file names are normalised alike: lower-case words, with leading track numbers dropped.
  - A title made only of generic words ("track", "recording", "audio", "untitled", …) or words containing digits ("AUD-20240101-WA0003") has no key and never matches.
- **Matching:** two files match when the title keys are equal, the artists are equal or unknown on either side, and the durations are within 2 s or 1 %. Clips under 30 s (ringtones, notifications, voice notes) are left out.
- **Device check:** on the emulator, a tagged MP3 (`rip-0042.mp3`, "Night Drive" by "The Band", 39.9 s) and an untagged AAC `02 - Night Drive.m4a` (40.1 s) matched. A different title at the same length and the same title at 59.8 s didn't.
- **Limits:**
  - Two different songs with the same title, no artist on one side and the same length would match.
  - Acoustic similarity is left to the AI tier (P6).
  - The keeper ranking doesn't use bitrate yet, so a group can suggest the lower-quality copy (P5-06).

**Near-duplicate UI (P5-06):**
- Results has tier chips (shown when more than one tier has groups). Near and AI groups carry a "Similar · 96%" tag and read "N similar files".
  - The similarity is rounded down and capped at 99%, so a near group never reads as identical.
  - Group detail repeats the tag and warns that the files aren't identical.
- **"Select duplicates" under "All" tiers selects exact copies only.** Similar files are selected only when the Similar (or AI) chip is chosen.
  - Bulk-deleting look-alikes such as bursts and edits is the riskiest thing the app can do, so it takes a deliberate choice.
  - Changing the tier chip clears the selection.
- **Compare (image groups):** any non-keeper file opens next to the keeper.
  - Two modes: side by side, or a wipe slider whose divider follows an M3 `Slider`.
  - One shared zoom and pan: pinch, mouse wheel, double-tap to reset, and zoom buttons. Images decode at up to 2048 px.
- **Settings → Similar files:** sets the Hamming threshold within 4–12 bits, saved when the slider is released.
  - It applies from the next scan, because grouping runs during the scan.
  - Documents keep their fixed SimHash threshold.


## D-013: Release build (accepted, P7-05)
**R8 over the whole app.** The template's `optimization {}` block stays, but its `packageScope` (only `androidx.**`, `kotlin.**`, `kotlinx.**`) is removed.
- Android's guidance treats `packageScope` as a first step for apps adopting R8, not as a release setup. Under it, the app and LiteRT-LM were never shrunk or renamed: the mapping had no `com.vipuljha` or `com.google.ai.edge` entries.
- Keep rules live in `app/src/main/keepRules/*.keep`, the AGP 9.3+ source set. LiteRT-LM ships no consumer rules and its native library reaches Java by name, so `litertlm.keep` keeps `com.google.ai.edge.litertlm.**` whole.
- Room, Hilt, WorkManager and kotlinx.serialization use their own consumer rules. The release smoke test exercised all of them, and none needed more.
- **Result:** dex went from 7.7 MB to 4.5 MB (APK 57.7 MB to 54.5 MB; the LiteRT-LM native libraries are most of the rest), and 687 of 693 app classes are renamed.

**No backup or transfer.** `allowBackup="false"`, and `data_extraction_rules.xml` excludes every domain from cloud backup and device transfer.
- The database holds file names, paths and embeddings, so cloud backup would have broken "nothing leaves the device".
- The index is also device-specific, and models are hundreds of MB.

**Signing:** the release build signs with `keystore.properties` at the repo root (store file, passwords, alias), which `.gitignore` excludes along with `*.jks`. Without that file the release APK is left unsigned.
- The keystore in `signing/` was generated locally for P7-05. The owner should back it up, or replace it with their own key before the first Play upload. Play App Signing can manage the app signing key.

**Privacy:** the note is `docs/PRIVACY.md`. The AI screen now says the model is downloaded once from Hugging Face.
