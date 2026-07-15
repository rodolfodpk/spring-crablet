# Crablet

[![Java CI](https://github.com/rodolfodpk/spring-crablet/actions/workflows/maven.yml/badge.svg)](https://github.com/rodolfodpk/spring-crablet/actions/workflows/maven.yml)
[![codecov](https://codecov.io/gh/rodolfodpk/spring-crablet/branch/main/graph/badge.svg)](https://codecov.io/gh/rodolfodpk/spring-crablet)
[![Java](https://img.shields.io/badge/Java-25-orange?logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/25/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

Crablet is a Java 25 / Spring Boot event-sourcing framework built on the Dynamic Consistency
Boundary (DCB) pattern: consistency checks scoped by tags instead of one aggregate per command.

## Quickstart

- [Quickstart](docs/user/QUICKSTART.md) — wallet reference app, fastest path
- [Tutorial](docs/user/TUTORIAL.md) — hands-on tutorial series
- [Module reference](docs/user/MODULES.md) — what each module does

```bash
make install    # full build with unit tests
make start      # run the wallet example app on :8080
```

See [`docs/user/BUILD.md`](docs/user/BUILD.md) for the full command reference.

## Stable Modules

`crablet-eventstore` (event store, DCB appends, queries, projections), `crablet-commands`
(command handlers, `CommandExecutor`), `crablet-commands-web` (generic HTTP command API),
`crablet-views` (materialized read models), `crablet-automations` (event-driven reactions),
`crablet-outbox` (reliable external publication), `crablet-event-poller` (shared polling
infrastructure), `crablet-metrics-micrometer` (optional metrics).

## What You Build

A Crablet application is a Spring Boot service backed by the Crablet event store. Commands make
decisions against event history, append new events, and optionally feed read models, automations,
and outbox publishers — all written directly in Java against the framework's public API.

For the simplest correctness-first deployment topology, run **1 application instance per cluster**; if views, automations, or outbox need operational isolation, use a singleton worker service per poller-backed module. See [Deployment Topology](docs/user/DEPLOYMENT_TOPOLOGY.md).

## When Crablet Fits

Crablet fits domains where command decisions depend on event history, consistency may cross
entity boundaries, and the team wants the model and runtime behavior to stay aligned.

It is probably not the right tool when plain CRUD is enough, one aggregate per command already
explains the whole domain, or Java 25 / Spring Boot is not a good platform choice for the team.

## Documentation

- [Documentation index](docs/README.md)
- [User docs](docs/user/README.md)
- [Framework development docs](docs/dev/README.md)
- [GitHub Pages site](https://rodolfodpk.github.io/spring-crablet/)
- [Roadmap](docs/dev/PRODUCT_ROADMAP.md) — AI-assisted codegen and Kubernetes generation status (pré-1.0/experimental)

## License

MIT License - see [LICENSE](LICENSE).
