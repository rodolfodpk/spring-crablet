---
markmap:
  initialExpandLevel: -1
---

# Spring Crablet

## Core Concepts
### Event Sourcing
#### Immutable event log
#### State from projections
### CQRS
#### Command Query Responsibility Segregation
#### Separate write model from read model
#### Write side — validates intent and records facts
#### Read side — projects facts into query models
#### Queries do not mutate write state
#### Read models may be eventually consistent
### DCB Pattern
#### Dynamic Consistency Boundary — criteria-based, not aggregate-based
#### Optimistic concurrency — compare-and-append against a captured position in the decision event stream (no distributed locks)
#### Append precondition — atomic check-and-append in one transaction
#### Consistency-boundary query — defines which events constrain the decision
#### Multi-entity consistency — one check spans multiple entity streams
#### Boundary conflict — append rejected, retry with a fresh projection
#### ref: dcb.events/specification (official spec by Sara Pellegrini & Milan Savić)
### Vertical Slice Architecture
#### Complements event modeling slices at the code organization level
#### Feature-first over layer-first — each slice owns its full stack
#### Thin vertical beats fat horizontal layer
#### Coupling rule — minimize between slices, maximize cohesion within
#### Align board slices with code — same vertical cut from model to implementation
#### ref: jimmybogard.com/vertical-slice-architecture
### Event Modeling
#### Blueprint — horizontal timeline of the entire system
#### Read left to right — time flows, events are the spine
#### Subsystem lanes — one per bounded area (e.g. inventory, auth, payment, gps)
#### Semantic rows — wireframes / commands / events / read models stacked vertically inside lanes
#### Event model vs diagram artifacts — core model uses rows by default; subsystem lanes live in optional diagram sidecars
#### Slices — atomic unit of work, vertical cut through all layers
##### State Change — trigger → command → event (write path)
##### State View — event → read model (query path)
##### Automation — event → command (policy/reaction)
##### Translation — event → external system (or external → command)
##### ref: eventmodeling.org/posts/event-modeling-cheatsheet
#### Building Blocks
##### Trigger — what starts the flow (UI wireframe, schedule, external system)
##### Command — intent to change state (blue)
##### Event — committed business fact, immutable (orange)
##### View — async read model built from events (green)
##### Automation — policy: if event X then emit command Y (amber)
##### Translation — external systems, webhooks, outbox, messaging (pink)
#### Design Rules
##### One fact per event — events describe what happened, not what to do
##### Commands validate — events never fail
##### Views are eventual — updated asynchronously after commit
##### Automations are policies — no direct writes, only command emission
#### Workshop Steps
##### 1. Brainstorm events — what happened in the domain? (orange stickies)
##### 2. Add commands — what caused each event? (blue stickies)
##### 3. Add triggers — who or what issues each command? (white stickies)
##### 4. Add views — what reads the events? (green stickies)
##### 5. Add automations — what policies fire after events? (amber stickies)
##### 6. Add translations — what crosses system boundaries? (pink stickies)
##### 7. Draw slices — group trigger+command+event+view into vertical features
##### 8. Define boundaries — tag decision scopes, name consistency scopes

## Crablet Architecture Model
### Grounded in Core Concepts
#### Event Sourcing — immutable log; projections on command path and in async read models
#### CQRS — write path vs materialized read paths below
#### DCB Pattern — criteria boundary; see consistency boundary + Crablet DCB interpretation
#### Event Modeling — triggers, commands, events, views, automations, translations map to paths below
#### Vertical Slice Architecture — CommandHandler + domain events + ViewProjector per feature slice
### Write path — CommandExecutor + CommandHandler + EventStore
### Decision projection — command handlers project state from events inside the write transaction
### Consistency boundary — DecisionModel + AppendCondition + StreamPosition
### Read path — ViewProjector + async poller + materialized views
### Automation path — AutomationHandler reacts to committed events and emits commands
### Translation path — outbox publishes committed events to external systems
### Event spine — immutable events connect commands, views, automations, and outbox
### Slice shape — trigger + command + event + view
### Crablet append taxonomy — idempotent / commutative / commutative + guard / non-commutative
### Postgres advisory locks — idempotent append semantics + session-scoped poller leader election
### NOTIFY + optional LISTEN — wake async processors after writes (Postgres; LISTEN needs a direct JDBC URL, not a pooler)
### Crablet DCB interpretation — inspired by DCB, not strict spec vocabulary

