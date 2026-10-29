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

## D-004: Storage access strategy (open, resolved by P0-05)
Options:
1. **MediaStore + SAF only.** Play-safe. Covers media device-wide and user-picked folders, but SAF can't pick the storage root or `Download/` on API 30+.
2. **`MANAGE_EXTERNAL_STORAGE`.** True whole-device scan of every file type. Play allows it only for approved use cases (file managers and similar), so a declaration is needed and it may be rejected.
3. **Hybrid (leaning):** default to 1. Offer 2 as an explicit "Deep scan" toggle with an explainer. If Play distribution needs it, ship a flavor without it.

**Needs from the owner:** is Play Store distribution a goal, or is sideload/F-Droid acceptable?

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

## D-006: LiteRT-LM embedding integration (open, resolved by P0-04)
Known from the docs (not yet verified on a device):
- Artifact: `com.google.ai.edge.litertlm:litertlm-android`. Pin an exact version.
- API: `EmbeddingEngineConfig(modelPath, backend, visionBackend, audioBackend)`, `EmbeddingEngine(config).initialize()`, `computeEmbedding(listOf(InputData.Text|Image|Audio))`.
- Model `litert-community/embeddinggemma-2-740m-litert-lm`: Apache-2.0, 485 MB, a shared 768-dim space with output sizes of 128/256/512/768, and inputs of text (up to 8192 tokens), image and audio.
- Text task prefix for similarity: `task: sentence similarity | text: `.
- Init takes up to ~10 s. RAM was ~500–800 MB in the published benchmarks.

To verify: the API names, how the output dim is chosen, whether vectors come normalised, real RAM and latency on a mid-range device, and that the download URL is ungated, along with its checksum.

## D-007: Never auto-delete; always keep one (accepted)
Deletion is always user-initiated and confirmed. Media goes to the system trash, and non-media to an in-app quarantine. A group can never be emptied. This is enforced in both the use case and the UI.

## D-008: Build baseline (accepted)
Keep the template's minSdk 31, compile/target 37, AGP 9.5 built-in Kotlin and the `optimization {}` release DSL. Move to a Java 21 toolchain in P0-01. Hilt and Room use KSP only.
