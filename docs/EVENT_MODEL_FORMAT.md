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
        type: String
      - name: customerId
        type: String
      - name: amount
        type: int
      - name: purpose
        type: String

  - name: CreditScoreChecked
    tags: [application_id]
    fields:
      - name: applicationId
        type: String
      - name: score
        type: int
      - name: provider
        type: String

  - name: LoanApplicationApproved
    tags: [application_id]
    fields:
      - name: applicationId
        type: String
      - name: approvedAmount
        type: int
      - name: approvedBy
        type: String

  - name: LoanApplicationRejected
    tags: [application_id]
    fields:
      - name: applicationId
        type: String
      - name: reason
        type: String
      - name: rejectedBy
        type: String

commands:
  - name: SubmitLoanApplication
    pattern: idempotent
    produces: [LoanApplicationSubmitted]
    fields:
      - name: applicationId
        type: String
        validation: [notNull, notBlank]
      - name: customerId
        type: String
        validation: [notNull, notBlank]
      - name: amount
        type: int
        validation: greaterThan(0)
      - name: purpose
        type: String
        validation: [notNull, notBlank]

  - name: RecordCreditScore
    pattern: commutative
    produces: [CreditScoreChecked]
    guardEvents: [LoanApplicationSubmitted]
    fields:
      - name: applicationId
        type: String
        validation: [notNull, notBlank]
      - name: score
        type: int
        validation: between(300, 850)
      - name: provider
        type: String
        validation: [notNull, notBlank]

  - name: ApproveLoanApplication
    pattern: non-commutative
    produces: [LoanApplicationApproved]
    fields:
      - name: applicationId
        type: String
        validation: [notNull, notBlank]
      - name: approvedAmount
        type: int
        validation: greaterThan(0)
      - name: approvedBy
        type: String
        validation: [notNull, notBlank]

  - name: RejectLoanApplication
    pattern: non-commutative
    produces: [LoanApplicationRejected]
    fields:
      - name: applicationId
        type: String
        validation: [notNull, notBlank]
      - name: reason
        type: String
        validation: [notNull, notBlank]
      - name: rejectedBy
        type: String
        validation: [notNull, notBlank]

views:
  - name: LoanApplicationReview
    reads: [LoanApplicationSubmitted, CreditScoreChecked, LoanApplicationApproved, LoanApplicationRejected]
    tag: application_id
    fields:
      - name: applicationId
        type: String
      - name: customerId
        type: String
      - name: amount
        type: int
      - name: creditScore
        type: int
      - name: status
        type: String

  - name: PendingAutoApprovals
    reads: [LoanApplicationSubmitted, CreditScoreChecked, LoanApplicationApproved]
    tag: application_id
    fields:
      - name: applicationId
        type: String
      - name: amount
        type: int
      - name: creditScore
        type: int
      - name: status
        type: String

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

Events are facts and do not carry validation rules. Commands represent input and should carry
validation rules.

Supported type vocabulary:

| YAML type | Java type | Validation mapping |
|---|---|---|
| `String` | `String` | string constraints |
| `int` | `int` | integer constraints |
| `long` | `long` | long constraints |
| `BigDecimal` | `BigDecimal` | decimal constraints |
| `boolean` | `boolean` | boolean constraints |
| `UUID` | `UUID` | UUID parsing or pattern constraint |
| `Instant` | `Instant` | timestamp serialization |

## Validation

Command fields support a small validation vocabulary that maps to generated Java validation code.

```yaml
fields:
  - name: customerId
    type: String
    validation: [notNull, notBlank]
  - name: amount
    type: int
    validation: greaterThan(0)
  - name: score
    type: int
    validation: between(300, 850)
```

Supported constraints:

| Constraint | Meaning |
|---|---|
| `notNull` | value must be present |
| `notBlank` | string must contain non-whitespace content |
| `greaterThan(n)` | numeric value must be greater than `n` |
| `between(min, max)` | numeric value must be between `min` and `max` |

## Shared Schemas

Use `schemas` when commands and events share the same fields. Events inherit fields as facts.
Commands inherit fields and add validation.

```yaml
schemas:
  - name: LoanApplicationData
    fields:
      - name: applicationId
        type: String
      - name: customerId
        type: String
      - name: amount
        type: int
      - name: purpose
        type: String

events:
  - name: LoanApplicationSubmitted
    tags: [application_id, customer_id]
    schema: LoanApplicationData

commands:
  - name: SubmitLoanApplication
    pattern: idempotent
    produces: [LoanApplicationSubmitted]
    schema: LoanApplicationData
    validation:
      applicationId: [notNull, notBlank]
      customerId: [notNull, notBlank]
      amount: greaterThan(0)
      purpose: [notNull, notBlank]
```

Schema references should be resolved before generation. Agents and templates should receive fully
expanded field lists.

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
- `fields` or `schema`: command input shape
- `validation`: input constraints, either per field or by inherited schema field

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
