# Docs Verification

The repository includes a lightweight docs verification step:

```bash
make docs-check
```

For compile-time verification of the canonical tutorial examples:

```bash
make docs-compile-check
```

For codegen documentation and fixture verification:

```bash
make codegen-check
```

`docs-check` validates:

- relative markdown links resolve to real files
- key deployment docs keep the agreed topology wording: `1 application instance per cluster` plus per-module poller clarification
- outdated wording such as `2 instances at most` does not reappear in the main onboarding docs
- early tutorials include explicit `EventType.type(...)` import context
- the outbox tutorial matches the current `OutboxPublisher` API shape
- Markdown word-budget guardrails per area (public/modules, maintainer, agents) and the repo-wide total
- banned AI/Kubernetes promotional terms stay confined to `docs/dev/PRODUCT_ROADMAP.md` and the
  skills explicitly marked pré-1.0/experimental

`codegen-check` separately verifies:

- `crablet-codegen` model parsing and artifact planning tests
- the documented loan feature-slice event model parses as a real `EventModel`
- the planner smoke command prints expected generated artifacts without calling a model

## Why This Exists

The main failure mode in the docs was not missing prose. It was drift:

- examples that no longer matched the public API
- tutorials that assumed imports or setup without showing them
- inconsistent deployment guidance across modules
- documentation growing unbounded and presenting incomplete tracks as production-ready

This check is meant to catch those regressions early with minimal build overhead.

## Current Limit

This does **not** compile markdown code blocks. The stronger next step would be to derive tutorial snippets from real sample code or compile dedicated tutorial fixtures in CI.

## Compile Fixtures

The `docs-samples` module is the canonical source for tutorial code.

It contains small compilable tutorial fixtures for:

- event store basics
- commands
- DCB concurrency
- views
- automations
- outbox

The current approach is intentionally simple:

- verify the canonical tutorial shapes against the real public API
- keep the fixture code close to the tutorial prose
- each tutorial page points to its canonical compile fixture and its tests directly, instead of
  duplicating source into the docs tree
