# Product Roadmap

This is the single document describing AI-assisted codegen and Kubernetes generation — the two
pré-1.0/experimental tracks referenced from skills, module READMEs, and `CLAUDE.md`. Everything
else in the project (event store, commands, views, automations, outbox, poller) is the stable,
documented runtime described in `docs/user/`.

## Current State

**Runtime (event store, commands, views, automations, outbox):** near complete, pre-1.0 API
hardening done, documented in `docs/user/` as the default path.

**AI-assisted codegen (`crablet-codegen`):** in progress. `plan`, `generate`, `init`, and
`sync-scenarios` are deterministic, tested, and CI-gated (`make codegen-regenerate-verify`).
No LLM call is involved in any of them. `event-model.yaml` → generator → structural Java is
functional for the wallet, course, and loan example domains.

The `crablet-event-modeling` skill guides an AI-assisted workshop that produces
`event-model.yaml`. This conversational modeling step is not CI-gated — it produces a YAML file
that a human reviews via a normal PR diff before `generate` runs.

**Kubernetes manifest generation (`crablet-codegen k8s`):** early. `K8sGenerator` produces
`k8s/base` manifests (Namespace, Deployments, Service, optional KEDA `ScaledObjects`, Secret
template) from the `deployment:` block in `event-model.yaml`. Code and tests exist and are
maintained; production-readiness gaps (probes, KEDA scale-to-zero interaction, rolling-update
safety around advisory-lock holders) are unresolved.

**Diagram renderer (`docs/event-model-renderer.js`, `crablet-diagram-advisor`):** early. Static
HTML renderer, ~2,300 lines, that turns `event-model.yaml` into an actor/lane board (used by
`docs/event-model-viewer.html` on GitHub Pages and vendored into the starter template for
`make diagram-preview`). No live-reload — the user must re-open the page after editing the YAML.
It has **no automated test coverage** (unlike the codegen generators), only a byte-equality check
that the two vendored copies stay in sync with each other.

## Desired Outcome

- **Codegen** graduates from experimental to documented-as-ready once: the five generators
  (events, commands, views, automations, outbox) have test coverage comparable to the framework
  modules, and the deterministic `generate` path has run unattended (no manual repair) across all
  three example domains for a sustained period.
- **K8s generation** graduates once the production gaps above are closed and validated against a
  real cluster deploy of at least one example app.

## Promotion Criteria

A track moves from this roadmap into `docs/user/` and stable skill routing in `CLAUDE.md` when:

1. It has a stable, documented public contract (no breaking shape changes expected).
2. Generated/produced artifacts are exercised by CI on every change, not just manually.
3. It has been used to build and deploy at least one non-trivial example without hand-repair.
4. The change is called out explicitly in `UPGRADE.md` and the root `README.md`.

Until all four hold, do not present the track as a supported default workflow in public docs.

## Open Items

- Codegen test coverage is thinner than the framework modules; close the gap before promotion.
- The diagram renderer has no automated tests; add rendering-output tests (or a snapshot check)
  before it can graduate alongside the generators it visualizes. No live-reload/watch mechanism
  exists — the user must manually re-open the preview after editing `event-model.yaml`.
- K8s production gaps: liveness/readiness probes, KEDA scale-to-zero interaction with
  LISTEN/NOTIFY, rolling-update strategy that avoids split-brain on advisory-lock holders.
- Model-assisted commands (`crablet explain`, `crablet suggest`) are unimplemented. `CodegenLlmClient`,
  `CodegenLlmProperties`, and `CodegenLlmSelection` are retained as dormant types reserved for these
  future opt-in commands — do not remove them, and do not add new implementations until the commands
  are planned. Any implementation must live in `com.crablet.codegen.llm` with no provider SDKs on the
  classpath outside that package (enforced by an ArchUnit rule). `codegen.anthropic.*` /
  `ANTHROPIC_API_KEY` are legacy config names; `codegen.llm.*` / `CODEGEN_LLM_*` are the
  provider-neutral names for when that work resumes.
- Gherkin/BDD and EventCatalog integration are intentionally out of scope for the Crablet core —
  documented here as a closed decision, not tracked as pending work.
