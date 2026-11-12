# Linear AI — S01 UX specification

**Version:** 0.1
**Status:** Final S01 design baseline; product scope decisions resolved for S02 and implementation planning
**Date:** 2026-11-12
**Primary target:** Android tablets with stylus; phone portrait remains usable
**Initial learning domain:** University STEM, especially linear algebra

This specification incorporates the supplied UX proposal and the repository's S00 observations. It describes intended experience, not existing product capability. Source code, current audit evidence, and later approved decisions remain authoritative for what can ship.

## Evidence boundary

The current app is a single Compose writing workspace with Pen, Eraser, Clear, and Check. `Check` synchronizes ink revisions; it does not verify mathematics. Local work and pending changes currently live in ViewModel memory. No page-aware API or multi-page document model was observed.

The emulator audit observed loss of unsynchronized work after force-stop, an inconclusive Clear attempt (source shows no confirmation, but the captured stroke remained visible), an offline Check error, top toolbar hit targets overlapping the system status bar on one tablet AVD, and a possible extra eraser revision. The exact eraser event trace and physical stylus behavior remain unverified. See [current-state-audit.md](current-state-audit.md) and its [evidence/](evidence/) directory. Do not mark these findings fixed or imply S00 is complete.

Competitor products (Goodnotes, Samsung Notes, MyScript, Notion, Photomath) are inspiration only. Do not copy protected visual designs or present uncited competitor observations as research findings.

## Product principles

1. **Write first.** The current problem and student's work own the visual focus.
2. **Be honest about data.** Distinguish local durable save, pending upload, server-acknowledged revision, and sync error. Do not claim local durability before it exists.
3. **Separate Save/Sync from Check math.** Never label revision acknowledgment as mathematical correctness.
4. **Progressively disclose.** Page navigation and review panels can collapse; writing remains available.
5. **Bind feedback to a revision.** Analysis output identifies the exact document/page/revision it concerns and becomes stale after relevant edits.
6. **Keep core actions accessible without a stylus.** Use labels, semantic state, comfortable touch targets, and predictable focus.
7. **Specify behavior before visual polish.** Empty, loading, offline, conflict, error, and recovery behavior are part of every flow.
8. **Never advertise an inert action.** A design-only future capability must be omitted or unmistakably marked as a prototype.

## Information architecture

```text
Home / Library
├── Recent documents
├── Subjects (optional basic organization)
│   └── Notebooks
│       └── Documents
├── Favorites
├── Trash
└── Settings / Account (when implemented)

Document
├── ordered logical pages
├── vector strokes
├── viewport state
└── revision-bound analysis history (future)
```

Logical pages are intended to appear in a continuous vertical document. This depends on S02 contract decisions and S10 implementation; it is not permission to add fake page controls early. Do not build a generic Notion-like database/block system.

## Screen map and low-fidelity annotated layouts

The layouts are target-state wireframes. A future capability is not a shipping control until its behavior and dependencies are implemented and tested; where useful, it is labeled below.

### Home — H1

**Tablet**

```text
+--------------------------------------------------------------+
| Linear AI                                  Search   Settings |
| [Continue: Algebra / Homework 01]                             |
| Recent                                                       |
|  Algebra / Homework 01             Updated / local status    |
|  Calculus / Limits                 Updated / local status    |
| Subjects       Notebooks       Favorites                    |
+--------------------------------------------------------------+
```

**Phone**

```text
+----------------------------+
| Linear AI          Search  |
| [Continue working]         |
| Recent                     |
| Algebra / Homework 01      |
| Calculus / Limits          |
| [Library] [New document]   |
+----------------------------+
```

The Continue action reopens an existing local document; it must not silently create a new work-session identity. Empty, loading, and unavailable-document states need explicit next actions.

### Library — L1

**Tablet**

```text
+--------------------------------------------------------------+
| Home | Subjects | Notebooks | Favorites | Trash              |
| Algebra > Homework             Search   Sort   New document  |
| Document title     Last edited   Local / sync status         |
| Homework 01        Today         Awaiting sync                |
| Homework 02        Yesterday     Local                        |
+--------------------------------------------------------------+
```

**Phone**

