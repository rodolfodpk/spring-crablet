---
name: crablet-k8s
description: >
  Use this skill for Crablet-specific Kubernetes concerns in this application: deployment.topology,
  manifests under k8s/base, singleton poller-backed workers, KEDA, and NOTIFY vs scale-to-zero.
  For generic manifest hardening, Helm, RBAC, and fleet patterns, open the spring-crablet parent
  repo skill /kubernetes-skill.
---

# Crablet Kubernetes (app template)

This skill is for **Crablet-shaped** manifests generated from **`event-model.yaml`**. Generic
Kubernetes quality bar → **`/kubernetes-skill`** on the **spring-crablet** repository (not
vendored in this app template).

Local build / Testcontainers / codegen → `/crablet-local-dev`.

## Topology and generated layout

Define **`deployment:`** in **`event-model.yaml`**. **`make k8s`** writes **`k8s/base/`**.
Follow **`k8s/base/README-k8s.md`** for secrets and environment variables after generation.

| `deployment.topology` | Result |
|---|---|
| `monolith` | One Deployment runs command API plus enabled modules |
| `distributed` | `command-api` (can scale horizontally) plus **singleton** Deployments per poller-backed module |

**Poller-backed modules** (`views`, `automations`, `outbox`): run as **singletons** per module.
Horizontal scaling applies to **`command-api`** in **distributed** mode only—not to duplicate view
processors for the same stream.

## KEDA basics

Keyed under `deployment.keda` in **`event-model.yaml`** (defaults omitted here—see root docs).

- **`deployment.keda.enabled`** — generates KEDA `ScaledObjects` when true.
- **`minReplicas: 0`** — scale workers to zero (cost savings; wakes only on scaler interval).
- **Monolith** forces workers up when zero would violate an always-available command tier.

Scale-to-zero and **PostgreSQL NOTIFY** see below.

## NOTIFY and scale-to-zero

Workers subscribe to Postgres **LISTEN/NOTIFY** for quick wakeups. At **zero replicas** there is
no pod to listen—work resumes only after KEDA’s PostgreSQL scaler runs (minimum lag tied to the
polling interval). **Cheap but higher latency.**

If automation or outbox delivery needs low latency, keep **`minReplicas: 1`** for those workers.

## Direct JDBC for notifications

Configure **`crablet.event-poller.notifications.jdbc-url`** as a **direct** PostgreSQL JDBC URL—
not PgBouncer transaction pooling, not PgCat, not RDS Proxy. In Kubernetes, expose a Postgres
service URL dedicated to NOTIFY if your app pool URLs go through proxies.
