# Event Model Format

The event model is the input contract for Crablet's AI-assisted app generation workflow. It should
be explicit enough for the generator to produce structurally complete code without guessing.

This format is still a preview contract while the generator matures.

## Top-Level Shape

An `event-model.yaml` describes the domain, target Java package, reusable schemas, events,
commands, views, automations, and outbox publishers.

```yaml
domain: LoanApplication
basePackage: com.example.loan

schemas: []
events: []
commands: []
views: []
automations: []
outbox: []
```

Only `domain`, `basePackage`, `events`, and `commands` are required for the smallest write-side
application. Views, automations, and outbox publishers are added when the model needs generated
read models or event-driven integrations.

## Complete Example

```yaml
domain: LoanApplication
basePackage: com.example.loan

events:
  - name: LoanApplicationSubmitted
    tags: [application_id, customer_id]
    fields:
      - name: applicationId
        type: string
      - name: customerId
        type: string
      - name: amount
        type: integer
      - name: purpose
        type: string

  - name: CreditScoreChecked
    tags: [application_id]
    fields:
      - name: applicationId
        type: string
      - name: score
        type: integer
      - name: provider
        type: string

  - name: LoanApplicationApproved
    tags: [application_id]
    fields:
      - name: applicationId
        type: string
      - name: approvedAmount
        type: integer
      - name: approvedBy
        type: string

  - name: LoanApplicationRejected
    tags: [application_id]
    fields:
      - name: applicationId
        type: string
      - name: reason
        type: string
      - name: rejectedBy
        type: string

commands:
  - name: SubmitLoanApplication
    pattern: idempotent
    produces: [LoanApplicationSubmitted]
    fields:
      - name: applicationId
        type: string
        minLength: 1
      - name: customerId
        type: string
        minLength: 1
      - name: amount
        type: integer
        exclusiveMinimum: 0
      - name: purpose
        type: string
        minLength: 1

  - name: RecordCreditScore
    pattern: commutative
    produces: [CreditScoreChecked]
    guardEvents: [LoanApplicationSubmitted]
    fields:
      - name: applicationId
        type: string
        minLength: 1
      - name: score
        type: integer
        minimum: 300
        maximum: 850
      - name: provider
        type: string
        minLength: 1

  - name: ApproveLoanApplication
    pattern: non-commutative
    produces: [LoanApplicationApproved]
    fields:
      - name: applicationId
        type: string
        minLength: 1
      - name: approvedAmount
        type: integer
        exclusiveMinimum: 0
      - name: approvedBy
        type: string
        minLength: 1

  - name: RejectLoanApplication
    pattern: non-commutative
    produces: [LoanApplicationRejected]
    fields:
      - name: applicationId
        type: string
        minLength: 1
      - name: reason
        type: string
        minLength: 1
      - name: rejectedBy
        type: string
        minLength: 1

views:
  - name: LoanApplicationReview
    reads: [LoanApplicationSubmitted, CreditScoreChecked, LoanApplicationApproved, LoanApplicationRejected]
    tag: application_id
    fields:
      - name: applicationId
        type: string
      - name: customerId
        type: string
      - name: amount
        type: integer
      - name: creditScore
        type: integer
      - name: status
        type: string

  - name: PendingAutoApprovals
    reads: [LoanApplicationSubmitted, CreditScoreChecked, LoanApplicationApproved]
    tag: application_id
    fields:
      - name: applicationId
        type: string
      - name: amount
        type: integer
      - name: creditScore
        type: integer
      - name: status
        type: string

automations:
  - name: AutoApproveSmallLoans
    triggeredBy: CreditScoreChecked
    emitsCommand: ApproveLoanApplication
    pattern: todo-list
    condition: "amount <= 5000 AND creditScore >= 700"
    readsView: PendingAutoApprovals

outbox:
  - name: EmailNotificationPublisher
    topic: loan-notifications
    handles: [LoanApplicationApproved, LoanApplicationRejected]
    adapter: smtp
```

## Fields And Types

Fields carry explicit types. The generator should reject unknown types instead of guessing.

Events are facts and do not carry validation constraints. Commands represent input and should carry
constraint keywords on fields that need them.

