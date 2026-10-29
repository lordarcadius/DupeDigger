# AGENTS.md — Dupe Digger

Instructions for any AI agent or contributor working in this repo. Read this file fully before writing code.

## What this app is

Dupe Digger is an Android app that finds duplicate and near-duplicate files (images, video, audio, documents, archives, anything) across the whole device or in folders the user picks. It works fully on-device, with no network calls except the optional AI model download.

Detection runs in three tiers. Each tier has its own confidence label and threshold:

| Tier | Finds | How |
|---|---|---|
| **Exact** | Byte-identical files | size → partial hash (head + tail + size) → full streaming hash |
| **Near-identical** | Re-encoded, resized or re-saved copies | perceptual hashes (dHash/pHash, video keyframes, SimHash for text) + Hamming distance |
| **Semantic (AI)** | Similar shots, the same content in a different format | on-device EmbeddingGemma embeddings + cosine similarity (opt-in) |

## Docs map

| File | Purpose |
|---|---|
| [docs/PLAN.md](docs/PLAN.md) | Roadmap: phases, task IDs, acceptance criteria. **The source of truth for what to build.** |
| [docs/PROGRESS.md](docs/PROGRESS.md) | Status for each task ID, plus a dated session log. |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Package layout, the detection pipeline, the data model, storage access and deletion. |
| [docs/DECISIONS.md](docs/DECISIONS.md) | Decision log (ADR-lite) and open questions. |

## Session workflow

