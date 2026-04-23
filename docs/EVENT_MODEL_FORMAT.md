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

`guardEvents` may be used with commutative commands when an operation is order-independent but
requires lifecycle existence.

## Views

Views describe materialized read models.

Each view needs:

- `name`: generated projector/read-model name
- `reads`: event types that update the view
- `tag`: tag used to route or query the view row
- `fields`: projected table shape

The generator should create both the projector and the database migration for the view table.

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
