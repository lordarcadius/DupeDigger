# Progress

Status only. Task scope and acceptance criteria are in [PLAN.md](PLAN.md).
Statuses: `todo` · `in-progress` · `blocked` · `done`

**Current phase:** P2, storage access and file indexing
**Next task:** P2-01

## Status

| ID | Task | Status | Notes |
|---|---|---|---|
| P0-01 | Build setup (toolchain 21, full stack deps) | done | material3 1.5.0-beta02, LiteRT-LM 0.18.1 |
| P0-02 | App skeleton (Hilt app, Timber, WorkManager config) | done | |
| P0-03 | Spike: hashing algorithm | done | SHA-256, 16 KiB partial window (D-005) |
| P0-04 | Spike: LiteRT-LM embeddings | done | CPU default, 70 vision tokens (D-006) |
| P0-05 | Spike: storage reach | done | Hybrid access; matrix in D-004 |
| P1-01 | Outcome / DomainError / dispatchers | done | |
| P1-02 | MviViewModel, UiText, ContentState | done | |
| P1-03 | Design system (M3 Expressive) | done | Placeholder brand seed (D-009) |
| P1-04 | Navigation 3 host + adaptive nav | done | Drawer / rail / panel, no bottom bar |
| P1-05 | Room DB + DataStore | done | One DB, schema v1 exported (D-010) |
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

