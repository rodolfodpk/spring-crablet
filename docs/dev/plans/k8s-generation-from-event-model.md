# Plan: K8s artifact generation from event-model.yaml (v3)

## Context

Generate correct, production-ready Kubernetes manifests from `event-model.yaml`.
Key value: encoding crablet's non-obvious topology rules so newcomers don't get them wrong.

## Recommended order

1. **[`README_ROOT_REFRESH.md`](README_ROOT_REFRESH.md)** — lands `docs/user/MODULES.md` and a cleaner top-level doc index; do this first so K8s and topology docs have stable targets.
2. **Refresh this plan** (this file) after model step completion — see **Current status** below.
3. **Continue implementation** — `K8sTopology`, `Dns1123`, `K8sGenerator`, CLI/MCP `k8s`, tests, skill tweaks as needed.

## Current status (keep in sync with the repo)

- **Done — deployment model (former Step 1):** `EventModel` has `@JsonProperty("deployment") DeploymentSpec deployment` with compact-constructor default to `DeploymentSpec.defaults()`. `DeploymentSpec` and `KedaSpec` exist under `embabel-codegen/.../model/`. The event-modeling **skill** documents `deployment` / KEDA / topology; keep it aligned when generated YAML or flags change.
- **Remaining:** K8s topology mapping, generator, `k8s` CLI + MCP, tests, Makefile `k8s` target, post-ship `DEPLOYMENT_TOPOLOGY.md` K8s subsection (per documentation strategy). CLI/MCP `k8s` not in checklist until implemented — update verified facts when added.

## Verified facts from the codebase

| Fact | Source / note |
|---|---|
| `EventModel` includes `deployment: DeploymentSpec` (defaults when absent) | `model/EventModel.java` |
| `DeploymentSpec`, `KedaSpec` exist | `model/DeploymentSpec.java`, `model/KedaSpec.java` |
| CLI: `plan`, `generate`, `init`, `mcp` (add `k8s` when implemented) | `CodegenCommand.java` |
| MCP tools: `embabel_generate`, `embabel_plan`, `embabel_init` (add `embabel_k8s` when implemented) | `McpServer.java` |
| No Mustache — Jackson YAML already in classpath | `embabel-codegen/pom.xml` |
| `EventSpec.name()`, not `.type()` | `model/EventSpec.java` |
| `ViewSpec.reads` → `List<String>` of raw event names | `model/ViewSpec.java` |
| `view_progress(view_name, last_position)` | `V3__view_progress_schema.sql` |
| `automation_progress(automation_name, last_position)` | `AutomationProgressTracker.java` |
| `outbox_topic_progress(topic, publisher, last_position)` | `OutboxProgressTracker.java` |
| Module enable: `crablet.views.enabled`, `crablet.automations.enabled`, `crablet.outbox.enabled` | `*AutoConfiguration.java` |
| `CRABLET_VIEWS_ENABLED=true` enables **all** registered view processors, not one | `ViewsAutoConfiguration.java` |
| Makefile variable is `CRABLET_CODEGEN_JAR` | `templates/crablet-app/Makefile` |
| Event-modeling skill: `deployment` / KEDA / topology | `.claude/skills/event-modeling/SKILL.md` |
| **Tests:** `embabel-codegen` is **not** in the root Maven reactor; run `cd embabel-codegen && ../mvnw test` (do **not** use `./mvnw test -pl embabel-codegen` from repo root). | `embabel-codegen/README.md`, `make codegen-build` |

---

## Scope (v1)

**Done:** `EventModel` + `deployment` / `DeploymentSpec` / `KedaSpec` parsing (covered by existing `EventModelParsingTest` and related tests; no separate v1 work).

**In (remaining):** topology mapper, standalone `k8s` CLI + MCP tool, K8s YAML generation,
module-level KEDA ScaledObjects, unit tests, Makefile `k8s` target, skill update as needed.

**Out (v2+):** per-processor include/exclude config, per-view/per-automation deployments,
sidecars, kustomize overlays, Vault agent, HPA for command-api, TriggerAuthentication.

---

## Design Decisions

