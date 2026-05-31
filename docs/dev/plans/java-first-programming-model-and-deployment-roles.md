# Plan: Clarify Java-First Programming Model And Deployment Roles

## Summary

Document Crablet as a **Java-first framework**. The runtime contract is Java APIs, Spring beans, and application modules. `event-model.yaml` is a tooling artifact for codegen, AI workflow, diagrams, validation, and Kubernetes generation; it is not required runtime configuration and must not be the only way to express framework behavior.

The docs should separate two layers:

- **Programming model:** how users organize Java code: commands, views, automations, outbox, shared contracts.
- **Deployment model:** which runtime processors are enabled in each process: command API, views worker, automations worker, outbox worker.

## Key Changes

- Update user docs to state the priority explicitly:
  - Hand-written Java is the source of truth.
  - `event-model.yaml` must generate the same Java shape a user could write manually.
  - Runtime features must work for pure Java consumers without requiring YAML.

- Document recommended Java module organization:
  - `domain`: events, commands, tags, shared query helpers.
  - `view-contracts`: `ViewSubscription` metadata and read-model contracts needed by automations.
  - `views`: view projectors and view processor wiring.
  - `automations`: automation handlers and command emission logic.
  - `outbox`: integration/event publishing handlers.
  - In small apps these can all live in one module; in larger systems they can be split into Maven/Gradle modules.

- Clarify deployment roles:
  - Command API runs command handlers and HTTP/API entrypoints.
  - Views worker runs the views processor.
  - Automations worker runs the automations processor.
  - Outbox worker runs the outbox processor.
  - Enable/disable flags control which processors run in a process, not whether the Java contracts are visible on the classpath.

- Clarify view-backed automation dependency:
  - A view-backed automation depends on the **view contract**: `ViewSubscription` metadata plus read-model access.
  - It does not need to run the views processor in the same process.
  - In split deployment, the views worker projects read models; the automations worker uses those read models and has the `ViewSubscription` beans available for wake-event inference.

- Clarify poller terminology:
  - Crablet shares poller infrastructure and event-store access patterns.
  - Views, automations, and outbox are still separate module consumers with separate progress tracking.
  - Do not describe this as one global poller unless the runtime actually implements one.

## Documentation Targets

- `docs/user/DEPLOYMENT_TOPOLOGY.md`
  - Add the two-layer model: Java modules vs runtime deployment roles.
  - Explain why enable/disable flags exist.
  - Clarify that view-backed automations do not imply enabling views in the automation worker.

- `docs/user/MODULES.md`
  - Add recommended module layouts for monolith, modular monolith, and split workers.
  - State that Java APIs are canonical and YAML is tooling.

- `crablet-automations/README.md`
  - Clarify `ViewBackedAutomationHandler` requirements in Java-first terms.
  - Say `event-model.yaml` is optional and only mirrors/generates the Java contract.

- `crablet-codegen` generated Kubernetes README text
  - Make generated docs say manifests are derived from `event-model.yaml`, but runtime behavior is still Java/Spring bean based.

## Test Plan

- Documentation review:
  - Search for wording that implies `event-model.yaml` is required at runtime and replace it.
  - Search for wording that implies view-backed automations require `crablet.views.enabled=true` in the automation worker and replace it.
  - Search for wording that implies one global shared poller and replace it with module-scoped progress/poller language.

- Existing runtime/codegen tests remain the behavioral guard:
  - Automations can resolve `ViewSubscription` beans without enabling the views processor.
  - K8s topology derives automation wake events from `EventModel.automationWakeEvents(...)`.
  - Generated automations worker keeps `CRABLET_VIEWS_ENABLED=false`.

## Assumptions

- Java-first is the architectural priority.
- `event-model.yaml` remains supported and important, but only as tooling input for AI/codegen/diagram/K8s workflows.
- Pure Java consumers must be able to use commands, views, automations, and outbox without adopting the YAML workflow.