```text
+----------------------------+
| [Back] Library      Search |
| [Subject / notebook v]     |
| Homework 01                |
| Today · Awaiting sync      |
| Homework 02                |
| Yesterday · Local          |
| [New document]             |
+----------------------------+
```

Search and organization should work offline over local data. Menu actions such as rename/move are enabled only when implemented. No cloud status is inferred from server revision acknowledgment.

### Mathematics Workspace — W1

**Wide tablet window (available content width at least 1200 dp)**

```text
+--------------------------------------------------------------+
| [Back] Algebra / Homework 01       [Local/sync status] [More]|
| [Pen] [Eraser]                                                |
|------------+----------------------------------+---------------|
| PAGES [<]  | PAGE 1                           |               |
| Page 1     |                                  |               |
|            |          Writing canvas          |               |
| [+ Page]   |          continuous pages        |               |
|------------+----------------------------------+---------------|
| Page position                         [Fit width] [Zoom]     |
+--------------------------------------------------------------+
```

The review panel is opened on demand; if open at this width it occupies the right side without removing the page rail or resetting the canvas. A single page rail item is real only after page identity and persistence ship. The layouts describe the approved target, not shipped capability.

**Phone portrait**

```text
+----------------------------+
| [Back] Homework 01    [⋯]  |
| [Pen] [Eraser] [More]      |
|                            |
|       Writing canvas       |
|                            |
| [Pages]  [Review] [Zoom]   |
+----------------------------+
```

The layouts above describe the approved target, not shipped capability. Until their dependencies ship, omit the Pages, Review, Fit width, and Zoom actions rather than exposing inert controls. The current supported writing tools remain Pen and Eraser. Clear lives in an overflow menu and requires confirmation (or a tested undo replacement) before it can destroy work. Sync status/action is separate from math analysis. The header, toolbar, and hit targets must respect system insets; the workspace must not reset strokes or tool selection when its layout changes.

### AI Review — R1

**Tablet**

```text
+--------------------------------------------------------------+
| [Back] Homework 01            Review · revision 12 [Close]   |
| [Pen] [Eraser]                 | Review panel                |
|------------------------------- | No math analysis available  |
|                                 | Sync status is separate     |
|          Same canvas            | Step feedback when verified |
|          and viewport           | Hint -> Explain -> Solution|
+--------------------------------------------------------------+
```

**Phone**

```text
+----------------------------+
| Homework 01      [Close]   |
|        Same canvas         |
|----------------------------|
| Review · revision 12       |
| Math analysis unavailable  |
| [Dismiss]                  |
+----------------------------+
```

Opening/dismissing review preserves the current document, selected tool, and canvas position; writing remains available. Results bind to an immutable analyzed revision. Any later edit marks the result stale without deleting it. Feedback is a chronological list of recognized steps with a hint first; explanation follows student request, and full solution is a further explicit action. Do not promise stroke/region anchors until recognition supplies reliable anchors. Unsupported or uncertain work abstains instead of receiving a correctness verdict. Live math feedback remains unavailable until recognition and deterministic verification exist. Any demo is visibly labeled “Demo”.

### Trash — T1

**Tablet**

```text
+--------------------------------------------------------------+
| Home | Subjects | Notebooks | Favorites | Trash              |
| Trash                                                       |
| Homework 03       Deleted date       [Restore] [Delete...]   |
| Empty state: No deleted documents                            |
+--------------------------------------------------------------+
```

**Phone**

```text
+----------------------------+
| [Back] Trash               |
| Homework 03                |
| Deleted date   [Restore]   |
|                            |
| Empty trash (when empty)   |
+----------------------------+
```

Trash, restore, retention, and permanent deletion require real persistence semantics. Permanent deletion is destructive and must be confirmed; do not imply a retention duration until implemented. The Library owns the Trash destination; restore returns to the original notebook when it exists, otherwise the default notebook, preserving document/page/stroke identities.

## Component hierarchy and navigation contract

These are logical UI responsibilities, not mandated package/class names. Keep route state and document editing state separate. A destination change must not recreate a document session or discard pending edits.