| Issue | Decision |
|---|---|
| Per-view/per-automation deployments not enforceable | **One singleton worker per module**, not per processor |
| Monolith + KEDA scale-to-zero kills command API | Monolith mode forces `kedaMinReplicas >= 1`; scale-to-zero (`minReplicas: 0`) is **distributed-worker-only** |
| SQL COALESCE bug (scalar subquery returning NULL) | Use `COALESCE((SELECT last_position FROM ... WHERE ...), 0)` |
| `commandReplicas` vs HPA semantics confusion | **Skip HPA in v1**; generate fixed `replicas: commandReplicas` on command-api Deployment |
| Makefile variable | `$(CRABLET_CODEGEN_JAR)` |
| `appName` sanitization | DNS-1123: lowercase, replace non-`[a-z0-9]` with `-`, collapse runs, trim edges, cap at 63 chars |
| KEDA authentication | `connectionFromEnv: KEDA_DATABASE_URL` on each trigger; no `TriggerAuthentication` in v1 |
| Multiple ScaledObjects on same Deployment (monolith) | One ScaledObject with multiple triggers to avoid KEDA "last writer wins" |
| Jackson cannot emit YAML comments | Write `secret-template.yaml` from a static string constant, not `ObjectNode` |
| PDB scope | `distributed && hasModule && kedaMinReplicas >= 1` only |

---

## Documentation strategy (keep deployment topology)

**Do not replace** [`docs/user/DEPLOYMENT_TOPOLOGY.md`](../../user/DEPLOYMENT_TOPOLOGY.md) with Kubernetes-only content.

| Doc | Role |
|-----|------|
| `DEPLOYMENT_TOPOLOGY.md` | **Platform-agnostic** rules: command-only vs poller-backed, singleton workers, why extra replicas are not more throughput, adoption ordering. Serves every runtime (Compose, VMs, ECS, K8s, etc.). |
| Generated `k8s/base/README-k8s.md` (and `event-model` `deployment:` block) | **One opinionated K8s encoding** of those rules (env vars, KEDA, monolith vs distributed). |

**After this feature ships:**

1. Add a **Kubernetes** subsection to `DEPLOYMENT_TOPOLOGY.md` (or a short `docs/user/DEPLOYMENT_KUBERNETES.md` linked from the topology doc) that states: *the topology rules above are what the manifests implement*; link to the template/app path for `make k8s` and to `README-k8s.md` in generated output.
2. **Avoid duplicating** long KEDA install / secret-format tables in `DEPLOYMENT_TOPOLOGY.md` — point to `README-k8s.md` for operational detail; keep the topology page conceptual.
3. **Do not** remove or relink the many existing references to `DEPLOYMENT_TOPOLOGY.md` across tutorials and READMEs unless a dedicated redirect page is required (not recommended).
4. **After [`README_ROOT_REFRESH.md`](README_ROOT_REFRESH.md) lands:** add **`docs/user/MODULES.md`** to the story — generated K8s / topology should be **discoverable** from the module reference page (e.g. a short “Kubernetes” pointer to `make k8s` + `k8s/base/README-k8s.md` and link back to `DEPLOYMENT_TOPOLOGY.md` for the conceptual rules). No long duplication; link-centric.

---

## Step 1 — K8sTopology mapper

**New package:** `embabel-codegen/src/main/java/com/crablet/codegen/k8s/`

**`K8sTopology.java`** — pure record, no I/O:

```java
record K8sTopology(
    String appName,               // DNS-1123 from domain()
    boolean distributed,
    int commandReplicas,          // fixed replicas on command-api (or monolith if command-only)
    boolean kedaEnabled,
    int kedaMinReplicas,          // always >= 1 when !distributed (enforced in factory)
    int kedaPollingInterval,
    boolean hasViews,
    boolean hasAutomations,
    boolean hasOutbox,
    List<String> viewEventTypes,          // union of all view.reads
    List<String> automationEventTypes,    // union of all automation.triggeredBy
    List<OutboxWorker> outboxWorkers      // one entry per OutboxSpec
)

record OutboxWorker(String topic, String publisher, List<String> handles)
// publisher = OutboxSpec.name(), topic = OutboxSpec.topic()
```

