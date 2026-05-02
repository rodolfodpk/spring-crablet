# Plan: Optional `diagram` in `event-model.yaml` + renderer support

**Authoritative copy:** this repository file (`docs/dev/plans/optional-event-model-diagram.md`) is the single source of truth for this work. Any Cursor-local plan is a pointer only.

**Cursor plan id:** `62f9eb1f-f45d-4cbc-83b5-d798f505fde0`.

**Status:** Implementation-ready. The decisions below are locked unless this plan is revised again.

## Summary

Add optional top-level `diagram` metadata to `event-model.yaml` so diagram lanes and visual overlays can live beside the model. Java parsing accepts and preserves this metadata, but Java artifact planning, code generation, and Kubernetes generation ignore it for structural output.

The docs renderer will support both shapes:

- New nested shape: `diagram.lanes`, `diagram.assignments`, etc.
- Existing flat shape: sidecar YAML already merged as top-level `lanes`, `assignments`, etc.

Classic horizontal swim-strip rendering is out of scope. Crablet continues to render subsystem lanes as vertical column groups under shared semantic rows.

## Implementation todos

- [ ] **codegen-diagram-spec** - add `diagram` to `EventModel`, add Jackson-safe `DiagramSpec`, preserve it through model-copy paths, and ignore it in Java/k8s generation.
- [ ] **json-schema-diagram** - update [`docs/user/examples/event-model-schema.json`](../../user/examples/event-model-schema.json) with `diagram` and missing `deployment` root properties plus `$defs`.
- [ ] **renderer-diagram-support** - update [`docs/event-model-renderer.js`](../../event-model-renderer.js) with a shared normalization/merge helper used by `render()`.
- [ ] **html-merge-diagram** - update [`docs/wallet.html`](../../wallet.html) and [`docs/course.html`](../../course.html) to use the shared helper instead of hand-merging sidecars.
- [ ] **examples-inline-diagram** - inline `diagram.lanes` and `diagram.assignments` into wallet/course event-model YAML; keep sidecars only for docs-only overlays.
- [ ] **doc-em-notation** - update `EVENT_MODEL_FORMAT.md` and `EVENT_MODELING.md` with `diagram`, merge rules, and column-group notation.
- [ ] **skill-template-diagram** - update `.claude/skills/event-modeling` and the `templates/crablet-app` copy to suggest optional `diagram` when multiple bounded contexts/subsystems appear.
- [ ] **acceptance-tests** - run the Java, renderer, HTML, and schema checks listed below.

## Locked Decisions

- `diagram` is optional metadata, not part of the Java structural generation contract.
- v1 supports the full renderer overlay set under `diagram`:
  `lanes`, `assignments`, `triggers`, `syntheticCommands`, `eventBadges`, and diagram-only `automations`.