Type vocabulary follows JSON Schema conventions:

| YAML type | Java type |
|---|---|
| `string` | `String` |
| `integer` | `int` |
| `number` | `java.math.BigDecimal` |
| `boolean` | `boolean` |
| `long` | `long` |
| `UUID` | `java.util.UUID` |
| `Instant` | `java.time.Instant` |

## Validation

Command fields use JSON Schema constraint keywords directly on the field.

```yaml
fields:
  - name: customerId
    type: string
    minLength: 1
  - name: amount
    type: integer
    exclusiveMinimum: 0
  - name: score
    type: integer
    minimum: 300
    maximum: 850
  - name: description
    type: string
    minLength: 1
    maxLength: 200
```

Supported constraint keywords:

| Keyword | Applies to | Meaning |
|---|---|---|
| `minLength: 1` | `string` | must be non-blank |
| `minLength: N` | `string` | minimum character count |
| `maxLength: N` | `string` | maximum character count |
| `minimum: N` | `integer`, `number` | value ≥ N (inclusive) |
| `exclusiveMinimum: N` | `integer`, `number` | value > N (exclusive) |
| `maximum: N` | `integer`, `number` | value ≤ N (inclusive) |
| `exclusiveMaximum: N` | `integer`, `number` | value < N (exclusive) |

These keywords map directly to YAVI validator methods in generated command records.

## Collections

Use `type: array` for lists and `type: map` for string-keyed maps. Both follow JSON Schema
conventions closely.

**Array field (generates `List<T>`):**
```yaml
- name: roles
  type: array
  items:
    type: string
- name: attachmentIds
  type: array
  items:
    type: UUID
  minItems: 1
```

**Map field (generates `Map<String, V>`, keys are always string):**
```yaml
- name: metadata
  type: map
  additionalProperties:
    type: string
```

Collection size constraints:

| Keyword | Meaning |
|---|---|
| `minItems: N` | collection must have at least N elements |
| `maxItems: N` | collection must have at most N elements |

**Java types generated:**

| Field type | Java type |
|---|---|
| `array` + `items: {type: string}` | `List<String>` |
| `array` + `items: {type: integer}` | `List<Integer>` |
| `array` + `items: {type: UUID}` | `List<java.util.UUID>` |
| `map` + `additionalProperties: {type: string}` | `Map<String, String>` |

**SQL column types generated for views:**

| Field type | PostgreSQL column |
|---|---|
| `array` of `string` | `TEXT[]` |
| `array` of `integer` | `INTEGER[]` |
| `array` of `UUID` | `UUID[]` |
| `map` | `JSONB` |

Events can carry collection fields as facts. Commands can carry collection fields with `minItems`/`maxItems`
constraints. Per-element constraints are not supported in the model — express them in prose in the
automation or command handler description if needed.

## Shared Schemas

Use the top-level `schemas` block to define reusable field groups. Commands and events reference a schema by name using the `schema` key. The generator inlines the fields before sending them to the AI agents — agents always receive fully expanded field lists.

### Defining a schema

```yaml
schemas:
  - name: MoneyAmount
    fields:
      - name: amount
        type: integer
        exclusiveMinimum: 0
      - name: currency
        type: string
        minLength: 3
        maxLength: 3
```

### Referencing a schema

Set `schema: <name>` on an event or command. Fields declared directly on the event/command are **merged on top** of the schema fields — a local field with the same name overrides the schema version.

```yaml
events:
  - name: DepositMade
    tags: [wallet_id, deposit_id]
    schema: MoneyAmount          # pulls in amount + currency
    fields:
      - name: depositId          # local fields added on top
        type: string
      - name: walletId
        type: string

commands:
  - name: Deposit
    pattern: commutative
    produces: [DepositMade]
    guardEvents: [WalletOpened]
    schema: MoneyAmount          # pulls in amount + currency
    fields:
      - name: depositId
        type: string
        minLength: 1             # adds validation not in the schema
      - name: walletId
        type: string
        minLength: 1
      - name: amount             # overrides the schema's amount to tighten the constraint
        type: integer
        exclusiveMinimum: 0
        maximum: 1000000
```

### Merge rules