Static factory `K8sTopology.from(EventModel model)`:
- `appName` = `Dns1123.sanitize(model.domain())`
- `distributed` = `model.deployment().isDistributed()`
- `commandReplicas`:
  - monolith with any poller module → `1` (leader election constraint)
  - monolith command-only → `model.deployment().commandReplicas()`
  - distributed → `model.deployment().commandReplicas()`
- `kedaMinReplicas` = `distributed ? spec.keda().minReplicas() : Math.max(1, spec.keda().minReplicas())`
- `viewEventTypes` = union of all `view.reads()` across all views (deduped, sorted)
- `automationEventTypes` = union of all `automation.triggeredBy()` across all automations (deduped)
- `outboxWorkers` = one `OutboxWorker` per `OutboxSpec`

**`Dns1123.java`** — static helper:
```java
static String sanitize(String raw)
// lowercase → replace [^a-z0-9] with - → collapse consecutive - → trim leading/trailing -
// → truncate to 63 → trim again (truncation may expose a trailing -) → fallback to "app" if empty
```

**`ViewNameResolver.java`** — static helper for codegen/prompt consistency:
```java
static String viewName(String specName)
// CamelCase → kebab-case: WalletBalanceView → wallet-balance-view
// Used in ViewsAgent prompt: "getViewName() must return <ViewNameResolver.viewName(spec.name())>"
```

**Implementation contract:** When implementing, wire **`ViewsAgent`** (or whatever generates view projectors) so the **LLM prompt actually uses the same `ViewNameResolver` rule** for `getViewName()`. If the prompt drifts, `view_progress.view_name` in the DB and KEDA SQL that reference view backlog will not line up. K8s `viewEventTypes` are not enough on their own — **view identity strings must be consistent end-to-end.**

---

## Step 2 — K8s YAML generator

**`K8sGenerator.java`** — uses `ObjectMapper(YAMLFactory)` (already in classpath) for structured
YAML; `secret-template.yaml` written from a static string constant.

Method: `void generate(K8sTopology t, Path outputDir)` → writes to `outputDir/k8s/base/`

### Files generated

| File | Condition |
|---|---|
| `namespace.yaml` | always |
| `deployment-api.yaml` | always; distributed mode adds `CRABLET_*_ENABLED=false` + Spring/KEDA DB env vars |
| `service-api.yaml` | always |
| `deployment-views-worker.yaml` | `hasViews && distributed` |
| `scaled-object-views-worker.yaml` | `distributed && hasViews && kedaEnabled` |
| `deployment-automations-worker.yaml` | `hasAutomations && distributed` |
| `scaled-object-automations-worker.yaml` | `distributed && hasAutomations && kedaEnabled` |
| `deployment-outbox-worker.yaml` | `hasOutbox && distributed` |
| `scaled-object-outbox-worker.yaml` | `distributed && hasOutbox && kedaEnabled` |
| `scaled-object-monolith.yaml` | `!distributed && kedaEnabled && (hasViews || hasAutomations || hasOutbox)` — one ScaledObject, all triggers |
| `secret-template.yaml` | always; static string constant; placeholder Spring datasource keys + KEDA connection string key |
| `pdb-views-worker.yaml` | `distributed && hasViews && kedaMinReplicas >= 1` |
| `pdb-automations-worker.yaml` | `distributed && hasAutomations && kedaMinReplicas >= 1` |
| `pdb-outbox-worker.yaml` | `distributed && hasOutbox && kedaMinReplicas >= 1` |
| `kustomization.yaml` | always; lists every generated resource |
| `README-k8s.md` | always; KEDA install, DB secret fill-in, monolith vs distributed, empty-table gotcha |

### KEDA queries — module-level, fixed COALESCE pattern

All queries return a single integer. Every poller ScaledObject sets:
```yaml
spec:
  minReplicaCount: <kedaMinReplicas>   # 0 for scale-to-zero (distributed only); >= 1 enforced for monolith
  maxReplicaCount: 1                   # singleton constraint — critical; KEDA default can exceed 1
  pollingInterval: <kedaPollingInterval>
```

Each trigger includes:
```yaml
connectionFromEnv: KEDA_DATABASE_URL   # see Secret section below
targetQueryValue: "1"
activationTargetQueryValue: "0"
```

