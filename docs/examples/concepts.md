---
markmap:
  initialExpandLevel: -1
---

# Spring Crablet

## Core Concepts
### Event Sourcing
#### Immutable event log
#### State from projections
### DCB Pattern
#### Dynamic Consistency Boundary
#### Optimistic concurrency (streamPosition)
#### No distributed locks
#### Multi-aggregate consistency — one check spans multiple entity streams
#### Decision model — query that defines the consistency boundary
#### ConcurrencyException — boundary violated, retry with fresh state
#### Traditional aggregates: one root per transaction boundary
#### DCB: boundary is defined per-operation, not per-aggregate
### Vertical Slice Architecture
#### Feature-first over layer-first organization
#### Each slice owns its request, handler, and response end-to-end
#### Thin vertical beats fat horizontal layer
#### Coupling rule — minimize between slices, maximize within
#### Contrast — horizontal layers share infrastructure, couple features
#### In Crablet — one slice = command + event + view + optional automation
#### ref: jimmybogard.com/vertical-slice-architecture
### Append Patterns
#### idempotent — entity creation, duplicate prevention
#### commutative — order-independent operations
#### commutative + guard — existence check before commit
#### non-commutative — state-dependent, detects conflicts
### Event Modeling
#### Blueprint — horizontal timeline of the entire system
#### Read left to right — time flows, events are the spine
#### Swim lanes — trigger / command / event / view / automation / translation
#### Slices — vertical cuts through all swim lanes, one per feature
##### Each slice = trigger + command + event + view end-to-end
##### Slices are the unit of delivery, not layers
##### Minimize coupling between slices, maximize cohesion within
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
##### 8. Define boundaries — assign DCB tags, name consistency scopes
#### Mapping to Crablet
##### Trigger → HTTP request, scheduler, or external event
##### Command → CommandHandler + CommandExecutor
##### Event → immutable record appended to EventStore
##### View → ViewProjector + async poller
##### Automation → AutomationHandler + async poller
##### Translation → OutboxPublisher + async poller

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
#### Diagram sidecar — triggers, synthetic nodes, eventBadges
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
