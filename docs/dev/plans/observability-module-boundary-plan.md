# Observability and Module Boundary Plan

## Context

This plan addresses three module-boundary issues:

1. `crablet-metrics-micrometer` imports metric event types from commands, views, automations,
   outbox, event-poller, and eventstore, forcing optional modules onto the classpath.
2. `com.crablet.eventpoller.internal.sharedfetch` is consumed by sibling module
   auto-configurations, so it is an extension API in practice.
3. `ViewBackedAutomationHandler` requires `crablet-views`; this is already documented in the
   interface Javadoc and should remain covered by tests and docs.

Spring Boot 4.1 keeps Micrometer Observation as the recommended application instrumentation API
for metrics and traces, while supporting OpenTelemetry/OTLP as the export path. Crablet should
therefore instrument with Spring Observation and recommend OTLP/OpenTelemetry export, not bind
core modules directly to the OpenTelemetry SDK.

## Decisions

- Do not deepen `crablet-metrics-micrometer`.
- Do not make a new Crablet-only metrics event bus the primary design.
- Use `ObservationRegistry` / Micrometer Observation for new instrumentation.
- Treat OpenTelemetry as the recommended backend/export path through Boot's OTLP support.
- Keep optional modules independently adoptable: adding observability must not require commands,
  views, automations, outbox, and event-poller all at once.
- Promote shared-fetch sibling-module contracts out of `internal`.

## Phase 1 - Stop the Coupling

Freeze `crablet-metrics-micrometer` as compatibility code:

- Mark its README as transitional.
- Do not add new metric event imports there.
- Add a dependency check or focused test proving the module graph problem, so future changes do
  not hide it.

Expected result: no new code increases the current cross-module metrics coupling.

## Phase 2 - Introduce Observation Conventions

Add a small neutral observability surface, either in a new `crablet-observability` module or in
the lowest existing module that does not force optional features together.

It should contain only:

- observation names;
- low-cardinality key names;
- small convention/context helpers if needed;
- no dependency on commands, views, automations, outbox, event-poller, Micrometer registries, or
  OpenTelemetry SDK types.

Prefer names such as:

- `crablet.eventstore.append`
- `crablet.command.handle`
- `crablet.poller.fetch`
- `crablet.processor.dispatch`
- `crablet.view.project`
- `crablet.automation.decide`
- `crablet.outbox.publish`

Keep high-cardinality values out of default tags.

## Phase 3 - Move Instrumentation Ownership Into Owning Modules

Each optional module records its own observations when `ObservationRegistry` is present:

- `crablet-eventstore`: append, append-if, DCB violation, projection timings.
- `crablet-commands`: command execution, success/failure, idempotent duplicate handling.
- `crablet-event-poller`: fetch cycles, dispatch cycles, leadership, backoff.
- `crablet-views`: projection duration, projected event count, projection errors.
- `crablet-automations`: decision duration, emitted command count, decision errors.
- `crablet-outbox`: publish duration, published event count, publisher errors.

Implementation shape:

- Add conditional auto-configuration per module, not one central collector.
- Use `@ConditionalOnClass(ObservationRegistry.class)` and `@ConditionalOnBean(ObservationRegistry.class)`
  where Spring configuration is required.
- Prefer direct observation around the work over publishing metric events and collecting them
  elsewhere.
- Keep existing `MetricEvent` records only as compatibility until the Micrometer collector is
  retired.

Expected result: adding `crablet-views` plus observability does not pull in outbox or automations;
adding `crablet-commands` plus observability does not pull in views.

## Phase 4 - Rework or Retire `crablet-metrics-micrometer`

After module-owned observations exist:

- Option A: deprecate `crablet-metrics-micrometer` and remove it in the next breaking release.
- Option B: shrink it to shared names/docs/tests only, with no imports from optional modules.

Do not keep the current central `MicrometerMetricsCollector` as the long-term path.

Prometheus/Grafana examples should move from "add Crablet Micrometer collector" to "enable Spring
Boot Actuator and OTLP/Prometheus export for observations".

## Phase 5 - Promote Shared-Fetch Extension API

Move shared-fetch types consumed by views, automations, and outbox from:

```text
com.crablet.eventpoller.sharedfetch
```

to:

```text
com.crablet.eventpoller.sharedfetch
```

Add `package-info.java` that states:

- this package is an internal framework extension surface for Crablet sibling modules;
- it is not intended as application user API;
- changes require coordination with views, automations, and outbox.

Update imports and tests in:

- `crablet-views`
- `crablet-automations`
- `crablet-outbox`
- `crablet-event-poller`

Expected result: package naming matches actual stability expectations.

## Phase 6 - Verify View-Backed Automation Contract

`ViewBackedAutomationHandler` already documents that `crablet-views` must be on the classpath and
that startup fails when referenced views are missing or empty.

Keep this as a verification item:

- ensure docs mention the requirement where view-backed automations are introduced;
- keep tests for missing views and empty view subscriptions;
- consider a clearer startup failure when `ViewBackedAutomationHandler` exists but no view
  subscription infrastructure is available.

## Verification Matrix

Add or keep tests that prove:

- `crablet-commands` starts without views, automations, outbox, or metrics collector.
- `crablet-views` starts with event-poller and observability, without outbox or automations.
- `crablet-outbox` starts with event-poller and observability, without views or automations.
- `crablet-automations` starts without views for plain `AutomationHandler`.
- `ViewBackedAutomationHandler` fails clearly without matching view subscriptions.
- Observability disabled still works with no `ObservationRegistry`.
- Observability enabled records observations without requiring optional sibling modules.
- OTLP/OpenTelemetry export is documented as the recommended runtime path.

## Rollout Order

1. Promote shared-fetch package and update imports.
2. Add observability conventions.
3. Instrument eventstore and commands first.
4. Instrument event-poller, views, automations, and outbox.
5. Update observability docs and examples for OTLP/OpenTelemetry export.
6. Deprecate or shrink `crablet-metrics-micrometer`.
7. Remove compatibility metric events only in a breaking release.