**Views trigger:**
```sql
SELECT COUNT(*) FROM events
WHERE position > COALESCE((SELECT MIN(last_position) FROM view_progress), 0)
AND type = ANY(ARRAY['EventA', 'EventB'])
```

**Automations trigger:**
```sql
SELECT COUNT(*) FROM events
WHERE position > COALESCE((SELECT MIN(last_position) FROM automation_progress), 0)
AND type = ANY(ARRAY['EventX'])
```

**Outbox trigger** (one per `OutboxWorker`, in single ScaledObject):
```sql
SELECT COUNT(*) FROM events
WHERE position > COALESCE(
  (SELECT last_position FROM outbox_topic_progress WHERE topic = '<topic>' AND publisher = '<publisher>'),
  0)
AND type = ANY(ARRAY['EventA', 'EventB'])
```

### Secret and env vars — Spring vs KEDA use different formats

Spring Boot reads `jdbc:postgresql://...` via `SPRING_DATASOURCE_*`.
KEDA's PostgreSQL `connectionFromEnv` expects libpq key=value format
(`host=... user=... password=... dbname=... port=...`). They cannot share one env var.

**Generated `secret-template.yaml`** (written as static string constant in `K8sGenerator` — not `ObjectNode`):
```yaml
apiVersion: v1
kind: Secret
metadata:
  name: {appName}-db-secret
  namespace: {appName}
type: Opaque
stringData:
  spring-datasource-url: "jdbc:postgresql://CHANGE_ME:5432/CHANGE_ME"
  datasource-username: "CHANGE_ME"
  datasource-password: "CHANGE_ME"
  keda-connection-string: "host=CHANGE_ME port=5432 user=CHANGE_ME password=CHANGE_ME dbname=CHANGE_ME sslmode=require"
```

**Env vars on every Deployment:**
```yaml
- name: SPRING_DATASOURCE_URL
  valueFrom: {secretKeyRef: {name: {appName}-db-secret, key: spring-datasource-url}}
- name: SPRING_DATASOURCE_USERNAME
  valueFrom: {secretKeyRef: {name: {appName}-db-secret, key: datasource-username}}
- name: SPRING_DATASOURCE_PASSWORD
  valueFrom: {secretKeyRef: {name: {appName}-db-secret, key: datasource-password}}
- name: KEDA_DATABASE_URL
  valueFrom: {secretKeyRef: {name: {appName}-db-secret, key: keda-connection-string}}
```

`KEDA_DATABASE_URL` is present on worker/monolith pods so `connectionFromEnv: KEDA_DATABASE_URL`
resolves at KEDA query time. No `TriggerAuthentication` in v1.

**Pre-implementation check:** verify `templates/crablet-app/src/main/resources/application.yml`
binds `SPRING_DATASOURCE_*` env vars (Spring Boot default `spring.datasource.*` mapping covers this).

### Spring module-isolation env vars for distributed deployments

| Deployment | Module env vars (in addition to DB env vars above) |
|---|---|
| `command-api` | `CRABLET_VIEWS_ENABLED=false`, `CRABLET_AUTOMATIONS_ENABLED=false`, `CRABLET_OUTBOX_ENABLED=false` |
| `views-worker` | `CRABLET_VIEWS_ENABLED=true`, `CRABLET_AUTOMATIONS_ENABLED=false`, `CRABLET_OUTBOX_ENABLED=false` |
| `automations-worker` | `CRABLET_VIEWS_ENABLED=false`, `CRABLET_AUTOMATIONS_ENABLED=true`, `CRABLET_OUTBOX_ENABLED=false` |
| `outbox-worker` | `CRABLET_VIEWS_ENABLED=false`, `CRABLET_AUTOMATIONS_ENABLED=false`, `CRABLET_OUTBOX_ENABLED=true` |

---

## Step 3 — CLI + MCP integration

### `CodegenCommand.java`
Add `case "k8s" -> runK8s(parseFlags(args, 1));`.
Update `printHelp()` to document `k8s --model --output` with same flag style as `generate`.

### `McpServer.java`
Add `embabel_k8s` tool to `toolsListResult()`.
Add `case "embabel_k8s"` branch to `toolCallResult()`.

