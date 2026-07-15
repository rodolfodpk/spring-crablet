# Plan: Deployment and Kubernetes Updates For View-backed Automations

**Status:** Draft.

## Summary

Update runtime wiring, deployment docs, Kubernetes codegen, and examples so view-backed automations work in distributed deployments without enabling the views processor in the automations worker.

Chosen topology: decouple view metadata lookup from view processing.

View-backed automations need `ViewSubscription` metadata to infer wake events. They do not need `crablet.views.enabled=true`, and the automations worker should keep `CRABLET_VIEWS_ENABLED=false`.

## Key Changes

### 1. Runtime Wiring

In `AutomationsAutoConfiguration.ViewSubscriptionLookupConfiguration`, replace the current dependency:

```java
@Qualifier("viewSubscriptions")
ObjectProvider<Map<String, ViewSubscription>> subscriptionsProvider
```

with:

```java
ObjectProvider<List<ViewSubscription>> subscriptionsProvider
```

Build the lookup map directly from individual `ViewSubscription` beans by `getViewName()`. The `ObjectProvider<List<T>>` pattern is already established in this codebase — see `automationHandlers(ObjectProvider<List<AutomationHandler>>)` in the same class.

Keep `@ConditionalOnClass(ViewSubscription)` so this only activates when crablet-views is on the classpath. Since `ViewSubscription` implementations are application-defined beans, they register in the application context regardless of `crablet.views.enabled`.

Update the error message in `AutomationDefinitionResolver:63` to remove "views are enabled". The corrected requirement is: crablet-views on the classpath plus the referenced `ViewSubscription` bean registered.

### 2. Kubernetes / Codegen

Keep the distributed automations worker isolated:
- `CRABLET_AUTOMATIONS_ENABLED=true`
- `CRABLET_VIEWS_ENABLED=false`

In `K8sTopology.from()`, replace the manual `triggeredBy` loop (lines 62–66) with a call to `model.automationWakeEvents(a)` for each automation. That method already exists in `EventModel` (`EventModel.java:45–53`) and handles `triggeredBy`, `readsViews`, `wakeEventsExtra`, `wakeEventsExclude`, and throws `IllegalArgumentException("View not found: " + name)` for missing view names via `viewNamed()`.

The fix is a targeted replacement of the existing loop — no new method or separate data path is needed.

Edge case: if `automationWakeEvents(a)` returns an empty list (all referenced views have empty `reads()`), fail at topology build time with a descriptive message. An empty wake-event set at K8sTopology resolution means the generated KEDA trigger would be `ARRAY[]::text[]`, which is not valid for a poller-backed automation.

Ensure automations with only `readsViews` do not produce empty KEDA event arrays.

### 3. Documentation

- `docs/user/DEPLOYMENT_TOPOLOGY.md`: view-backed automations read view metadata and read-model tables, but do not require the views processor to run in the automations worker.
- Generated `README-k8s.md`: distributed workers stay isolated; views worker projects views, automations worker runs automations and may use `ViewSubscription` metadata for wake-event inference.
- `crablet-automations/README.md`: requirement becomes "crablet-views on the classpath and referenced `ViewSubscription` beans registered," not "views must be enabled."

## Test Plan

### Runtime

- Test `ViewSubscriptionLookupConfiguration` with `crablet.automations.enabled=true`, `crablet.views.enabled=false`, and application-provided `ViewSubscription` beans in context.
- Prefer a narrow Spring context test for the configuration; a direct unit test of the lookup bean construction is acceptable if full auto-configuration is too heavy.
- Verify a view-backed automation resolves wake events when views are disabled.
- Verify missing referenced views and empty final wake-event sets still fail startup with updated messages.

### K8s / Codegen

- Add `K8sTopologyTest` coverage: automation with `readsViews` → `model.views()` → `reads` produces the expected `automationEventTypes`.
- Add coverage that `wakeEventsExtra` is included and `wakeEventsExclude` is removed.
- Verify generated distributed automations worker deployment YAML (not the API deployment) keeps `CRABLET_VIEWS_ENABLED=false`.
- Verify generated automations `ScaledObject` query contains inferred event types for `readsViews` automations.
- Verify that a `readsViews` automation whose referenced views all have empty `reads()` fails at topology build time.
- Keep existing `triggeredBy` automation behavior unchanged.

## Assumptions

- `ViewSubscription` beans are application metadata and can exist independently of `crablet.views.enabled`.
- Running view processors inside automations workers is undesirable because it blurs module isolation and creates unnecessary leader-election competition.
- KEDA trigger event types should mirror effective automation wake events, including `readsViews`, `wakeEventsExtra`, and `wakeEventsExclude`.
- `event-model.yaml` remains codegen/deployment tooling input only; pure Java deployments rely on runtime beans and properties.

## Non-goals

- Do not co-locate the views processor in the automations worker.
- Do not make `event-model.yaml` runtime configuration.
- Do not introduce projection barriers or view-row pollers in this work.