- 2026-10-29: P1-05: added `DupeDiggerDatabase` v1 (all 11 ARCHITECTURE.md tables, cascading deletes from `file_index`, DAOs, `exportSchema` with `app/schemas/.../1.json`, `ALL_MIGRATIONS` plus a `MigrationTestHelper` harness that checks user decisions survive) and DataStore `SettingsRepository` (domain model + interface, clamped values). `duplicate_group` gained a `fingerprint` column; PLAN.md P1-05 drops the theme setting and moves excluded paths to Room (D-010). Tests: 3 new JVM settings tests pass (13 unit tests total); on the Primebook, `DaoTest` (5) and `DatabaseMigrationTest` (1) pass; domain purity grep clean. Next: P2-01.
- 2026-10-29: P1-04: added `presentation/navigation` (`@Serializable` `Destination` keys, `open/back/backTo`, `TopLevelTab`, `AppRoot` with one decorated back stack per tab, placeholder screens) and the design-system shell (`AppShell` field and pane, drawer/rail/resizable panel with seam, `NavRow`, `AppTopBar`, tooltip icon buttons, popup content-bounds clamping, push/pop/fade-through transitions); window background follows the chrome tone; predictive back enabled. PLAN.md P1-04 now says drawer/rail/panel instead of "bottom bar or nav rail", because the house design has no bottom bar. Verified on an API 35 phone emulator: drawer opens and switches tabs, Back from Results returns to Home and Back on Home leaves the app, and the selected tab survives portrait→landscape→portrait while the drawer becomes the labelled panel. The panel width is kept only for the session (`rememberSaveable`); P1-05's DataStore can persist it. Next: P1-05.
- 2026-10-29: P1-03: added `core/designsystem` (`DupeDiggerTheme` on `MaterialExpressiveTheme` with expressive motion and dynamic colour, generated 48-role brand fallback schemes, Inter 4.1 with its OFL text, `AppShapes`, `AppTypography` + `SectionLabelStyle`, `Dimens`/`StateAlpha`/`WindowBreakpoints`/`Motion.kt` tokens, Expressive wrappers for loading indicator, wavy progress, connected button group and extended FAB, and a light/dark preview gallery); removed the template `ui/theme` and unused `colors.xml`. The gallery, shown temporarily as the app's content on the Primebook, rendered in light and dark. Next: P1-04, which also sets the window background tone.
- 2026-10-29: P1-01, P1-02: added `core/common` (`Outcome`, sealed `DomainError`, `runSuspendCatching`, dispatcher and `@ApplicationScope` qualifiers) with `di/DispatchersModule`, `core/mvi` (`MviViewModel`, `UiState/UiIntent/UiEffect`, `ObserveAsEvents`) and `core/ui` (`UiText`, `ContentState`, `DomainError.toUiText()` with error strings). Unit tests cover cancellation rethrow, atomic `setState` under 40k concurrent updates, effects delivered exactly once, and `ContentState` mapping; breaking `setState` or the cancellation rethrow made the matching tests fail. Added `kotlinx-coroutines-test` 1.11.0 (the resolved coroutines version). Next: P1-03.
- 2026-10-29: P0-05: measured what MediaStore, media permissions, partial access and `MANAGE_EXTERNAL_STORAGE` reach on API 31, 33, 34, 35 and 36 emulators, and what the SAF tree picker allows on API 35. D-004 is resolved as hybrid: MediaStore and SAF by default, opt-in Deep scan through `MANAGE_EXTERNAL_STORAGE`. Gaps: no SD card on any test device, and the Primebook shell cannot create test files on `/sdcard`. Installed the API 31/33/34 `google_apis` system images (kept for later steps); the spike AVDs and test code were removed. Next: P1-01.
- 2026-10-29: P0-04: confirmed the LiteRT-LM 0.18.1 embedding API and ran the 270M and 740M models (SHA-256 verified) on a Primebook (MT8781, Android 15), on CPU and GPU. Vectors come back normalised; `outputSize` gives 128/256/512/768. CPU is faster and much lighter than GPU on this Mali; 70 vision tokens cuts image latency from 6.8 s to 2.9 s. The text prefix is corrected to `task: sentence similarity | query:`. D-006 holds the URLs, revisions, checksums and numbers; ARCHITECTURE.md's AI section is updated. The benchmark was a throwaway instrumented test and is not in the repo. Next: P0-05.
- 2026-10-29: P0-03: benchmarked SHA-256 against XXH3-128 (hash4j) and 4/16/64 KiB partial windows on a Primebook (MT8781, Android 15), with cold caches, over 1000 MiB. Chose SHA-256 (about 1.3 GB/s, storage-bound) and a 16 KiB head+tail window; numbers in D-005. The benchmark was a throwaway instrumented test and is not in the repo. Next: P0-04.
- 2026-10-29: P0-02: `DupeDiggerApp` (@HiltAndroidApp, Timber DebugTree in debug only, `Configuration.Provider` with `HiltWorkerFactory`), manifest registers it and removes the default `WorkManagerInitializer`; `MainActivity` is @AndroidEntryPoint and calls `enableEdgeToEdge()` before `super.onCreate()`; `buildConfig` enabled. Builds pass; the debug APK cold-starts on a Primebook (API 35) with no crash. Hilt KSP works on Kotlin 2.4.21 without `kotlin-metadata-jvm`. Next: P0-03.
- 2026-10-29: P0-01: Java 21 toolchain replaces `compileOptions`; catalog and app gain KSP 2.3.12, Hilt 2.60.1, androidx.hilt 1.4.0, Room 2.8.5 (+ Gradle plugin, schemas in `app/schemas`), DataStore 1.3.0, Navigation 3 1.2.0, lifecycle-viewmodel-navigation3, kotlinx.serialization 1.12.0, collections-immutable 0.5.2, Coil 3.6.3 (+ video, no network module), WorkManager 2.12.0, Paging 3.5.3, Timber 5.0.1, material-icons-extended (BOM), material3 pinned at 1.5.0-beta02 (BOM maps 1.4.0), LiteRT-LM 0.18.1. AGP, Kotlin and BOM unchanged; no kotlin-android, no kapt. `assembleDebug assembleRelease` and `testDebugUnitTest` pass. Next: P0-02.
- 2026-10-29: planning: created AGENTS.md, PLAN.md, ARCHITECTURE.md, PROGRESS.md and DECISIONS.md. No code changes yet. Next: P0-01.