- Schema fields come first in field order.
- A local field with the same `name` as a schema field replaces it entirely.
- A local field with a new name is appended after the schema fields.
- Only one `schema` reference per event or command (no multi-inheritance).
- `schema` and `fields` can coexist; omitting `fields` is valid when the schema covers everything.

### When to use schemas

- Two or more commands produce events that share the same payload shape.
- A command and its produced event share most fields (common in idempotent creation patterns).
- You want to enforce a common field vocabulary (e.g. all money fields use `amount`+`currency`).

Schemas are a codegen-time concept. They do not affect the runtime event store, command handlers, or DCB pattern — they only control what field lists the AI agents see when generating code.

## Events

Each event needs:

- `name`: Java record name
- `tags`: event-store tags used for query and consistency boundaries
- `fields` or `schema`: event payload shape

Events should describe facts that happened. They should not contain validation rules or imperative
behavior.

## Commands

Each command needs:

- `name`: Java command record name
- `pattern`: DCB command pattern
- `produces`: event types the command can append
- `fields` or `schema`: command input shape with JSON Schema constraint keywords

Supported command patterns:

| Pattern | Use |
|---|---|
| `idempotent` | entity creation and duplicate prevention |
| `commutative` | order-independent operations |
| `non-commutative` | state-dependent operations that must detect conflicts |

`guardEvents` declares lifecycle preconditions for any command pattern. The command `pattern`
still selects the handler/append strategy; guards describe facts the decision model must read
before appending.

- `commutative` with `guardEvents` uses a lifecycle guard such as `CommutativeGuarded`.
- `non-commutative` with `guardEvents` remains non-commutative; its decision model/projector must
  validate those lifecycle facts before returning `CommandDecision.NonCommutative`.
- `idempotent` with `guardEvents` is reserved for child/resource creation that depends on an
  existing parent.

When a command references the same lifecycle entity more than once, one guard event type can imply
multiple required instances. For example, `TransferMoney` can use `guardEvents: [WalletOpened]`
while validating both `fromWalletId` and `toWalletId`.

## Views

Views describe materialized read models.

Each view needs:

- `name`: generated projector/read-model name
- `reads`: event types that update the view
- `tag`: tag used to route or query the view row
- `fields`: projected table shape

The generator should create both the projector and the database migration for the view table.

Generated event hierarchies use sealed event roots by default. This is intentional for
multi-aggregate models: adding a lifecycle event should make business code decide whether the new
fact matters.

Decision projectors should rely on sealed-event exhaustiveness when every new event may affect
correctness. Read-model projectors often read only a subset of the domain event family; when they
switch over the sealed event root, they must explicitly ignore irrelevant variants instead of
removing sealed typing. Infrastructure-style consumers may route by event type strings when they
are intentionally non-exhaustive.

## Automations

Automations describe event-driven policies that may emit commands.

Each automation needs:

- `name`: generated automation handler name
- `triggeredBy`: event type that starts the automation decision
- `emitsCommand`: command emitted when the condition passes
- `pattern`: processing pattern
- `condition`: expression evaluated against the trigger event and optional view row
- `readsView`: view used when the condition depends on accumulated state

Conditions must be explicit. The generator should translate them into Java condition checks, not
invent policy.

## Outbox

Outbox entries describe integration publishers.

Each publisher needs:

- `name`: generated publisher name
- `topic`: outbox topic
- `handles`: event types the publisher can publish
- `adapter`: application integration delegate to call

The generated publisher should handle Crablet outbox mechanics. Credentials, endpoints, and
external-system client configuration belong in application configuration.

## Diagram

The optional top-level `diagram:` key carries renderer and tooling metadata. **Java codegen ignores
it entirely** — it does not affect code generation, artifact planning, or Kubernetes manifest
generation.

