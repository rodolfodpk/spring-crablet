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

then default to **one application instance per cluster**.

These modules depend on the event poller. The simplest and clearest deployment model is correctness-first, single-instance execution.

## Recommended Positioning

Do not present poller-backed modules as part of the first adoption promise.

Instead:

- teach them in single-instance learning mode
- position them as optional add-ons
- document their topology constraint next to every module that depends on the poller

## Short Rule To Repeat Everywhere

Use this wording consistently:

> Command-only applications can scale horizontally. If `crablet-views`, `crablet-outbox`, or `crablet-automations` are enabled, default to one application instance per cluster.

## Where This Rule Should Appear

- root `README.md`
- starter documentation
- `crablet-views/README.md`
- `crablet-outbox/README.md`
- `crablet-automations/README.md`
- learning guides and example app docs