```text
AppRoot
├── AppScaffold
│   ├── PrimaryNavigation (Home / Library; Trash under Library)
│   └── NavHost
│       ├── HomeScreen
│       │   ├── HomeTopBar (Search, Settings)
│       │   ├── ContinueDocumentCard
│       │   └── RecentDocumentList
│       ├── LibraryScreen
│       │   ├── LibraryNavigation (Subjects, Notebooks, Favorites, Trash)
│       │   ├── LibraryToolbar (Breadcrumb, Search, Sort, New)
│       │   └── DocumentList (status, open, overflow)
│       ├── MathematicsWorkspaceScreen(documentId)
│       │   ├── WorkspaceHeader (Back, title, save/sync status, More)
│       │   ├── WorkspaceToolbar (Pen, Eraser; Clear in confirmed overflow)
│       │   ├── WorkspaceBody
│       │   │   ├── PageNavigator (when page model exists)
│       │   │   ├── ContinuousPageCanvas (page-local ink surfaces)
│       │   │   └── AIReviewPane (on demand)
│       │   └── WorkspaceFooter (active page; viewport controls when supported)
│       ├── AIReviewScreen(documentId, analyzedRevision)
│       │   ├── ReviewHeader (revision, freshness, close)
│       │   ├── AnalysisState
│       │   └── StepFeedbackList (hint, requested explanation, requested solution)
│       └── TrashScreen
│           ├── TrashList
│           └── Restore / confirmed permanent-delete actions
└── Global overlays (confirmation, conflict/recovery details, transient messages)
```

### Navigation rules

- Home is the launch destination. Continue opens the last available local document; New creates one document exactly once and then navigates to its workspace. Both depend on local document persistence.
- Library lists locally available documents first. Selecting a row opens the same document identity. Back from workspace returns to the invoking Home/Library context. Moving between destinations preserves the active document and pending journal.
- Trash is a Library destination. Restore returns to the original location or a defined default; permanent deletion requires a destructive confirmation that names the document.
- AI Review is subordinate to a document and analyzed revision. On wide windows it is a collapsible workspace pane; on narrower windows it is a modal bottom sheet or dedicated route. Closing returns focus to the invoking review action and leaves the workspace unchanged.
- System Back dismisses a modal/pane first, then returns to the previous destination. It never implicitly clears, syncs, deletes, or creates work.
- Search results and deep links resolve stable document IDs. An unavailable document produces a recoverable error and never silently creates a replacement.

## Responsive and interaction rules

- Choose layout from available **content width in dp after system insets**, not device name. Use these initial design thresholds, then verify them against real window sizes in S06:
  - **Compact: <600 dp.** No persistent side rail. Pages opens in a modal drawer/sheet; AI Review opens in a bottom sheet or dedicated route. Only one overlay is open at a time.
  - **Medium: 600–839 dp.** Canvas is primary. Either the page navigator or review may open as a temporary side panel/sheet; never show both beside a cramped canvas.
  - **Expanded: 840–1199 dp.** A collapsible page rail may remain visible. Opening Review collapses the rail and gives the canvas/panel the available width.
  - **Wide: >=1200 dp.** Page rail and Review may coexist only if at least 560 dp remains for the canvas after panes, insets, and spacing. Otherwise apply the expanded-width single-panel rule.
- At expanded/wide sizes, writing starts with the page rail open only after real page data exists; Review stays closed until requested or showing a relevant result. On smaller sizes both panels are transient. A panel toggle must not reset document, selection, active page, or viewport.
- Focus mode collapses the page rail and Review pane; persistent expand affordances remain accessible. Opening Review at wide width preserves the rail when the canvas minimum is met.
- Rotation, split-screen resize, IME, and fold changes must not clear ink, create revisions, trigger sync, change selected tool, or force orientation.
- Any implemented touch action has at least a 48 dp target. Include system-bar insets and cutouts in layout measurement; do not put tappable content underneath system UI. This responds to the S00 tablet finding; the exact edge-to-edge/inset remedy is an implementation decision for S06.
- Pen remains the default. Finger draws when Pen is selected so phone use and touch-only access remain possible. Do not claim palm rejection. Two-finger pan/zoom is deferred to S12 and must not be enabled until gesture arbitration and transformed hit-testing are tested.
- Keep viewport and page-coordinate transformations an explicit future contract. Zoom must not scale stored ink or cause eraser drift.
- Toolbar contains only implemented, core writing tools: Pen and Eraser, with text labels and selected state. Header contains document/back/status/More. Clear is a destructive More-menu action requiring confirmation; it is not Delete Page or Trash. No visible dead controls for Hand, Selection, Keyboard, Color, Undo, or Redo.
- Sync status/action is distinct from Check Math. Before durable local persistence, status must disclose that edits exist only in the current app session; never call that “saved on device.” Server revision acknowledgment means only that ink events were accepted.
- Focus returns to the invoking control after dismissing a dialog, drawer, or Review sheet. Status updates are announced politely and do not steal writing focus.

