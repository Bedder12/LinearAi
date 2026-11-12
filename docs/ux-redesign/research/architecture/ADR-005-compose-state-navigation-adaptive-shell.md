# ADR-005 — Compose state ownership, navigation, and adaptive shell

**Status:** Proposed architecture direction; implementation review required
**Decision track:** S02-E
**Code baseline:** `86e5ecdd65bbc127f2da49c03c00732f9ca2b124`
**Reviewed HEAD:** `35e006a81fb6ffc83f0cf95d5d19a7a94bcca4c0`

## Context and verified current behavior

- `MainActivity` directly renders one `MathWorkspaceScreen`; no navigation host or route model is present.
- `MathWorkspaceScreen` constructs the analysis repository from the network singleton and obtains a workspace ViewModel. The ViewModel owns both in-memory `StudentWork` and UI state such as selected tool and sync status.
- `InkSurface` remembers renderer/hit-test objects in composition; in-progress input state is not a durable document source of truth. S00 confirmed unsynced work can be lost after process death.
- Existing tests cover selected tool, basic workspace state, and sync races, not navigation, process recreation, responsive width rules, or focus return.
- The current module has Compose, lifecycle ViewModel Compose, and Material3; Navigation Compose and a persistence layer are not dependencies.

Evidence: [MainActivity.kt](../../../../app/src/main/java/com/example/myapplication/MainActivity.kt), [MathWorkspaceScreen.kt](../../../../app/src/main/java/com/example/myapplication/MathWorkspaceScreen.kt), [MathWorkspaceViewModel.kt](../../../../app/src/main/java/com/example/myapplication/ui/workspace/MathWorkspaceViewModel.kt), [InkSurface.kt](../../../../app/src/main/java/com/example/myapplication/ink/InkSurface.kt), [S00 audit](../current-state-audit.md), and [S01 responsive rules](../ux-spec.md).

## Proposed ownership boundaries

Keep the architecture small and explicit:

```text
Activity / AppRoot
  ├─ navigation and back stack (stable documentId route arguments)
  ├─ app container: database, local repositories, API and service construction
  └─ destination UI
       └─ document-scoped ViewModel
            ├─ observes durable repository state
            ├─ exposes immutable screen state and user actions
            └─ delegates sync / analysis to separate coordinators

LocalDocumentRepository (Room-backed after S03)
  └─ owns durable document/page/stroke/revision/outbox state
SyncCoordinator / SyncRepository
  └─ owns delivery, retry, reconciliation and acknowledged status
AnalysisRepository
  └─ owns revision-bound analysis requests/results, not ink acknowledgment
Compose UI
  └─ owns ephemeral rendering/composition only; no source-of-truth ink
```

Use stable `documentId` navigation arguments and a destination/back-stack-scoped ViewModel per active document. Route transitions must not create a replacement document or share mutable work state across different document IDs. Repositories return domain models/flows; Room entities and Retrofit DTOs do not escape their data layers.

Construct infrastructure in an explicit app container and pass dependencies through ViewModel factories. Do not add a dependency-injection framework solely for this slice. Navigation Compose is a reasonable option for the planned multi-screen/deep-link flow, but it is not installed today; confirm dependency and route-state implications before adding it. Until multiple routes are implemented, a small single-activity route owner remains a viable alternative.

The ViewModel exposes screen state and dispatches operations; it does not instantiate network clients or persistence. Durable content comes only from the repository. Selected tool, panel expansion, active page, and viewport are UI/workspace state, not content revisions. Keep lightweight state in the document-scoped ViewModel or saved state as appropriate; never use `rememberSaveable` as a replacement for durable strokes/outbox. Persist active-page/viewport preferences only if product requires restoration across process death.

## Adaptive layout contract

Use S01's available content width after system insets, not device type:

- Compact `<600 dp`: no persistent rail; one temporary page/review panel at a time.
- Medium `600–839 dp`: canvas primary; one temporary panel.
- Expanded `840–1199 dp`: page rail may persist; Review replaces/collapses it.
- Wide `>=1200 dp`: rail and Review may coexist only while at least `560 dp` remains for the canvas.

Rotation, split-screen, fold, IME, and inset changes recompute layout without mutating document/revision/outbox or resetting the selected tool, active page, or viewport anchor. Use stable keys for document/page state; derive layout from current constraints. Respect S01's 48 dp target, TalkBack order, Back behavior, and focus restoration. The current toolbar/status-bar overlap remains an S00 finding until the responsive/inset implementation and device checks pass.

## Alternatives and risks

- Keep one screen and one Activity-scoped ViewModel: minimal now, but risks cross-document state leakage and cannot provide destination-scoped back behavior as Home/Library/Trash arrive.
- Add Navigation Compose with document-keyed destinations: fits the target flows and stable IDs, but adds a dependency and requires lifecycle/recreation tests.
- Store the entire document in `SavedStateHandle`/`rememberSaveable`: rejected as durable storage; saved UI state is not an event journal or reliable process-death persistence.
- Add a DI framework: unnecessary indirection for the current app size; start with a small explicit app container and revisit only when module/testing needs justify it.

Primary risks are ViewModel-owned document data, composition-owned in-progress ink, direct infrastructure construction in a screen, and treating width changes as document changes.

## Acceptance tests and implementation gates

- Navigating away/back and switching document IDs preserves the correct persisted work and never shares state between documents.
- Configuration recreation restores screen/tool/active-page/viewport state from the agreed owner; process kill restores document/outbox only after durable commit.
- Width boundary tests at 600/840/1200 dp verify rail/panel rules and >=560 dp canvas at wide size; verify insets, fold, split-screen, rotation, and IME behavior.
- Panel/layout changes do not increment document revision, change outbox, create sync requests, or alter canonical stroke coordinates.
- Back dismisses the topmost overlay before navigation; focus returns to its invoking control; TalkBack traversal and touch targets meet S01.
- Keep the toolbar inset defect and physical stylus uncertainty visible until measured verification; no test is claimed by this ADR.

No navigation dependency, production UI refactor, or state migration is approved by this proposal.