```yaml
diagram:
  actors:            # human actor bands (canonical Event Modeling board); order = top-to-bottom
    - id: customer
      label: Customer
  lanes:           # vertical subsystem column groups (optional; >= 2 enables group headers)
    - id: wallet
      label: Wallet
    - id: notification
      label: Notification
  assignments:     # blueprint-with-actors: views, outbox, synthetics only (ignore command keys)
    WalletBalance: wallet
    SendWelcomeNotification: notification
  triggers:        # optional actor id → diagram.actors[].id; omit actor for an "External" row
    - name: "Customer opens wallet"
      linkedCommand: OpenWallet
      actor: customer
  syntheticCommands:  # diagram-only command cards for cross-domain automation targets
    - name: SendWelcomeNotification
      displayLabel: "SendWelcomeNotification"
      note: "notification subdomain"
  eventBadges:     # badge text shown on event cards
    WalletOpened: "lifecycle"
  automations:     # diagram-only automation rows not in the structural automations list
    - name: WalletOpenedAutomation
      triggeredBy: WalletOpened
      emitsCommand: SendWelcomeNotification
```

### Docs renderer layout modes

- **Flat** — no `diagram.actors` and no `diagram.lanes`: single vertical stack of rows.
- **Horizontal swim-stack** — `diagram.lanes` without `diagram.actors`: each lane is a horizontal band (legacy column partition).
- **Canonical blueprint** — `diagram.actors` present: actor/processor bands above, one shared event timeline, `lanes` apply only to the **bottom** section (views, outbox, synthetics). Structural commands render in actor bands; do not assign them in `assignments`.

### Diagram projection (single source)

The HTML/SVG docs renderer **does not** require a second, hand-authored diagram graph. It **projects** an Event Modeling-style board from **`events`**, **`commands`**, **`views`**, **`automations`**, **`outbox`**, plus optional **`diagram.*`** and sidecar overlays (**triggers**, badges, **syntheticCommands**, diagram-only **automations**). **Java codegen ignores `diagram`**; keep structural lists authoritative.

**Horizontal timeline (left → right):** Event column order is **derived in the renderer** using a small causal graph: `guardEvents → produces`, order within `produces`, and **`automation.triggeredBy` → first event emitted by a structural `emitsCommand`**. A topological sort chooses columns; ties break by **`events[]` YAML order**. If the graph cycles or is inconsistent, the renderer falls back to **`events[]` order** and logs a warning.

**Publication edges (canonical boards):** Dashed `publication → …` connectors from events listed in **`outbox[].handles`** are **hidden by default** (they are integration notation, not core Event Modeling flow). Set **`diagram.display.publicationEdges: true`** (merged to renderer `display`) to draw them.

**Outbox and automations:** You do **not** need a `sync` / `async` flag on those entries for the model contract. Outbox means **durable publication after commit**; automations mean **reactions when the event processor sees the trigger event**. Call out user-visible latency (e.g. “caller blocks until X”) in prose, **API design**, or **`note`** fields—not as a generic sync switch in v1.

**Deferred:** Optional hand-authored layout (`diagram.canvas` with explicit element coordinates) is **out of scope for v1**.

### v1 Fields

| Key | Type | Purpose |
|-----|------|---------|
| `actors` | array of `{id, label}` | Human actors for the canonical board; `triggers[].actor` references `id`. |
| `lanes` | array of `{id, label}` | Subsystem groupings. With **actors**, these label only the bottom section; without actors, they drive horizontal swim-stack columns. |
| `assignments` | map of name → lane id | Element → lane. In blueprint-with-actors mode, use for views, outbox, synthetics (not structural commands). |
| `triggers` | array of `{name, linkedCommand, actor?}` | Actor cards; optional `actor` matches `diagram.actors[].id` (omit → External row). |
| `syntheticCommands` | array of `{name, displayLabel?, note?}` | Visual command nodes for cross-domain targets that cannot be codegen inputs. |
| `eventBadges` | map of event name → label | Badge overlaid on an event card (e.g. "multi-entity DCB"). |
| `automations` | array of `AutomationSpec` | Diagram-only automations excluded from codegen because they span bounded contexts. |

### Merge Precedence

When the renderer loads a main model plus an optional sidecar YAML:

```
core model fields < diagram.* overlay < sidecar.*
```

- `lanes`, `triggers`, `syntheticCommands`, `actors`: later defined array wins entirely (including empty `[]`).
- `assignments`, `eventBadges`: shallow merge — later keys override matching earlier keys.
- `automations`: merged by `name`; entries in `model.automations` then `diagram.automations` then
  `sidecar.automations`, with later same-name entries replacing earlier ones.