## Primary journeys

| ID | Entry | Primary actions | Alternate path | Error/recovery | Exit and required feedback |
|---|---|---|---|---|---|
| J01 Launch/resume | App icon or Continue | Open recent document | First use -> create | Missing/unavailable doc; process killed before durable save | Enter workspace; accurately show local and remote state. |
| J02 Create/organize | Home -> New | Name document, optionally choose notebook, write | Cancel or use default notebook | Empty/duplicate title; offline | Return to document/library without accidental duplicate. |
| J03 Write/erase | Workspace | Select Pen, draw; select Eraser, remove intended stroke | Touch-only or stylus input | Duplicate eraser events; palm/touch ambiguity | Keep selection visible; one logical removal; no invalid event. |
| J04 Navigate pages | Workspace page navigator | Add page and scroll between pages | Collapse navigator; select thumbnail | Failed page save; last-page removal; resize | Preserve order, active page, boundaries, and ink. |
| J05 Zoom/pan | Workspace canvas | Pan/zoom, fit width, draw/erase | Hand tool or pinch gesture | Mixed pointer types; transform/hit-test drift | Preserve page-local geometry and visible zoom state. |
| J06 Save/offline/recover | Workspace status/action | Write offline, save locally, reconnect, sync | Retry or inspect conflict | Lost/ambiguous ack; gap; conflict; process death | Explicitly distinguish durable local, uploading, server-acknowledged, needs attention. |
| J07 Review math | Workspace -> Review | Request analysis; inspect step hint; continue writing | Correct recognition or request explanation | Unsupported/uncertain recognition, stale result, service failure | Tie result to revision; label uncertainty; keep writing available. |
| J08 Delete/restore | Library overflow or Trash | Move to Trash; restore if needed | Cancel permanent deletion | Offline failure; retention expiration | Confirm destructive action and preserve original IDs on restore. |

These are target journeys, not current capabilities. During design review, mark every action as **implemented**, **S01 proposal**, or **future/deferred**.

## UI state matrix

| Surface | Empty | Loading/progress | Success | Error/offline | Recovery |
|---|---|---|---|---|---|
| Home/Library | Helpful create/open action | Opening/indexing | Local documents visible | Keep local browsing available | Retry remote fetch without hiding local data. |
| Workspace | Blank writing surface | Never block writing on network sync | Local edit vs server acknowledgment distinguished | Actionable banner; canvas stays usable | Retry or inspect conflict; never silently overwrite. |
| Page rail | One real page only | Brief operation progress | Active page highlighted | Failed operation shown | Preserve old page/history until safe retry. |
| Review | Explain availability/status | Analyzing a specific revision | Feedback tied to step and revision | Uncertain/unsupported/network error | Retry explicitly selected revision; mark stale after edit. |
| Trash | Empty-trash explanation | Restoring | Restored document visible | Delete/restore failure | Safe retry; explicit confirmation for permanent deletion. |

## Accessibility and walkthrough acceptance