## Framework Modules
### crablet-eventstore
#### EventStore interface
#### appendIdempotent / appendCommutative / appendNonCommutative
#### StateProjector — project state from events
#### StreamPosition — optimistic concurrency token
### crablet-commands
#### CommandHandler interface
#### CommandExecutor — discovers handlers, runs transactions
#### CommandDecision — idempotent / commutative / nonCommutative
### crablet-commands-web
#### Generic HTTP POST /api/commands
#### CommandApiExposedCommands — allowlist
#### Optional Swagger / OpenAPI integration
### crablet-views
#### ViewProjector — async materialized views
#### ViewSubscription — event selection + poller config
#### AbstractTypedViewProjector — sealed interface pattern matching
### crablet-outbox
#### OutboxPublisher — reliable external event delivery
#### Transactional outbox pattern
#### Per-(topic, publisher) processor
### crablet-automations
#### AutomationHandler — event-driven command emission
#### AutomationDecision — decide what command to emit
#### Leader election per processor
### crablet-event-poller
#### EventProcessor — shared polling infrastructure
#### Leader election via PostgreSQL advisory locks
#### Shared-fetch mode — one query per cycle
#### LISTEN/NOTIFY wakeup (opt-in)

## Examples
### Wallet (single-entity)
#### OpenWallet → WalletOpened (idempotent)
#### Deposit → DepositMade (commutative + guard)
#### Withdraw → WithdrawalMade (non-commutative)
#### TransferMoney → MoneyTransferred (non-commutative)
#### WalletBalance view
#### WalletOpenedAutomation → SendWelcomeNotification
### Course Enrollment (multi-entity DCB)
#### DefineCourse → CourseDefined (idempotent)
#### RegisterStudent → StudentRegistered (idempotent)
#### ChangeCourseCapacity → CourseCapacityChanged (non-commutative)
#### SubscribeStudentToCourse → StudentSubscribedToCourse
#### Multi-entity: course_id + student_id tag boundary
#### CourseAvailability view

## AI Tooling
### embabel-codegen
#### CLI — init / plan / generate
#### MCP server — exposes embabel_init, embabel_plan, embabel_generate
#### Agent pipeline — events → commands → views → automations → outbox
#### Auto-repair on compile errors
#### Error recovery loop — compile, diagnose, patch, retry
### event-model.yaml format
#### Official codegen input contract
#### Clean spec — events, commands, views, automations, outbox
#### Diagram sidecar — triggers, subsystem lanes, assignments, synthetic nodes, eventBadges
#### Shared schemas ($ref composition)
### Starter template
#### templates/crablet-app — pre-wired pom.xml + Flyway migration
#### event-model.yaml skeleton
#### Makefile — plan / generate / verify / check
#### .claude/settings.json — MCP server auto-wired
### AI Workflows
#### AI-first workflow — event model → full slice generation
#### Feature slice workflow — one vertical slice at a time
#### Event modeling workshop — /event-modeling skill session
### Claude Code Skills
#### /event-modeling — structured workshop, produces event-model.yaml
#### /dcb — choose AppendCondition, diagnose ConcurrencyException
#### /balanced-coupling — modularity review and coupling analysis
#### /review — pull request review
#### /jspecify — add null-safety annotations

## Operations
### PostgreSQL 17+
#### MVCC for concurrency
#### GIN indexes on event tags
#### Advisory locks for leader election
#### pg_notify for LISTEN/NOTIFY wakeup
### Performance
#### Read/write DataSource separation
#### Closing the books (period segmentation)
#### Shared-fetch mode for many processors
### Observability
#### crablet-metrics-micrometer
#### Prometheus + Grafana stack
#### Per-processor metrics
### Deployment
#### Spring Boot fat JAR — standard packaging
#### Docker container — one image per app
#### Kubernetes — Deployment + Service
#### KEDA — event-driven autoscaling (optional)
#### Poller instances — prefer 1, max 2 for active/failover
#### Connection pooling — PgBouncer safe for commands/views
#### LISTEN connections — must be direct Postgres (no pooler)