Use `diagram.lanes` and `diagram.assignments` for subsystem groupings that belong with the model.
Use a sidecar YAML for docs-specific overlays (triggers, badges, synthetic commands, diagram-only
automations) that are visual embellishments rather than structural facts.

### Docs diagram viewer manifest

Published diagrams under `docs/` are driven by [`docs/diagrams.manifest.json`](../../diagrams.manifest.json) and rendered in [`docs/event-model-viewer.html`](../../event-model-viewer.html). That page loads the manifest, then fetches each diagram’s YAML paths, calls `EventModelRenderer.mergeEventModelForDiagram(model, sidecar || {})`, and `EventModelRenderer.render` (same merge contract as described above).

**Query parameters**

- With **`?id=<diagramId>`** — show title, subtitle, optional note blocks, then the SVG diagram.
- With **no `id`** — show an index of all `diagrams[]` entries (no per-diagram notes).

**Manifest shape**

Top level:

| Field | Type | Purpose |
|-------|------|---------|
| `diagrams` | array | Registered diagrams. |

Each diagram:

| Field | Type | Purpose |
|-------|------|---------|
| `id` | string | Stable id for `?id=` (e.g. `wallet`, `course`). |
| `title` | string | Page / card heading. |
| `subtitle` | string | Short description under the title. |
| `modelPath` | string | Relative path to the main `event-model.yaml` (see path rules below). |
| `sidecarPath` | string (optional) | Relative path to diagram-only YAML merged after the model. Omit or use empty sidecar semantics as `{}`. |
| `notes` | array (optional) | Shown only when `?id=` is set, **after** the subtitle and **before** the diagram. Omit on index. |

**Note blocks** (`notes[]`)

Each note:

| Field | Type | Purpose |
|-------|------|---------|
| `kind` | string | `callout` (warning-style), `aside` (outbox-style), or `dcb-note` (blue callout). Maps to the same CSS classes used on the legacy diagram pages. |
| `parts` | array | Structured inline content — **no raw HTML**. |

Each `parts[]` entry:

| `type` | Fields | Renders as |
|--------|--------|--------------|
| `text` | `text` | Plain text. Use `\n` for line breaks; note blocks use `white-space: pre-line`. |
| `strong` | `text` | `<strong>` |
| `code` | `text` | `<code>` |
| `link` | `text`, `href` | `<a>` (`target="_blank"` for `http`/`https`) |

**Path rules** (enforced in the viewer)

- Paths must be **relative** (no `https://`, no leading `/`).
- No `..` segments.
- Must start with **`examples/`** (YAML lives under [`docs/examples/`](../../examples/)).

**Serving locally**

Open the `docs/` tree over HTTP (e.g. `python3 -m http.server` from `docs/`, or your usual static server) so `fetch` can load the manifest and YAML. File URLs alone are unreliable for `fetch`.

**Checklist after changing YAML or the manifest**

1. `event-model-viewer.html` — index without `id` lists every diagram.
2. `event-model-viewer.html?id=<id>` — diagram renders; notes appear in the right order when present.
3. Unknown `id` — clear error (unknown id), not a silent blank diagram.
4. Broken path — error names the resource (manifest, event model YAML, sidecar).
5. Legacy URLs `wallet.html` / `course.html` still reach the viewer (redirect + visible link).

## State Inference

The generator may infer command-side state projectors from `produces` relationships and event
names, but inference must stay conservative.

Example:

| Event produced by | Likely generated state |
|---|---|
| `SubmitLoanApplication` -> `LoanApplicationSubmitted` | `isExisting = true`, `status = PENDING` |
| `ApproveLoanApplication` -> `LoanApplicationApproved` | `isAlreadyDecided = true`, `status = APPROVED` |
| `RejectLoanApplication` -> `LoanApplicationRejected` | `isAlreadyDecided = true`, `status = REJECTED` |

If the transition is ambiguous, improve the model instead of relying on naming guesses.

## Generation Rule

The model should be rich enough that generated code is structurally complete. When a generator
cannot determine a field, condition, tag, command pattern, view dependency, or adapter boundary, it
should fail with a model error rather than emit placeholder code.
