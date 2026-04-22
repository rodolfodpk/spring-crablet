---
name: event-modeling
description: >
  Conducts a structured event-modeling workshop with the user to produce an event-model.yaml
  file for a given domain. Use when the user wants to design a new feature or application using
  the spring-crablet event sourcing framework.
argument-hint: "[domain name or description]"
allowed-tools: Read, Write, AskUserQuestion, TaskCreate, TaskUpdate
---

# Event Modeling Workshop

You conduct a structured dialogue with the user to produce a complete `event-model.yaml` for a
domain. The goal is an explicit, generator-ready model — rich enough that `embabel-codegen` can
produce structurally complete Java code without guessing.

## Input

If `$ARGUMENTS` contains a domain name or description, use it as the starting context.
Otherwise use `AskUserQuestion` to ask: "What domain or feature would you like to model?"

## Workshop Steps

Use TaskCreate to track these steps: Discover Events, Identify Commands, Design Views,
Add Automations, Add Outbox, Review and Write.

### 1. Discover Events

Ask the user to describe what happens in the domain — things that occurred in the past.
Extract named domain events. For each event, identify:
- The Java record name (PascalCase, past-tense verb phrase)
- Which tags identify the entity (snake_case: `wallet_id`, `order_id`, etc.)
- The payload fields

Use `AskUserQuestion` — one concept at a time. When the event list feels complete, confirm.

### 2. Identify Commands

For each group of events, identify the command that causes them. For each command:
- Name it (PascalCase imperative: `OpenWallet`, `SubmitOrder`)
- Choose the DCB pattern:
  - `idempotent` — entity creation, first event for an identity
  - `commutative` — order-independent (deposits, credits, analytics)
  - `non-commutative` — state-dependent, must detect conflicts (withdrawals, approvals)
- List which events it `produces`
- If commutative but entity must exist first, add `guardEvents`
- Collect input fields and their validation constraints

### 3. Design Views

Ask: which read models does this domain need? For each view:
- Name it (PascalCase)
- List which events update it (`reads`)
- Identify the routing tag
- Define the projected fields

### 4. Add Automations (optional)

Ask: are there any event-driven policies? For each automation:
- Name it (kebab-case)
- `triggeredBy` — the event that fires it
- `emitsCommand` — the command to emit when the condition passes
- `condition` — explicit expression (not vague)
- `readsView` — if the condition depends on accumulated state

### 5. Add Outbox (optional)

Ask: does any event need to be published to an external system? For each publisher:
- Name it
- `topic`
- `handles` — event types it publishes
- `adapter` — integration type (smtp, kafka, http, etc.)

### 6. Review and Write

Summarize the full model back to the user as a YAML preview. Ask for confirmation.
On confirmation, write the file to `event-model.yaml` (or the path the user specifies).

## Output Rules

**Field types** — use JSON Schema vocabulary:

| Use | For |
|---|---|
| `string` | text values |
| `integer` | whole numbers |
| `number` | decimals (maps to `BigDecimal`) |
| `boolean` | true/false |
| `long` | large integers |
| `UUID` | identifiers in UUID format |
| `Instant` | timestamps |

**Field constraints on command fields** — use JSON Schema keywords directly on the field:

| User says | Emit |
|---|---|
| "must not be blank" / "required" | `minLength: 1` |
| "at least N characters" | `minLength: N` |
| "at most N characters" | `maxLength: N` |
| "must be positive" | `exclusiveMinimum: 0` |
| "at least N" / "minimum N" | `minimum: N` |
| "at most N" / "maximum N" | `maximum: N` |
| "between N and M" | `minimum: N` + `maximum: M` |
| "strictly less than N" | `exclusiveMaximum: N` |

**Collection fields** — use `type: array` with `items` or `type: map` with `additionalProperties`:

| User says | Emit |
|---|---|
| "a list of strings" | `type: array` + `items: {type: string}` |
| "a list of UUIDs" / "a list of IDs" | `type: array` + `items: {type: UUID}` |
| "a list of integers" / "a list of numbers" | `type: array` + `items: {type: integer}` |
| "a map of string to string" / "key-value pairs" | `type: map` + `additionalProperties: {type: string}` |
| "at least N items required" | add `minItems: N` to the array/map field |
| "at most N items allowed" | add `maxItems: N` to the array/map field |

Collections with `minItems`/`maxItems` generate YAVI size constraints. Per-element constraints
are not supported in the model.

Do NOT invent constraint syntax. Do NOT use `validation:` lists. Do NOT use `notNull`, `notBlank`,
`greaterThan(n)`, or `between(n,m)` — these are the old vocabulary and will fail to parse.

Events are facts — they never carry constraint keywords. Constraints only appear on command fields.

## YAML Template

```yaml
# yaml-language-server: $schema=../../docs/examples/event-model-schema.json
domain: YourDomain
basePackage: com.example.yourdomain

schemas: []        # optional reusable field groups

events:
  - name: SomethingHappened
    tags: [entity_id]
    fields:
      - name: entityId
        type: string
      - name: value
        type: integer

commands:
  - name: DoSomething
    pattern: idempotent          # idempotent | commutative | non-commutative
    produces: [SomethingHappened]
    # guardEvents: [EntityCreated]  # commutative only, when entity must exist
    fields:
      - name: entityId
        type: string
        minLength: 1
      - name: value
        type: integer
        exclusiveMinimum: 0

views:
  - name: SomethingView
    reads: [SomethingHappened]
    tag: entity_id
    fields:
      - name: entityId
        type: string
      - name: value
        type: integer

automations: []
outbox: []
```
