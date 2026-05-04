---
name: crablet-k8s
description: >
  Use this skill for Crablet-specific Kubernetes deployment concerns: KEDA configuration,
  LISTEN/NOTIFY and scale-to-zero interaction, deployment.topology → k8s/base manifest
  mapping, and leader election behavior under KEDA. For generic K8s manifest quality,
  Helm, RBAC, and security hardening use /kubernetes-skill.
---

# Crablet Kubernetes Deployment

Crablet-specific K8s/KEDA concerns. For generic manifest quality, security contexts,
RBAC, and Helm patterns use `/kubernetes-skill`.

## Routing

- Generic K8s manifests, Helm, RBAC, security hardening → `/kubernetes-skill`
- KEDA configuration, KEDA + Crablet wakeup, `deployment:` YAML block → this skill
- Local build, Testcontainers, codegen workflow → `/crablet-local-dev`

## Deployment Topology → Manifests

`make k8s` reads the `deployment:` block from `event-model.yaml` and writes `k8s/base/`.
See the generated `k8s/base/README-k8s.md` for fill-in steps, secrets, and env vars.

| `deployment.topology` | Generated shape |
|---|---|
| `monolith` | One Deployment running all enabled modules |
| `distributed` | `command-api` (N replicas) + one singleton Deployment per poller-backed module |

Poller-backed modules (views, automations, outbox) must run as singletons. Horizontal
scaling applies only to `command-api` in distributed topology.

## KEDA Configuration Keys and Defaults

| Key | Default | Notes |
|---|---|---|
| `deployment.keda.enabled` | `false` | `true` → generate KEDA ScaledObjects |
| `deployment.keda.minReplicas` | `0` | `0` = scale-to-zero; `1` = always-on |
| `deployment.keda.pollingInterval` | `30` | Seconds between KEDA PostgreSQL checks |

**Monolith ignores `keda.minReplicas: 0`** — forced to 1 because the command API must
stay available. PodDisruptionBudgets are omitted when `keda.minReplicas = 0`.

Scale-to-zero (`minReplicas: 0`) is only effective in distributed topology where
poller-backed workers are separate Deployments.

## KEDA + LISTEN/NOTIFY Interaction

Crablet workers wake on PostgreSQL NOTIFY for low-latency event processing. When KEDA
scales a worker to 0 replicas:

- The pod is gone — it cannot receive NOTIFY.
- Processing resumes only when KEDA's PostgreSQL scaler detects accumulated events and
  scales the pod back up. Minimum lag: `keda.pollingInterval` seconds.
- **Scale-to-zero trades event latency for cost.** Do not use `keda.minReplicas: 0`
  when low-latency automation or outbox delivery is required.

Use `keda.minReplicas: 1` to keep workers alive and preserve NOTIFY-driven wakeup.

## KEDA PostgreSQL TriggerAuthentication

KEDA's PostgreSQL scaler needs its own database connection — separate from the app's
`WriteDataSource` and `ReadDataSource`. It must be provided as a K8s secret referenced
by a `TriggerAuthentication` resource. See `k8s/base/README-k8s.md` for the exact
secret structure and required env var names.

## Leader Election Under KEDA

Crablet's leader election picks one active instance per poller-backed module. In K8s:

- `minReplicas: 1` — pod is always up; wins leader election immediately on startup.
- `minReplicas: 0` — when KEDA wakes the pod, it must win leader election before
  processing starts. Total lag = KEDA polling interval + leader election time.
- Multiple replicas of the same poller-backed worker (not recommended) compete for
  leadership; only one processes at a time. The others are idle standby.

## LISTEN/NOTIFY in K8s

`crablet.event-poller.notifications.jdbc-url` must be a direct PostgreSQL JDBC URL —
not PgBouncer in transaction mode, not PgCat, not RDS Proxy. In K8s, configure a
direct Postgres service URL for this property, separate from the connection pool URL
used by the application datasources.
