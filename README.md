# Crablet: AI-Assisted Event-Sourced Spring Applications From Event Models

[![Java CI](https://github.com/rodolfodpk/spring-crablet/actions/workflows/maven.yml/badge.svg)](https://github.com/rodolfodpk/spring-crablet/actions/workflows/maven.yml)
[![codecov](https://codecov.io/gh/rodolfodpk/spring-crablet/branch/main/graph/badge.svg)](https://codecov.io/gh/rodolfodpk/spring-crablet)
[![Java](https://img.shields.io/badge/Java-25-orange?logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/25/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

Crablet helps Spring teams turn an event-modeled domain into a working event-sourced application. It uses AI-assisted generation to produce the structural code around commands, events, views, automations, outbox publishers, and tests, then runs that code on a small Java 25 Spring Boot runtime.

## Why Crablet May Be Useful

- Event-model-first workflow for DCB-style domains
- AI-assisted generation for commands, events, handlers, views, automations, outbox publishers, and tests
- Cross-entity consistency without forcing everything into one aggregate stream
- Small Java runtime for consistency, persistence, polling, and operational behavior
- Manual APIs available when generated code needs customization

## Framework Path

Crablet can be used directly as a Java framework: `EventStore`, command handlers, and `CommandExecutor` are the typical write path; views, outbox, automations, and the optional HTTP command adapter layer on top. For a first project, start with the [module reference](docs/user/MODULES.md), [Create a new Crablet app manually](docs/user/CREATE_A_CRABLET_APP.md), the [Tutorial](docs/user/TUTORIAL.md), the [Commands](crablet-commands/README.md) and [Event Store](crablet-eventstore/README.md) module READMEs, and the [Wallet example](wallet-example-app/README.md) for a full app shape.

## AI-First Path

The only tool you interact with is Claude Code. You describe outcomes in plain language; Claude handles modeling, planning, generating, and repairing — entirely through conversation.

### One-time setup

**Prerequisites:** Java 25, [Claude Code CLI](https://claude.ai/code), `ANTHROPIC_API_KEY`

```bash
# 1. Build the codegen JAR (from this repo)
make install && make codegen-build

# 2. Copy the template and drop the JAR in place
cp -r templates/crablet-app ../my-service
cp embabel-codegen/target/embabel-codegen.jar ../my-service/tools/
```

### Start Claude Code

```bash
cd ../my-service
export ANTHROPIC_API_KEY=sk-ant-...
claude
```

The template's `.claude/settings.json` wires `embabel-codegen` as an MCP server. You never call `java -jar` directly — Claude Code calls the tools on your behalf.

### Describe one outcome

```text
Add the first vertical slice: Submit Loan Application.

Outcome:
- a customer submits a loan application
- Crablet records LoanApplicationSubmitted
- reviewers can query pending applications

Use the Crablet feature-slice workflow.
Ask for missing facts before changing files.
```

Claude will:
1. Ask for the missing business facts (entity identity, idempotency, required fields, read model columns)
2. Run `/event-modeling` to update `event-model.yaml`
3. Call `embabel_plan` and show you the planned artifact list
4. Wait for your approval before calling `embabel_generate`
5. Fix any compile errors and tell you when to run `./mvnw verify`

Repeat for each new slice. Update `event-model.yaml` when something is structural; edit generated code only for behavior the model cannot express.

**Codegen is optional.** The stable runtime APIs (`CommandHandler`, `ViewProjector`, `CommandExecutor`) work independently — you can build a full Crablet application without the generator. The AI-first path is a productivity layer on top, not a requirement.

## When Crablet Fits

Crablet is a good fit when command decisions depend on more than one entity stream, consistency is naturally query-based, and you want the code to make concurrency semantics explicit.

It is probably not the right tool if plain CRUD is enough, one aggregate per command already fits your domain, or your team is not ready to standardize on Java 25.

## Learning And Deployment

- **Learning mode:** run one application instance with commands, views, automations, and outbox together. See [Learning Mode](docs/user/LEARNING_MODE.md).
- **Command-only production:** applications using only `crablet-eventstore` and `crablet-commands` can scale horizontally in the normal Spring Boot way. See [Commands-First Adoption](docs/user/COMMANDS_FIRST_ADOPTION.md).
- **Poller-backed production:** applications enabling views, outbox, or automations should default to **one application instance per cluster** for the simplest topology, or use one singleton worker service per poller-backed module for isolation. See [Deployment Topology](docs/user/DEPLOYMENT_TOPOLOGY.md).

## Why Java 25

Crablet intentionally targets Java 25. The framework leans on modern Java features such as records, sealed types, and pattern matching to keep the public API small and explicit.

## DCB At A Glance

Traditional event sourcing ties consistency to a single aggregate stream. DCB lets you define consistency boundaries dynamically, so a command can check consistency across multiple entity types using a query.

Crablet maps that model onto three append methods. These are **Crablet API terms**, not DCB spec vocabulary:

| Method | Use case | Concurrency check |
|--------|----------|-------------------|
| `appendIdempotent` | Entity creation and duplicate prevention | Uniqueness/idempotency check |
| `appendNonCommutative` | State-dependent operations | Stream-position-based conflict detection |
| `appendCommutative` | Order-independent operations | None, optionally guarded by lifecycle checks |

Read more in [DCB And Crablet](crablet-eventstore/docs/DCB_AND_CRABLET.md) and [Command Patterns](crablet-eventstore/docs/COMMAND_PATTERNS.md).

## Documentation

[Documentation index](docs/README.md) — how **user** (`docs/user/`) and **dev** (`docs/dev/`) are organized.

### Framework

[Quickstart](docs/user/QUICKSTART.md) · [Tutorial](docs/user/TUTORIAL.md) · [Create a new Crablet app manually](docs/user/CREATE_A_CRABLET_APP.md) · [Learning mode](docs/user/LEARNING_MODE.md) · [Commands-first adoption](docs/user/COMMANDS_FIRST_ADOPTION.md) · [Module reference](docs/user/MODULES.md) · [Public API](docs/user/PUBLIC_API.md) · [Deployment topology](docs/user/DEPLOYMENT_TOPOLOGY.md) · [DCB and Crablet](crablet-eventstore/docs/DCB_AND_CRABLET.md) · [Command patterns](crablet-eventstore/docs/COMMAND_PATTERNS.md) · [Configuration](docs/user/CONFIGURATION.md) · [Build](docs/user/BUILD.md) · [Performance](docs/user/PERFORMANCE.md) · [Troubleshooting](docs/user/TROUBLESHOOTING.md) · [Upgrade](docs/user/UPGRADE.md) · [Management API](docs/user/MANAGEMENT_API.md) · [Fault recovery](docs/user/FAULT_RECOVERY.md) · [Leader election](docs/user/LEADER_ELECTION.md) · [Connection poolers](crablet-eventstore/docs/CONNECTION_POOLERS.md) · [Observability](docs/user/OBSERVABILITY.md) · [Wallet example](wallet-example-app/README.md)

### AI tooling

[AI-first workflow](docs/user/ai-tooling/AI_FIRST_WORKFLOW.md) · [Feature slice workflow](docs/user/ai-tooling/FEATURE_SLICE_WORKFLOW.md) · [Event modeling](docs/user/ai-tooling/EVENT_MODELING.md) · [Event model format](docs/user/ai-tooling/EVENT_MODEL_FORMAT.md) · [Templates](templates/README.md) · [Crablet app template](templates/crablet-app/README.md) · [Embabel codegen](embabel-codegen/README.md)

Contributors: see [Build](docs/user/BUILD.md), [CLAUDE.md](CLAUDE.md), and [framework development docs](docs/dev/README.md) (maintainer plans, reviews, doc verification).

## License

MIT License - see [LICENSE](LICENSE).