- Every tappable control has a visible label or a descriptive accessible name, a correct role, and at least a 48 dp hit target. Pen/Eraser expose selected state; disabled actions expose why they are unavailable.
- TalkBack traversal follows the screen hierarchy: destination/header → primary action/tools → content list/canvas → secondary panel/footer. Drawing gestures do not steal focus from toolbar controls.
- Announce document open/create, tool changes, sync state transitions, review result freshness, and destructive confirmation outcomes through polite live regions; do not announce every pointer sample or steal focus.
- Opening a dialog, drawer, or Review sheet moves focus into it. Dismissal restores focus to its invoking control. System Back dismisses the topmost overlay before navigating.
- Clear confirmation names the action and document and offers a safe cancel path. Permanent deletion is separately labeled and confirmed.
- Test large text/font scale, contrast, light/dark theme, keyboard/IME, touch-only and stylus input, reduced available width, and system insets/cutouts.
- Required walkthroughs: (1) open/resume, (2) write/erase, (3) add/scroll/collapse pages when available, (4) resize/zoom when available, (5) offline sync/restart, (6) request/correct a math hint when supported.
- For each walkthrough record expected/observed result, friction, screenshot or video, and follow-up. Unimplemented future paths are reviewed as design-only, not falsely reported as runtime-passed.
- Targets: no controls under system bars; no viewport reset on layout changes; no false synced/correctness state; no dead-looking enabled controls.

### User-visible data and analysis status contract

| State | User-facing meaning | Do not say |
|---|---|---|
| Current-session edits (before local persistence) | Work exists in this running app session only. | Saved locally, backed up, or safe after force-stop. |
| Saved on device (only after durable local commit) | Local repository confirmed a durable write. | Synced or backed up remotely. |
| Waiting to sync / Syncing | Durable local edits are pending upload / being submitted. | Mathematically checked. |
| Server acknowledged | Server accepted the stated ink revision. | Cloud backup across accounts/devices or correct mathematics. |
| Sync attention needed | Network, gap, or conflict needs retry/review; local data remains available. | Saved/synced when acceptance is unknown. |
| Analysis unavailable / uncertain / stale | No current verified math verdict is available, or result belongs to an older revision. | Correct/incorrect without a supported verifier and matching revision. |

## Resolved S01 decisions

| Topic | Decision | Technical / sequencing gate |
|---|---|---|
| Page model and presentation | Pages are distinct fixed-size logical surfaces in one continuous vertical scroll. Add Page is explicit. No auto-append or infinite 2D board in MVP. | S02 defines identity/order/events/revision scope and legacy migration before S10; S11 implements the vertical surface. |
| Page navigator | Left collapsible rail at expanded width; temporary drawer/sheet below 840 dp. Active page only until real page CRUD exists. | S08 may deliver single-page navigation UI after prerequisites; page operations wait for S10. |
| Panel coexistence | Both page rail and Review coexist at >=1200 dp only when canvas remains >=560 dp. At 840–1199 dp Review replaces/collapses the rail; below 840 dp one temporary panel at a time. | S06 verifies insets and available-width rules on phone, tablet, split screen, and foldable windows. |
| Toolbar | Persistent Pen/Eraser only. Header holds Back, title, status, More. Clear is in More with confirmation (or separately proven undo); no placeholder Hand/Select/Keyboard/Color/Undo/Redo. | S07 after data/revision safety. A tool is exposed only when it has end-to-end behavior and tests. |
| Save and math check | Server ink sync is presented as Save/Sync, not math correctness. Check Math is a separate action and state machine, absent until supported by a real analysis contract. | S02 separates contracts; S07 labels sync; S17/S18 enable math review only after verifier/API readiness. |
| Feedback progression | Revision-bound chronological step list; hint first, explanation on request, full solution only on a further explicit request. Abstain on uncertain/unsupported work. Stroke/region anchors are optional only if recognition provides them. | S17 state contract; S18 recognition/verifier/evaluation and versioned result. |
| Feedback freshness | Each result references immutable document/page/revision. Edits mark it Stale; they do not silently rewrite or delete the prior result. | S02 defines result identity and revision contract; S18 implements stale/cancellation behavior. |
| Conflict recovery | Keep local history visible and preserved. Explain conflict and offer only safe, explicit recovery; no default reset, overwrite, or last-write-wins. | S02 defines history/ownership; S05 tests sync and recovery. |
| Input | Pen selected by default; finger drawing remains available with Pen. No palm-rejection promise. Two-finger pan/zoom deferred until S12 gesture and transform tests pass. | S00 physical-device behavior remains unverified; S12 validates actual stylus/finger interaction. |
| Organization | Home prioritizes Continue and Recent. Optional Subject → Notebook → Document; no arbitrary nesting or Notion-like block database. Trash is under Library. | S14/S15 require a durable local document repository; S16 requires soft-delete/restore semantics. |
| MVP exclusions | Collaboration, instructor dashboards, keyboard math objects, lasso, automatic pages, and infinite board are deferred. | Scope change requires dated plan change and architecture review. |

