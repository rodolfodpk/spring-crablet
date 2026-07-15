---
name: crablet-greenfield
description: >
  Use this skill for the full greenfield Crablet journey: bootstrapping a new
  app and evolving it through hand-written commands, views, automations, and
  outbox, one vertical slice at a time, with local verification.
---

# Crablet Greenfield

This skill is the entry point when the user asks how to start a Crablet app from scratch or wants
help pacing the whole lifecycle. It coordinates existing depth skills; it does not replace their
playbooks.

## Routing

| Phase | Use |
|-------|-----|
| App bootstrap and baseline | `crablet-greenfield`, then `crablet-local-dev` if local setup fails |
| Slice implementation (handlers, views, automations, outbox) | `crablet-app-dev` |
| DCB pattern, tags, guard events, concurrency diagnosis | `crablet-dcb` |
| Local Kubernetes, once needed | `crablet-k8s` (pré-1.0/experimental) |

An AI-assisted codegen path (`event-model.yaml` workshop → generated structural code) exists as
a separate, pré-1.0/experimental track — see `crablet-event-modeling`, `crablet-codegen`, and
`docs/dev/PRODUCT_ROADMAP.md`. It is optional; this skill's default path is manual Java.

## Phase A - Repo And Runtime Baseline

Preferred path: copy `templates/crablet-app`, install framework artifacts when they are not yet
published. Align the exact setup with `docs/user/CREATE_A_CRABLET_APP.md`, `docs/user/BUILD.md`,
and `templates/crablet-app/README.md`.

Alternative path: use Spring Initializr or `curl` to create a Spring Boot app, then wire Crablet
manually from `docs/user/CREATE_A_CRABLET_APP.md`.

Phase A is done when:

- the app is a clear repo boundary separate from `spring-crablet`
- Java 25 and PostgreSQL are available
- `./mvnw verify` can run from the app root

## Phase B - Land One Slice

Use `crablet-app-dev` for the feature-slice loop:

1. Clarify the outcome and missing business facts.
2. Write the command, event(s), handler, and (if needed) view/automation/outbox by hand.
3. Write handler/view/automation tests.
4. Run `./mvnw verify`.

## Phase C - Evolve The App

Treat every new capability as another vertical slice. Return to Phase B for the next command,
view, automation, or outbox publisher.

When adding views, automations, or outbox, confirm the required Maven modules and runtime wiring.
Poller-backed modules process at least once, so views, automations, and publishers must be
idempotent. For production topology, point to `docs/user/DEPLOYMENT_TOPOLOGY.md`; command-only
apps scale horizontally, while poller-backed modules need the documented singleton-worker shape.

Use `crablet-dcb` whenever command consistency, tags, `guardEvents`, or conflict behavior are not
obvious.

```mermaid
flowchart LR
  bootstrap[BaselineRepo]
  slice[WriteSliceAndVerify]
  bootstrap --> slice
  slice -->|"next feature"| slice
```
