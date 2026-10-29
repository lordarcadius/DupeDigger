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

The 270M and 740M downloads matched their SHA-256 exactly. I did not download the 440M file; its size and hash come from the Hugging Face API.

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

## D-007: Never auto-delete; always keep one (accepted)
Deletion is always user-initiated and confirmed. Media goes to the system trash, and non-media to an in-app quarantine. A group can never be emptied. This is enforced in both the use case and the UI.

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
1. higher quality (pixels or bitrate), once measured; it is not measured yet and is equal within an exact group anyway;
2. location: a file under `DCIM/` beats one in an unremarkable folder, which beats a copy under `WhatsApp/`, `Telegram/`, `Download(s)/`, `Messenger/`, `Android/media/`, `.thumbnails/` or `cache/`, matched on whole folder names;
3. the oldest modification time, with an unknown time (0) ranked last;
4. `sourceUri`, so the result is always the same.

A saved keep choice wins while that file is still in the group.
