# Progress

Status only. Task scope and acceptance criteria are in [PLAN.md](PLAN.md).
Statuses: `todo` · `in-progress` · `blocked` · `done`

**Current phase:** P0, project setup and spikes
**Next task:** P0-05

## Status

| ID | Task | Status | Notes |
|---|---|---|---|
| P0-01 | Build setup (toolchain 21, full stack deps) | done | material3 1.5.0-beta02, LiteRT-LM 0.18.1 |
| P0-02 | App skeleton (Hilt app, Timber, WorkManager config) | done | |
| P0-03 | Spike: hashing algorithm | done | SHA-256, 16 KiB partial window (D-005) |
| P0-04 | Spike: LiteRT-LM embeddings | done | CPU default, 70 vision tokens (D-006) |
| P0-05 | Spike: storage reach | todo | |
| P1-01 | Outcome / DomainError / dispatchers | todo | |
| P1-02 | MviViewModel, UiText, ContentState | todo | |
| P1-03 | Design system (M3 Expressive) | todo | |
| P1-04 | Navigation 3 host + adaptive nav | todo | |
| P1-05 | Room DB + DataStore | todo | |
| P2-01 | Onboarding + permissions | todo | |
| P2-02 | StorageAccess + ScanScope + SAF picker | todo | |
| P2-03 | File enumerators | todo | |
| P2-04 | Incremental indexer | todo | |
| P3-01 | Size → partial → full hash | todo | |
| P3-02 | ScanWorker (foreground, resumable) | todo | |
| P3-03 | Grouping + SuggestKeeper | todo | |
| P3-04 | DeleteDuplicates (safe) | todo | |
| P4-01 | Home screen | todo | |
| P4-02 | Scan screen | todo | |
| P4-03 | Results screen | todo | |
| P4-04 | Group detail screen | todo | |
| P4-05 | Delete flow + Quarantine screen | todo | |
| P5-01 | Image dHash/pHash | todo | |
| P5-02 | BK-tree/LSH + union-find | todo | |
| P5-03 | Video keyframe hashing | todo | |
| P5-04 | Document SimHash | todo | |
| P5-05 | Audio heuristics | todo | |
| P5-06 | Near-duplicate UI | todo | |
| P6-01 | Model catalog + download worker | todo | |
| P6-02 | EmbeddingEngine wrapper | todo | |
| P6-03 | Embedding scan stage | todo | |
| P6-04 | Cosine similarity grouping | todo | |
| P6-05 | AI settings UI | todo | |
| P7-01 | Performance + baseline profile | todo | |
| P7-02 | Adaptive layouts | todo | |
| P7-03 | Accessibility | todo | |
| P7-04 | Test coverage | todo | |
| P7-05 | Release build | todo | |

## Log

Newest first. One entry per session: `YYYY-MM-DD: <task IDs>: what was done; what's next; blockers.`

- 2026-10-29: P0-04: confirmed the LiteRT-LM 0.18.1 embedding API and ran the 270M and 740M models (SHA-256 verified) on a Primebook (MT8781, Android 15), on CPU and GPU. Vectors come back normalised; `outputSize` gives 128/256/512/768. CPU is faster and much lighter than GPU on this Mali; 70 vision tokens cuts image latency from 6.8 s to 2.9 s. The text prefix is corrected to `task: sentence similarity | query:`. D-006 holds the URLs, revisions, checksums and numbers; ARCHITECTURE.md's AI section is updated. The benchmark was a throwaway instrumented test and is not in the repo. Next: P0-05.
- 2026-10-29: P0-03: benchmarked SHA-256 against XXH3-128 (hash4j) and 4/16/64 KiB partial windows on a Primebook (MT8781, Android 15), with cold caches, over 1000 MiB. Chose SHA-256 (about 1.3 GB/s, storage-bound) and a 16 KiB head+tail window; numbers in D-005. The benchmark was a throwaway instrumented test and is not in the repo. Next: P0-04.
- 2026-10-29: P0-02: `DupeDiggerApp` (@HiltAndroidApp, Timber DebugTree in debug only, `Configuration.Provider` with `HiltWorkerFactory`), manifest registers it and removes the default `WorkManagerInitializer`; `MainActivity` is @AndroidEntryPoint and calls `enableEdgeToEdge()` before `super.onCreate()`; `buildConfig` enabled. Builds pass; the debug APK cold-starts on a Primebook (API 35) with no crash. Hilt KSP works on Kotlin 2.4.21 without `kotlin-metadata-jvm`. Next: P0-03.
- 2026-10-29: P0-01: Java 21 toolchain replaces `compileOptions`; catalog and app gain KSP 2.3.12, Hilt 2.60.1, androidx.hilt 1.4.0, Room 2.8.5 (+ Gradle plugin, schemas in `app/schemas`), DataStore 1.3.0, Navigation 3 1.2.0, lifecycle-viewmodel-navigation3, kotlinx.serialization 1.12.0, collections-immutable 0.5.2, Coil 3.6.3 (+ video, no network module), WorkManager 2.12.0, Paging 3.5.3, Timber 5.0.1, material-icons-extended (BOM), material3 pinned at 1.5.0-beta02 (BOM maps 1.4.0), LiteRT-LM 0.18.1. AGP, Kotlin and BOM unchanged; no kotlin-android, no kapt. `assembleDebug assembleRelease` and `testDebugUnitTest` pass. Next: P0-02.
- 2026-10-29: planning: created AGENTS.md, PLAN.md, ARCHITECTURE.md, PROGRESS.md and DECISIONS.md. No code changes yet. Next: P0-01.