- Java code should parse and preserve `diagram`, but planning/generation/k8s should not create artifacts from it.
- Sidecars remain supported for docs-specific visual overlays and overrides.
- Merge precedence is:
  `event-model.yaml` core fields < nested `diagram` overlay < flat sidecar/top-level overlay`.

## Java Model And Schema

Add a new `diagram` component to [`EventModel`](../../../embabel-codegen/src/main/java/com/crablet/codegen/model/EventModel.java). Use a compact-constructor default so `null` becomes an empty diagram object or another safe default that keeps existing tests passing.

Add `DiagramSpec` with explicit fields for all v1 keys:

- `List<LaneSpec> lanes`
- `Map<String, String> assignments`
- `List<TriggerSpec>` or an equivalent renderer metadata type for `triggers`
- `List<SyntheticCommandSpec>` or an equivalent renderer metadata type for `syntheticCommands`
- `Map<String, String> eventBadges`
- `List<AutomationSpec> automations`

`DiagramSpec` must be annotated with `@JsonIgnoreProperties(ignoreUnknown = true)`. The current embabel-codegen mappers use `ObjectMapper` with `YAMLFactory` and do not globally disable unknown-property failures, so this is required for forward-compatible diagram metadata.

Update every direct `new EventModel(...)` call site after adding the record component. Known current call sites include:

- [`SchemaResolver.java`](../../../embabel-codegen/src/main/java/com/crablet/codegen/pipeline/SchemaResolver.java): pass through `model.diagram()` so schema resolution does not drop metadata.
- [`ArtifactPlannerTest.java`](../../../embabel-codegen/src/test/java/com/crablet/codegen/planning/ArtifactPlannerTest.java): pass `null` or the empty diagram default.

Before merging, rerun `rg "new EventModel\\(" embabel-codegen` from the repository root and update any additional call sites.

Update [`docs/user/examples/event-model-schema.json`](../../user/examples/event-model-schema.json):

- Add root `diagram` property and `$defs` matching the accepted v1 shape.
- Add root `deployment` property and `$defs` to match the existing Java `DeploymentSpec`.
- Keep root `additionalProperties: false`; allow forward-compatible extension only inside `diagram` if the schema already has a local pattern for that. Otherwise keep the schema strict and rely on Java's `DiagramSpec` ignore-unknown behavior.

## Renderer And Merge Rules

Implement one shared JavaScript helper in [`docs/event-model-renderer.js`](../../event-model-renderer.js), for example:

```js
EventModelRenderer.mergeEventModelForDiagram(model, sidecar = {})
```

`render(model, container)` must call the same normalization logic internally so callers that pass only a nested `model.diagram` also work.

The helper accepts:

- `model`: parsed `event-model.yaml`
- `sidecar`: parsed `*-diagram.yaml`, or `{}` when absent

Merge rules are fixed:

- Base model fields come from `model`.
- Overlay fields first come from `model.diagram`.
- Sidecar keys override diagram keys only when the sidecar has that own property.
- `lanes`, `triggers`, and `syntheticCommands`: later arrays replace earlier arrays when the later layer defines that key, including empty arrays for intentional overrides.
- `assignments` and `eventBadges`: shallow object merge, with later keys overriding matching earlier keys.
- `automations`: merge by `name` in this order:
  `model.automations`, then `model.diagram.automations`, then `sidecar.automations`; later entries replace earlier entries with the same name.

The merged object passed to layout should be flat:

- `lanes`
- `assignments`
- `triggers`
- `syntheticCommands`
- `eventBadges`
- `automations`

After flattening, layout code should not need to read `model.diagram`.

Existing flat payloads must remain compatible. If a caller passes an object with top-level `lanes` / `assignments` and no `diagram`, `render()` should behave as it does today.

## HTML And Examples

Update [`docs/wallet.html`](../../wallet.html) and [`docs/course.html`](../../course.html) so they:

- Fetch the main event model and sidecar as they do today.
- Parse both with `js-yaml`.
- Call the shared merge helper.
- Pass the merged object to `EventModelRenderer.render`.

Inline these fields into `docs/examples/wallet-event-model.yaml` and `docs/examples/course-event-model.yaml`:

- `diagram.lanes`
- `diagram.assignments`

Keep sidecars for docs-only visual overlays such as:

- `triggers`
- `syntheticCommands`
- `eventBadges`
- diagram-only automation rows, when keeping them outside the main model is clearer for docs

Wallet is the primary demo because it proves lane grouping plus a notification-side synthetic command. Course is the second demo because it proves a simpler multi-entity DCB board without notification complexity.

Add a `diagram` parser fixture to [`embabel-codegen/src/test/resources/wallet-event-model.yaml`](../../../embabel-codegen/src/test/resources/wallet-event-model.yaml), and add a short commented `diagram:` example to [`templates/crablet-app/event-model.yaml`](../../../templates/crablet-app/event-model.yaml).

## Documentation

Update [`EVENT_MODEL_FORMAT.md`](../../user/ai-tooling/EVENT_MODEL_FORMAT.md):

- Document `diagram` as optional renderer/tooling metadata.
- State that Java codegen ignores `diagram`.
- List the v1 fields and their meanings.
- Document merge precedence for HTML rendering.

Update [`EVENT_MODELING.md`](../../user/ai-tooling/EVENT_MODELING.md):

- Explain that Crablet renders subsystem lanes as vertical column groups, not classic horizontal swim strips.
- Explain when to use `diagram` inline versus a sidecar overlay.

Update `.claude/skills/event-modeling/SKILL.md` and the template copy so agents suggest `diagram.lanes` / `diagram.assignments` only when multiple bounded contexts or subsystems are present.

## Acceptance / Test Plan

Java:

- [ ] YAML without `diagram` still parses.
- [ ] YAML with `diagram.lanes` and `diagram.assignments` parses.
- [ ] YAML with the full v1 overlay set parses.
- [ ] Unknown keys under `diagram` do not fail parsing.
- [ ] `SchemaResolver.resolve` preserves `diagram`.
- [ ] Artifact planning output is unchanged when `diagram` is present.
- [ ] Java generation and k8s generation ignore `diagram`.

Renderer and HTML:

- [ ] `EventModelRenderer.render` works with only nested `model.diagram` and no sidecar.
- [ ] Existing flat merged payloads render as before.
- [ ] Sidecar values override nested `diagram` values according to the merge rules.
- [ ] Wallet still shows Wallet and Notification lane headers after migration.
- [ ] Course still shows Course and Student enrollment lane headers after migration.

Schema:

- [ ] Representative YAML with `deployment` validates.
- [ ] Representative YAML with `diagram` validates.
- [ ] Representative YAML with both `deployment` and `diagram` validates.

## Suggested Execution Order

1. Add `DiagramSpec`, update `EventModel`, preserve `diagram` through `SchemaResolver`, and add Java tests.
2. Add renderer normalization/merge helper and wire it into `render()`.
3. Update JSON schema with `deployment` and `diagram`.
4. Update wallet/course HTML and migrate example YAML.
5. Update docs and skills.
6. Add the optional renderer hint for lane sections if desired.
