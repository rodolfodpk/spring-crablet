# User documentation

Everything here is for **people building or operating applications** on Crablet (not for working on the framework repository itself).

| Track | Status | Use this when |
|------|--------|---------------|
| [Stage 1 — Java-first](#stage-1--java-first-runtime-near-complete) | **Near complete** | You want to use Crablet as a Spring framework directly. |
| [Stage 2 — AI-first](#stage-2--ai-first-code-generation-in-progress) | **In progress** | You want to turn Event Modeling conversations into `event-model.yaml` and generated Spring code. |
| [Stage 3 — Local Kubernetes](#stage-3--local-kubernetes-early--planned) | **Early / planned** | You want generated local/test Kubernetes manifests from the modeled service shape. |

## Stage 1 — Java-first runtime _(near complete)_

| Path | Contents |
|------|----------|
| [QUICKSTART.md](QUICKSTART.md) | Fastest path — wallet reference app |
| [TUTORIAL.md](TUTORIAL.md), [tutorials/](tutorials/) | Hands-on tutorial series |
| [CREATE_A_CRABLET_APP.md](CREATE_A_CRABLET_APP.md) | Manual app setup |
| [COMMANDS_FIRST_ADOPTION.md](COMMANDS_FIRST_ADOPTION.md) | Adopt the command side first |
| [MODULES.md](MODULES.md), [PUBLIC_API.md](PUBLIC_API.md) | Module map and HTTP API surface |
| [../../crablet-eventstore/SCHEMA.md](../../crablet-eventstore/SCHEMA.md) | Database schema — what Crablet adds to Postgres and why |
| [CONFIGURATION.md](CONFIGURATION.md), [BUILD.md](BUILD.md), [UPGRADE.md](UPGRADE.md) | Configure, build, upgrade |
| [DEPLOYMENT_TOPOLOGY.md](DEPLOYMENT_TOPOLOGY.md) | Monolith model and deployment shapes _(also covers K8s; will be split when Stage 3 matures)_ |

## Stage 2 — AI-first code generation _(in progress)_

| Path | Contents |
|------|----------|
| [ai-tooling/AI_FIRST_WORKFLOW.md](ai-tooling/AI_FIRST_WORKFLOW.md) | End-to-end codegen workflow |
| [ai-tooling/COURSE_CORE_WORKFLOW.md](ai-tooling/COURSE_CORE_WORKFLOW.md) | First Course-domain AI workflow target |
| [ai-tooling/FEATURE_SLICE_WORKFLOW.md](ai-tooling/FEATURE_SLICE_WORKFLOW.md) | Adding one vertical slice |
| [ai-tooling/EVENT_MODEL_FORMAT.md](ai-tooling/EVENT_MODEL_FORMAT.md) | event-model.yaml contract |
| [ai-tooling/EVENT_MODELING.md](ai-tooling/EVENT_MODELING.md) | Event Modeling notation |
| [ai-tooling/AI_SKILLS.md](ai-tooling/AI_SKILLS.md) | Claude Code skill routing |

## Stage 3 — Local Kubernetes _(early / planned)_

No dedicated docs exist yet. See the Kubernetes section of
[DEPLOYMENT_TOPOLOGY.md](DEPLOYMENT_TOPOLOGY.md) for current guidance.
This section will expand as the K8s workflow matures.

## Common

| Path | Contents |
|------|----------|
| [TROUBLESHOOTING.md](TROUBLESHOOTING.md) | Common issues |
| [PERFORMANCE.md](PERFORMANCE.md) | Performance guidance |
| [OBSERVABILITY.md](OBSERVABILITY.md) | Metrics and tracing |
| [LEADER_ELECTION.md](LEADER_ELECTION.md) | Leader election for singleton workers |
| [examples/](examples/) | Example event models and dialogue samples |
| [assets/](assets/) | Diagrams referenced from the guides |
| [generated/](generated/) | Source-derived snippet output (see `make docs-generate`) |

Maintainer-only material lives under [docs/dev/](../dev/README.md).
