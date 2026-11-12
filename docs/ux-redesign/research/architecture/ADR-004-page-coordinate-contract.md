# ADR-004 — Page-local coordinates and viewport transforms

**Status:** Reconciled proposal; A4 is the initial profile candidate; implementation proof required
**Decision track:** S02-D
**Code baseline:** `86e5ecdd65bbc127f2da49c03c00732f9ca2b124`
**Depends on:** Provisional [ADR-001](ADR-001-document-page-worksession.md) and [ADR-002](ADR-002-local-storage-outbox.md)

## Context and verified current behavior

- `InkSurface` uses `InProgressStrokes` for input and draws completed strokes with an identity transform. No page size, viewport state, zoom/pan transform, or explicit density conversion is modeled.
- Eraser input uses a fixed `20f` half-extent. `StrokeHitTester` constructs an axis-aligned square around the pointer and tests with `AffineTransform.IDENTITY`; the intended screen-space size and dp equivalence are not specified.
- `StrokePayloadMapper` forwards float x/y values without coordinate conversion. A mapper test confirms those sample values pass through; this does not establish their unit or physical scale.
- The domain has one unscoped stroke list and no page dimensions or geometry. There are no transform, eraser-at-zoom, or viewport-invariance tests.
- S01 requires distinct fixed-size pages in a continuous vertical document and says zoom must not change stored strokes. It does not choose a page size or coordinate unit.

Evidence: [InkSurface.kt](../../../../app/src/main/java/com/example/myapplication/ink/InkSurface.kt), [StrokeHitTester.kt](../../../../app/src/main/java/com/example/myapplication/ink/StrokeHitTester.kt), [StrokePayloadMapper.kt](../../../../app/src/main/java/com/example/myapplication/analysis/transport/StrokePayloadMapper.kt), [InkDocument.kt](../../../../app/src/main/java/com/example/myapplication/domain/InkDocument.kt), [AndroidX Ink dependency pin](../../../../app/build.gradle.kts), [S01 UX specification](../ux-spec.md), and [S00 audit](../current-state-audit.md).

The pinned AndroidX Ink 1.0.0 API exposes `pointerEventToWorldTransform` and `strokeToWorldTransform` on `InProgressStrokes`, and `strokeToScreenTransform` on `CanvasStrokeRenderer`. This establishes that explicit event/world and stroke/screen mappings are available, not that the app currently uses them correctly or that AndroidX chooses page-point units or physical calibration. The current app supplies no such transforms. The adapter and rendering alignment still require an instrumented proof at the pinned version; do not assume that a Compose graphics transform transforms Ink input or hit testing.

## Proposed coordinate contract

Use **A4 portrait as the initial fixed page-profile candidate**: 210 × 297 mm = 595.2756 × 841.8898 logical points, with `1 point = 1/72 inch`. US Letter (612 × 792 points) is a possible later explicit profile, not an implicit runtime fallback. Product approval of the page profile remains open. Define the origin at top-left, +x to the right, +y downward, and the dimensions as the page bounds; margins are content/layout guidance, not a second coordinate origin. Store a profile/version and unit with page geometry.

Logical points are a canonical document unit, not a claim that the Android display is physically calibrated at 72 dpi. Do not store viewport/device pixels or independently normalize x/y axes: pixels vary with device/layout and independent normalization can distort stroke proportions.

Represent viewport state separately from document content. For page-local point `p` and page-stack origin `o` in logical points, uniform scale `s` in canvas pixels per point, canvas-local translation `t` in canvas pixels, and canvas-local pixel coordinate `v`:

```text
v = s * (o + p) + t
p = ((v - t) / s) - o
```

Page dimensions and stack gaps/origins belong to the page/viewport model, not to stroke coordinates. Measure the canvas after window/system insets; convert pointer positions to canvas-local coordinates once. Insets must not be added again to page-local points. Keep active page, pan, zoom, and fit-width as viewport/UI state, not content revisions or outbox events. Store canonical coordinates once; never repeatedly transform and persist them on zoom/layout changes.

Use the same forward transform for rendering and inverse for input: configure the Ink event-to-world mapping and renderer stroke-to-screen mapping consistently, then verify wet/dry alignment. For a continuous page stack, route each pointer to a page by its visible bounds and apply that page's local inverse transform; page ID scoping is required so a gap alone is not relied on to prevent hits against neighboring-page strokes.

Define eraser size in dp at the UI boundary, convert to canvas pixels using the current density, then map its hit region through the inverse scale. Keep the existing square hit-region semantics unless a product/UI decision selects a different geometry; the current `20f` is a raw half-extent under identity, not a validated dp or physical size. Screen-space coverage must remain constant while zoom changes.

On resize, rotation, fold, or IME/inset changes, recompute the transform while preserving a viewport anchor represented by `(pageId, pageLocalPoint, canvasPixelPosition)`. Fit-width may change scale by design; preserve the anchor and active page instead of promising identical zoom. S01 requires the document and tool not reset and targets preserving the active page and viewport. Gesture arbitration and two-finger pan/zoom remain deferred until transformed stylus/finger input and eraser behavior are validated.

## Alternatives and unresolved choices

- **A4 logical page points (initial candidate):** independent of device density and supports predictable printing/zoom. Requires product approval and a tested mapping at Ink input/render boundaries.
- **Normalized coordinates:** easy to fit variable page sizes, but nonuniform scaling can distort strokes; only acceptable with a fixed aspect ratio and a defined mapping.
- **Viewport/device pixels:** matches current raw pass-through most closely but couples stored geometry to physical/display density, content insets, and the current layout. Not recommended for a durable multi-device document format.
- Product approval of the A4 profile, page margins, out-of-bounds stroke behavior, brush width/color/texture representation, Float precision/tolerance, and whether current raw coordinates can be migrated losslessly remain open. No existing page dimensions or viewport metadata permit claiming a lossless conversion.

## Invariants and acceptance tests

Before enabling transformed input, require:

1. Forward/inverse round trips for A4 page corners, per-page origins, scales, translations, and inset-adjusted canvas coordinates within an explicitly selected numeric tolerance.
2. Instrument `InProgressStrokes` with known event-to-world transforms across scales, pans, canvas sizes, and densities; emitted page coordinates must be stable. Render with the matching renderer transform and verify wet-to-dry alignment.
3. Eraser region has a defined dp size/shape and produces equivalent coverage at multiple zoom levels and near stroke edges; page-ID filtering prevents neighboring-page hits.
4. Fit-width, zoom, pan, rotation, resize, fold, and IME changes preserve `(pageId, pageLocalPoint, canvasPixelPosition)` as specified and leave canonical strokes, document revision, and outbox unchanged.
5. Versioned persistence round-trips A4 dimensions and every required stroke/brush/input attribute within the approved precision budget, including extreme coordinates and repeated transforms.
6. Instrumented tests exercise AndroidX Ink 1.0.0 `pointerEventToWorldTransform`, `strokeToWorldTransform`, and renderer `strokeToScreenTransform`; physical stylus/finger verification remains a separate release gate.

These are proposed architecture tests, not current test coverage. A4 is an initial profile recommendation, not a user-approved product decision. The contract is not implementation-approved until the profile, coordinate adapter, eraser size, numeric tolerance, and AndroidX behavior are validated. The earlier assertion that the pinned API's transform capability was unknown is superseded by the read-only API review; app integration remains unproven.