### `templates/crablet-app/Makefile`
```makefile
k8s: ## Generate Kubernetes manifests from event-model.yaml
	@java -jar $(CRABLET_CODEGEN_JAR) k8s --model event-model.yaml --output .
```
Add `k8s` to `.PHONY` and `help` output.

---

## Step 4 — Skill update

**File: `.claude/skills/event-modeling/SKILL.md`**

Update YAML template `deployment:` block:
```yaml
deployment:
  topology: monolith        # monolith | distributed
  # commandReplicas: 2      # distributed only — fixed replicas on command-api
  keda:
    enabled: false          # true → KEDA ScaledObjects for poller-backed workers
    minReplicas: 0          # distributed only; monolith forces >= 1 (command-api must stay up)
    pollingInterval: 30     # seconds between KEDA PostgreSQL checks
```

Also capture in `README-k8s.md` / skill (operator notes), not only inline YAML comments:
- KEDA install: `helm install keda kedacore/keda --namespace keda --create-namespace`
- `minReplicas: 0` (scale-to-zero) is only effective in `distributed` topology; monolith ignores it and uses `1`
- PDB is omitted when `minReplicas = 0`
- `connectionFromEnv: KEDA_DATABASE_URL` is used for KEDA auth; the generated Secret must be filled in before deploy

---

## Step 5 — Tests (3 new classes under `k8s/` + existing model tests)

**`embabel-codegen/src/test/java/com/crablet/codegen/k8s/`**

1. **Deployment / model parsing** — keep **`EventModelParsingTest`** (and related) **green**; do not add a separate `DeploymentSpecParsingTest` unless you need cases not already covered.
2. **`K8sTopologyTest`** — unit-test `K8sTopology.from()`:
   - command-only → `hasViews=false`, `hasAutomations=false`, `hasOutbox=false`
   - monolith with views → `commandReplicas=1` (overridden), `kedaMinReplicas >= 1`
   - distributed + `keda.minReplicas=0` → `kedaMinReplicas=0`
   - monolith + `keda.minReplicas=0` → `kedaMinReplicas=1` (enforced)
   - `viewEventTypes` = correct union, no duplicates
3. **`Dns1123Test`** — edge cases: spaces, uppercase, leading/trailing hyphens, length > 63, empty-after-sanitize fallback to `app`.
4. **`K8sGeneratorSnapshotTest`** — generate into temp dir; assert:
   - all expected files exist for distributed + KEDA model
   - `scaled-object-views-worker.yaml` contains `view_progress`, correct event type array, `connectionFromEnv: KEDA_DATABASE_URL`, `targetQueryValue: "1"`, `activationTargetQueryValue: "0"`
   - every poller ScaledObject contains `minReplicaCount: <kedaMinReplicas>` and `maxReplicaCount: 1`
   - `scaled-object-outbox-worker.yaml` has one trigger per `OutboxSpec` with correct `outbox_topic_progress` WHERE clause
   - `deployment-api.yaml` has all three `CRABLET_*_ENABLED=false` plus `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, and `KEDA_DATABASE_URL` env vars in distributed mode
   - no PDB files when `keda.minReplicas=0`
   - no PDB files in monolith mode
   - no `trigger-auth.yaml` generated
   - `secret-template.yaml` contains `spring-datasource-url`, `datasource-username`, `datasource-password`, and `keda-connection-string` keys
   - monolith model generates no worker deployments and no worker ScaledObjects
   - monolith + KEDA + poller modules generates exactly one `scaled-object-monolith.yaml`
   - `kustomization.yaml` lists exactly the generated files

---

## Verification

1. `cd embabel-codegen && ../mvnw test` — all new test classes in `embabel-codegen` pass, no regressions (`embabel-codegen` is not in the root reactor; do not use `./mvnw test -pl embabel-codegen` from root)
2. `make k8s` in `templates/crablet-app/` against template `event-model.yaml` — writes `k8s/base/`
3. `kubectl kustomize k8s/base/` — validates YAML structure (no KEDA CRDs needed)
4. `embabel_generate`, `embabel_plan`, `embabel_init` still work unchanged
5. **Docs:** `DEPLOYMENT_TOPOLOGY.md` updated per **Documentation strategy** above (K8s subsection or new page + cross-links; topology doc stays conceptual)