## Validation against J01–J08

This is a specification desk-check against the UX requirements and S00 audit, **not** a student usability study or runtime test.

| Journey | Screens / components | UX acceptance covered | Current runtime boundary |
|---|---|---|---|
| J01 Launch/resume | Home → Mathematics Workspace | Continue/recent, empty state, unavailable document, honest local/remote status | Home/library and durable resume are not implemented; S00 confirmed unsynced loss after process death. |
| J02 Create/organize | Home → Library → Workspace | Create once, title/notebook, cancel, validation and duplicate/error path | Local document repository and create/organize actions are future work. |
| J03 Write/erase | Workspace toolbar/canvas | Tool feedback, touch/stylus alternatives, repeated erasure and palm-risk handling | Pen/Eraser exist; duplicate eraser revision is likely but exact callback/event sequence is unverified. |
| J04 Navigate pages | Workspace page rail/continuous canvas | Explicit add, ordering, active marker, collapse, resize and operation failure | Page domain/events are absent; S02 contract precedes S10/S11. |
| J05 Zoom/pan | Workspace canvas/footer | Fit/zoom, gesture arbitration, coordinate and eraser accuracy | Not implemented; S12 only after page-coordinate contract. |
| J06 Save/offline/recover | Workspace header/status/recovery overlay | Distinguish local durable save, upload, acknowledgment, offline, conflict and process death | Server revision flow exists; local persistence and PostgreSQL E2E are open. |
| J07 Review math | Workspace → AI Review | Hint-first progression, uncertainty, stale result, retry and continued writing | Recognition, verifier, and analysis endpoint unavailable; show unavailable state or omit action. |
| J08 Delete/restore | Library → Trash | Soft-delete, restore identity/location, confirmed permanent delete, failure/offline | Document repository and trash are future work. |

All eight journeys have an entry, primary flow, alternative/cancel path, error/recovery, exit, and user-visible feedback in the journey table above. This establishes design coverage only; future workflow execution must be tested separately.

## S02 architecture handoff

S02 owns technical decisions; this UX baseline specifies behavior without prescribing persistence or transport implementation:

1. Define `Document`, `Page`, and `WorkSession` ownership/cardinality. Decide whether `workSessionId` is a durable document identity or a sync-session identity.
2. Select local source of truth, durable vector-ink serialization, atomic pending outbox, crash semantics, and migration from existing one-page state. Do not label in-memory work “saved locally.”
3. Define page-scoped stroke ownership, stable ordering, explicit create/delete/reorder/clear events, idempotency, and whether revisions are document-wide or page-scoped.
4. Define page-local coordinate units/dimensions and viewport state. Rendering, input, and eraser hit-testing must share a tested transform/inverse; zoom must never rewrite stored strokes.
5. Separate ink-sync acknowledgment from math-analysis request/results. Analysis must carry a stable document identity and immutable revision; contract must represent stale/unsupported/uncertain results.
6. Define non-destructive conflict/recovery operations and ownership/authz before multi-user or multi-device access. Raw UUIDs are not authorization.
7. Freeze API/event version compatibility and old-client behavior before page events ship; reject unsupported semantics without discarding local pending history.

Implementation dependencies: S03–S04 persistence/outbox; S05 sync/recovery correctness; S06 responsive shell/insets; S07 truthful toolbar/status/Clear; S08 single-page navigator; S10 page contract/migration; S11 continuous page surfaces; S12 transform/gestures; S14–S16 library/organization/trash; S17 review state UI; S18 evaluated recognition/verifier integration; S19 identity/cloud.

## Sign-off status

S01 design baseline is complete for planning. It is not evidence that the S00 technical audit is complete, that proposed UX passed user testing, or that future capabilities are implemented. The user requested this finalization. Any change to these resolved decisions requires a dated plan/design change and an S02 dependency review.
