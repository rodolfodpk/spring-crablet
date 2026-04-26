# Docs Verification

The repository now includes a lightweight docs verification step:

```bash
make docs-check
```

For compile-time verification of the canonical tutorial examples:

```bash
make docs-compile-check
make docs-generate-check
```

For AI-first codegen documentation and fixture verification:

```bash
make codegen-check
```

It currently validates:

- relative markdown links resolve to real files
- key deployment docs keep the agreed topology wording: `1 application instance per cluster` plus per-module poller clarification
- outdated wording such as `2 instances at most` does not reappear in the main onboarding docs
- early tutorials include explicit `EventType.type(...)` import context
- the outbox tutorial matches the current `OutboxPublisher` API shape

`codegen-check` separately verifies:

- `embabel-codegen` model parsing, artifact planning, and MCP tool tests
- the documented loan feature-slice event model parses as a real `EventModel`
- the planner smoke command prints expected generated artifacts without calling Anthropic

## Why This Exists

The main failure mode in the docs was not missing prose. It was drift:

- examples that no longer matched the public API
- tutorials that assumed imports or setup without showing them
- inconsistent deployment guidance across modules

This check is meant to catch those regressions early with minimal build overhead.

## Current Limit

This does **not** compile markdown code blocks. The stronger next step would be to derive tutorial snippets from real sample code or compile dedicated tutorial fixtures in CI.

## Compile Fixtures

The `docs-samples` module is the first compile-time step in that direction.

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
- avoid introducing snippet extraction machinery too early

Each tutorial and `GETTING_STARTED.md` page should now point to its canonical compile fixture. That makes the relationship explicit and reviewable even before snippet extraction exists.

## Generated Snippets

The repository now also supports source-derived snippet generation:

```bash
make docs-generate
make docs-generate-check
```

Snippet regions are marked in the fixture sources with:

```java
// docs:begin snippet-name
// docs:end snippet-name
```

Generated output is written to [generated docs](../generated).

This is the bridge from “compile fixtures exist” to “snippets come from source.”
