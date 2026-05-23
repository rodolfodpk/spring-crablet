# Crablet

[![Java CI](https://github.com/rodolfodpk/spring-crablet/actions/workflows/maven.yml/badge.svg)](https://github.com/rodolfodpk/spring-crablet/actions/workflows/maven.yml)
[![codecov](https://codecov.io/gh/rodolfodpk/spring-crablet/branch/main/graph/badge.svg)](https://codecov.io/gh/rodolfodpk/spring-crablet)
[![Java](https://img.shields.io/badge/Java-25-orange?logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/25/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

Crablet is a Java 25 / Spring Boot stack for building event-sourced applications from event-modeled domains.

The project has three related tracks. Each track has a different maturity level, so the docs are split by what you are trying to do.

| Track | Status | Start here |
|------|--------|------------|
| **Spring-native runtime** for commands, events, consistency checks, views, automations, outbox publishing, and tests | **Near complete** | [Quickstart](docs/user/QUICKSTART.md), [Tutorial](docs/user/TUTORIAL.md), [Module reference](docs/user/MODULES.md) |
| **AI-first Event Modeling and code generation** from workshop conversation to `event-model.yaml` to Spring code | **In progress** | [AI-first workflow](docs/user/ai-tooling/AI_FIRST_WORKFLOW.md), [Feature-slice workflow](docs/user/ai-tooling/FEATURE_SLICE_WORKFLOW.md), [Event model format](docs/user/ai-tooling/EVENT_MODEL_FORMAT.md) |
| **Local Kubernetes generation** from the modeled service shape | **Early / planned** | [Deployment topology](docs/user/DEPLOYMENT_TOPOLOGY.md), [App template](templates/crablet-app/README.md), [Codegen CLI](embabel-codegen/README.md) |

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

For the simplest correctness-first deployment topology, run **1 application instance per cluster**; if views, automations, or outbox need operational isolation, use a singleton worker service per poller-backed module.

## When Crablet Fits

Crablet fits domains where command decisions depend on event history, consistency may cross entity boundaries, and the team wants the model, generated code, and runtime behavior to stay aligned.

It is probably not the right tool when plain CRUD is enough, one aggregate per command already explains the whole domain, or Java 25 / Spring Boot is not a good platform choice for the team.

## Documentation

- [Documentation index](docs/README.md)
- [User docs](docs/user/README.md)
- [AI tooling docs](docs/user/ai-tooling/AI_FIRST_WORKFLOW.md)
- [AI skills](docs/user/ai-tooling/AI_SKILLS.md)
- [Framework development docs](docs/dev/README.md)
- [GitHub Pages site](https://rodolfodpk.github.io/spring-crablet/)
- [Interactive concept map](https://rodolfodpk.github.io/spring-crablet/concepts.html)

## License

MIT License - see [LICENSE](LICENSE).
