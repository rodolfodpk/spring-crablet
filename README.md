# Crablet

[![Java CI](https://github.com/rodolfodpk/spring-crablet/actions/workflows/maven.yml/badge.svg)](https://github.com/rodolfodpk/spring-crablet/actions/workflows/maven.yml)
[![codecov](https://codecov.io/gh/rodolfodpk/spring-crablet/branch/main/graph/badge.svg)](https://codecov.io/gh/rodolfodpk/spring-crablet)
[![Java](https://img.shields.io/badge/Java-25-orange?logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/25/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

Crablet is a Java 25 / Spring Boot stack for building event-sourced applications from event-modeled domains.

The project has three goals:

1. Provide a small Spring-native runtime for commands, events, consistency checks, views, automations, outbox publishing, and tests.
2. Use AI tooling to support Event Modeling workshops, keep `event-model.yaml` as the structural source of truth, and generate Spring application code from that model.
3. Generate small local Kubernetes deployments so teams can test a modeled service outside the IDE without designing production infrastructure first.

The AI tooling is optional. The Java runtime APIs work directly when a team wants to build by hand, customize generated code, or adopt Crablet one module at a time.

## What You Build

A Crablet application is a Spring Boot service backed by the Crablet event store. Commands make decisions against event history, append new events, and optionally feed read models, automations, and outbox publishers.

The same model can drive:

- command records, handlers, validation, and consistency decisions
- event records and sealed event interfaces
- state projectors and materialized views
- automations that react to events and emit commands
- outbox publishers for integration events
- focused test scaffolding
- local Kubernetes manifests for testing

## AI-Assisted Workflow

Crablet is designed to start from an Event Modeling conversation. During a workshop, the team captures outcomes, commands, events, views, policies, and integration points. The assistant turns that into `event-model.yaml`, plans the generated artifacts, asks for approval, and then generates the Spring code.

```text
Describe one feature slice
  -> update event-model.yaml
  -> plan generated artifacts
  -> approve
  -> generate Spring code
  -> compile and repair
  -> optionally generate local k8s manifests
```

Claude Code and Cursor can call the generator through MCP. Codex and terminal workflows can use the same Makefile and CLI commands.

```bash
make install && make codegen-build
cp -r templates/crablet-app ../my-service
cp embabel-codegen/target/embabel-codegen.jar ../my-service/tools/
cd ../my-service
make plan
make generate
make verify
make k8s
```

See [AI-first workflow](docs/user/ai-tooling/AI_FIRST_WORKFLOW.md), [Feature slice workflow](docs/user/ai-tooling/FEATURE_SLICE_WORKFLOW.md), [Event Modeling](docs/user/ai-tooling/EVENT_MODELING.md), and [Event model format](docs/user/ai-tooling/EVENT_MODEL_FORMAT.md).

## Java Spring Runtime

Crablet can also be used as a normal Java framework. The core path is `EventStore`, command handlers, and `CommandExecutor`; views, automations, outbox, metrics, and the optional HTTP command adapter layer on top.

Start with [Quickstart](docs/user/QUICKSTART.md), [Tutorial](docs/user/TUTORIAL.md), [Create a new Crablet app manually](docs/user/CREATE_A_CRABLET_APP.md), [Module reference](docs/user/MODULES.md), [Event Store](crablet-eventstore/README.md), [Commands](crablet-commands/README.md), and the [Wallet example](examples/wallet-example-app/README.md).

## Local Kubernetes

The generator can create a small Kubernetes base from `event-model.yaml` and its `deployment:` block:

```bash
make k8s
```

This is for local and test environments. It helps teams exercise the service shape, secrets, environment variables, and worker topology early. Production topology still needs deliberate operational design.

See [Deployment topology](docs/user/DEPLOYMENT_TOPOLOGY.md), [Crablet app template](templates/crablet-app/README.md), and [Embabel codegen](embabel-codegen/README.md).

## When Crablet Fits

Crablet fits domains where command decisions depend on event history, consistency may cross entity boundaries, and the team wants the model, generated code, and runtime behavior to stay aligned.

It is probably not the right tool when plain CRUD is enough, one aggregate per command already explains the whole domain, or Java 25 / Spring Boot is not a good platform choice for the team.

## Documentation

- [Documentation index](docs/README.md)
- [User docs](docs/user/README.md)
- [AI tooling docs](docs/user/ai-tooling/AI_FIRST_WORKFLOW.md)
- [Framework development docs](docs/dev/README.md)
- [Interactive concept map](https://rodolfodpk.github.io/spring-crablet/concepts.html)

## License

MIT License - see [LICENSE](LICENSE).
