# ADR-004 — Page-local coordinates and viewport transforms

**Status:** Proposed; page profile and Ink input mapping require validation
**Decision track:** S02-D
**Code baseline:** `86e5ecdd65bbc127f2da49c03c00732f9ca2b124`
**Depends on:** Provisional [ADR-001](ADR-001-document-page-worksession.md) and [ADR-002](ADR-002-local-storage-outbox.md)

## Context and verified current behavior

- `InkSurface` uses `InProgressStrokes` for input and draws completed strokes with an identity transform. No page size, viewport state, zoom/pan transform, or density conversion is modeled.
- Eraser input uses a fixed `20f` radius. `StrokeHitTester` constructs an axis-aligned square around the pointer and tests with `AffineTransform.IDENTITY`.
- `StrokePayloadMapper` forwards float x/y values without coordinate conversion. A mapper test confirms those sample values pass through; this does not establish their unit or physical scale.
- The domain has one unscoped stroke list and no page dimensions or geometry. There are no transform, eraser-at-zoom, or viewport-invariance tests.
- S01 requires distinct fixed-size pages in a continuous vertical document and says zoom must not change stored strokes. It does not choose a page size or coordinate unit.

Evidence: [InkSurface.kt](../../../../app/src/main/java/com/example/myapplication/ink/InkSurface.kt), [StrokeHitTester.kt](../../../../app/src/main/java/com/example/myapplication/ink/StrokeHitTester.kt), [StrokePayloadMapper.kt](../../../../app/src/main/java/com/example/myapplication/analysis/transport/StrokePayloadMapper.kt), [InkDocument.kt](../../../../app/src/main/java/com/example/myapplication/domain/InkDocument.kt), [S01 UX specification](../ux-spec.md), and [S00 audit](../current-state-audit.md).

The inspected usage does not establish AndroidX Ink's coordinate-unit guarantees or whether the current input builder can consume a mapped transform. Verify against the pinned AndroidX Ink version and instrumentation before choosing an adapter. Do not infer that a Compose graphics transform also transforms Ink input or hit testing.

## Proposed coordinate contract

Store stroke geometry in an immutable, page-local coordinate system with explicit versioned page dimensions and unit. A candidate is logical points (`1 point = 1/72 inch`) with uniform aspect-preserving scale. A4 and US Letter are candidate fixed page profiles; product must choose one or allow a defined profile set before implementation. Do not use device pixels or independently normalized x/y axes as canonical storage: pixels vary with device/layout, and independent normalization can distort stroke proportions.

Represent viewport state separately from document content. For page-local point `p`, page-stack origin `o`, uniform scale `s`, canvas-local translation `t`, and viewport point `v`:

```text
v = s * (o + p) + t
p = ((v - t) / s) - o
```

Page dimensions and stack gaps/origins belong to the page/viewport model, not to stroke coordinates. Measure the canvas after window/system insets; convert pointer positions to canvas-local coordinates once. Insets must not be added again to page-local points. Keep active page, pan, zoom, and fit-width as viewport/UI state, not content revisions or outbox events.

Use the same forward transform for rendering and inverse for input. Eraser hit testing must use the corresponding inverse-transformed center and radius (`r_page = r_viewport / s`) or test in viewport space with transformed stroke geometry. The perceived eraser radius should remain constant in screen space; page boundaries and gaps must prevent hits on a neighboring page.

On resize, rotation, fold, or IME/inset changes, recompute the transform while preserving an explicit page-space viewport anchor. S01 requires document, tool, active page, and viewport continuity on layout changes. Gesture arbitration and two-finger pan/zoom remain deferred until transformed stylus/finger input and eraser behavior are validated.

## Alternatives and unresolved choices

- **Logical page points with a fixed page profile (proposed):** independent of device density and supports predictable printing/zoom. Requires product choice of page dimensions and a tested mapping at Ink input/render boundaries.
- **Normalized coordinates:** easy to fit variable page sizes, but nonuniform scaling can distort strokes; only acceptable with a fixed aspect ratio and a defined mapping.
- **Viewport/device pixels:** matches current raw pass-through most closely but couples stored geometry to physical/display density, content insets, and the current layout. Not recommended for a durable multi-device document format.
- Exact A4 versus Letter dimensions, page margins, out-of-bounds stroke handling, brush width/color representation, precision/tolerance, and whether current raw coordinates can be migrated losslessly remain open. No existing page dimensions or viewport metadata permit claiming a lossless conversion.

## Invariants and acceptance tests

Before enabling transformed input, require:

1. Forward/inverse round trips for page origins, scales, translations, and insets within a documented numeric tolerance.
2. The same physical sample maps to the same stored page point across viewport scales and device/layout sizes; render-after-input returns to its originating viewport point.
3. Eraser results match at multiple zoom/pan values and near stroke edges; screen-space eraser size remains constant; page gaps/boundaries prevent cross-page hits.
4. Zoom, pan, fit-width, rotation, resize, and IME changes leave canonical strokes, document revision, and outbox content unchanged.
5. Versioned persistence round-trips page dimensions and all required stroke/brush/input attributes within an approved precision budget, including extreme coordinates and repeated transforms.
6. Instrumented tests verify the actual input-builder mapping and renderer transform using the pinned AndroidX Ink version; physical stylus/finger validation remains a separate release gate.

These are proposed architecture tests, not current test coverage. The candidate contract is not implementation-approved until page profile, coordinate adapter, numeric tolerance, and AndroidX behavior are verified.