1. Read `docs/PROGRESS.md` to find the current phase and the next `todo` task. Read that task in `docs/PLAN.md`.
2. Work on **one task ID at a time**. Don't start tasks from a later phase unless the plan marks them as parallel.
3. Before finishing a session:
   - set the task's status in `docs/PROGRESS.md`;
   - add a dated line to the log there (what was done, what's left, any blocker);
   - record any non-obvious choice in `docs/DECISIONS.md`.
4. If the plan turns out to be wrong, change `docs/PLAN.md` in the same change and say why in the log. Never let the plan and the code silently drift apart.

## Build and verify

```bash
./gradlew assembleDebug          # build
./gradlew testDebugUnitTest      # JVM unit tests
./gradlew lint                   # Android lint
./gradlew connectedDebugAndroidTest   # instrumented tests (device/emulator needed)
```

Run the smallest relevant check first, then widen it. Never claim a check passed unless you ran it.

## Stack

- Kotlin, AGP 9 with **built-in Kotlin**. Never apply `org.jetbrains.kotlin.android`.
- Jetpack Compose + **Material 3 Expressive**. Pin `material3` above the BOM for the Expressive APIs.
- Hilt (on **KSP**, never kapt), Room, Preferences DataStore, Navigation 3, WorkManager.
- kotlinx.serialization (Nav keys), kotlinx-collections-immutable, Coil 3 (thumbnails), Timber.
- LiteRT-LM (`com.google.ai.edge.litertlm:litertlm-android`) for on-device embeddings. Pin an exact version, never `latest.release`.
- minSdk 31, target/compile SDK 37.

Versions live only in `gradle/libs.versions.toml`. Don't bump AGP, Kotlin or any library as a side effect of another task.

## Architecture rules

Single `:app` module, with Clean Architecture layers as packages under `com.vipuljha.dupedigger`. The full layout is in [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

### Layers
- **Dependency rule:** `presentation → domain ← data`, wired by Hilt. Presentation sees only domain types, never an entity, DAO, `File` wrapper or repository implementation.
- **Domain is plain Kotlin:** no `android.*`, Compose, Room or LiteRT imports, and no `Uri`, `Bitmap` or `Context`. Check before finishing:
  ```bash
  grep -rnE '^import (android|androidx|com\.google\.ai)' app/src/main/java/com/vipuljha/dupedigger/domain || echo "domain clean"
  ```
- **Business rules live in use cases** (for example "which file to keep" or "group by threshold"), never in a ViewModel or composable.
- **Map at the boundaries:** entity → domain model inside `data/`; domain model → `@Immutable` UI model (with display-ready strings and sizes) through `toUiModel()` in the ViewModel.
- **Results:** `Outcome<T>` = `Success(data)` | `Failure(DomainError)`. A result carries no user-facing strings and no `Loading` case, because loading is UI state.

### Coroutines
- **Never swallow `CancellationException`.** No bare `runCatching` or `catch (e: Exception)` around suspend code. Use `runSuspendCatching`, or rethrow cancellation first. Scans are long and cancellable, so this matters.
- **Inject dispatchers** with `@IoDispatcher`, `@DefaultDispatcher` and `@MainDispatcher` qualifiers. Never hardcode `Dispatchers.IO`. Hashing and image decoding are CPU-heavy (`@DefaultDispatcher`); file reads are IO.
- **Scopes:** screen work runs in `viewModelScope`. Work that outlives a screen runs in the injected `@ApplicationScope`. Scans and model downloads must survive process death, so they run in **WorkManager**. Never use `GlobalScope` or a hand-made `CoroutineScope(...)`.
- Repositories return cold `Flow`s. Never `emit` inside `try/catch` in a `flow {}` builder. Every `catch {}` operator emits a fallback or rethrows.

### MVI and state
- Every screen ViewModel extends `MviViewModel<S : UiState, I : UiIntent, E : UiEffect>`, with a single `onIntent(intent)` entry point.
- **One `@Immutable data class` state per screen**, exposed as a read-only `StateFlow`. Change it only through `setState { copy(...) }`.
- State, UI models and domain models hold `ImmutableList`, `ImmutableSet` or `ImmutableMap`, never `List` or `MutableList`.
- Model phases with sealed types (`ContentState`), not with booleans that can contradict each other.
- Text reaches state and effects as `UiText`, never as a literal string or a raw `@StringRes Int`.
- One-shot effects use `Channel(Channel.BUFFERED).receiveAsFlow()` and are collected with `ObserveAsEvents`. Never use `MutableSharedFlow` for events, never LiveData, and never a navigation flag in state.
- A ViewModel injects use cases (plus `SavedStateHandle`), never a DAO, `Context`, `DataStore` or data-layer class.

### Compose
- **Route/Screen split.** `XxxRoute(onNavigate..., viewModel = hiltViewModel())` collects state with `collectAsStateWithLifecycle()` and effects with `ObserveAsEvents`, then calls the stateless `XxxScreen(state, onIntent)`. The ViewModel never goes below the Route.
- Composables draw; they don't compute. No formatting, sorting or filtering in a composable. `modifier` is the first optional parameter and is applied once, to the root.
- Every user-visible string, `contentDescription` included, comes from string resources. Decorative icons pass `null`.
- Lazy lists: every item gets a stable `key` (file id or group id, never its index) and a `contentType`. Duplicate groups can number in the thousands, so use Paging 3 where a list is unbounded.
- A value that changes many times per second (scan progress) is passed as `() -> T` and read where it is drawn.
- UI follows **Material 3 Expressive**: use the expressive motion scheme, shape morphing, `LoadingIndicator`/wavy progress, emphasized type, and dynamic color with a branded fallback. Every screen handles its loading, empty and error states.

### Navigation, DI, data
- Navigation 3 with `@Serializable` keys holding only ids and flags. The back stack lives in the host. Routes receive lambdas (`onOpenGroup`, `onBack`) and never see the back stack. Key arguments reach a ViewModel through assisted injection.
- Hilt: constructor injection first. Modules only `@Binds` (abstract class) or `@Provides` types you don't own (object). Qualifiers are typed annotations, never `@Named`.
- App-wide singletons: the Room database, each DataStore, the Coil `ImageLoader` and the LiteRT `EmbeddingEngine`. The engine is heavy (hundreds of MB of RAM), so load it lazily and close it when idle.
- **Room migration policy.** The DB has two kinds of tables:
  - *Index/cache* tables (file index, hashes, embeddings) can be rebuilt by rescanning.
  - *User-decision* tables (ignored groups, keep choices, quarantine) hold user data and need real `Migration`s plus a migration test.
  - Use `exportSchema = true` always. Never `fallbackToDestructiveMigration` on the user-decision tables.
- **Timber only**, with `DebugTree` planted in debug builds only. Never log full file paths of user content in release builds. No `Log.d` and no `println`.

## Safety rules (non-negotiable)

- **Never delete automatically.** Every deletion is an explicit user action, with a confirmation that shows the count and total size.
- **Always keep at least one file per duplicate group.** The UI and the use case must both make "delete all copies" impossible.
- Media is deleted through `MediaStore.createTrashRequest` (recoverable). Non-media goes to an in-app quarantine with undo before any permanent delete.
- Read files without modifying them. Never write to a scanned file.
- Skip `Android/data`, `Android/obb` and app-private dirs. Don't follow symlinks out of the scan root.

## Code style

- Default to no comments. Add one only for a non-obvious reason, constraint or gotcha, in at most one line. Never narrate history in a comment.
- Clear names, small focused functions, no speculative abstractions.
- Change only what the task needs. No drive-by refactors, renames or version bumps.
