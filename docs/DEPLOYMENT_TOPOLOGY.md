# Deployment Topology

Crablet has two distinct operational modes. The documentation should present them clearly because they scale differently.

## 1. Command-Only Applications

If your application only uses:

- `crablet-eventstore`
- `crablet-commands`

then your service can scale horizontally like a normal Spring Boot write application.

This is the recommended first production adoption path.

## 2. Applications With Poller-Backed Modules

If your application enables any of:

- `crablet-views`
- `crablet-outbox`
- `crablet-automations`

then default to **one application instance per cluster** for the simplest correctness-first topology. In that default shape, one service instance runs commands/API plus the enabled poller-backed modules.

These modules depend on the event poller, but they do not share one global poller. Views, outbox, and automations each wire their own module-level poller with its own scheduler, leader election key, and progress tracking.

If you want operational isolation, use singleton worker services per module:

- one singleton views worker service with one active views poller
- one singleton outbox worker service with one active outbox poller
- one singleton automations worker service with one active automations poller

Different pods or VMs may hold leadership for different modules at the same time. Extra replicas of the same singleton worker service mainly provide standby failover; they do not make that module's same processors run faster.

## Recommended Positioning

Do not present poller-backed modules as part of the first adoption promise.

Instead:

- teach them in single-instance learning mode
- position them as optional add-ons
- document their topology constraint next to every module that depends on the poller

## Short Rule To Repeat Everywhere

Use this wording consistently:

> Command-only applications can scale horizontally. If `crablet-views`, `crablet-outbox`, or `crablet-automations` are enabled, default to one application instance per cluster.
>
> When modules are deployed separately, use singleton worker services: one active poller per poller-backed module, with optional standby replicas for failover.

## Where This Rule Should Appear

- root `README.md`
- starter documentation
- `crablet-views/README.md`
- `crablet-outbox/README.md`
- `crablet-automations/README.md`
- learning guides and example app docs

## Kubernetes (optional)

The rules above (singleton workers per module, monolith vs split) can be **encoded in YAML** for Kubernetes. The [app template](../templates/crablet-app/README.md) and [Embabel Codegen](../embabel-codegen/README.md) support `make k8s` / `java -jar embabel-codegen.jar k8s`, which writes `k8s/base` from `event-model.yaml` including a `deployment:` block. See the generated `k8s/base/README-k8s.md` in your app for KEDA, secrets, and env vars — that file is the operational layer; this document stays the conceptual source for **why** the topology is shaped that way. See also [Module reference](MODULES.md).
