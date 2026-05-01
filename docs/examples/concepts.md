---
markmap:
  initialExpandLevel: -1
---

# Spring Crablet

## Event Modeling
### Methodology
#### Blueprint — horizontal timeline of the entire system
#### Read left to right — time flows left across the board
#### Swim lanes — trigger / command / event / view / automation
### Building Blocks
#### Trigger — what starts the flow (UI action, schedule, external event)
#### Command — intent to change state (blue)
#### Event — committed business fact, immutable (orange/yellow)
#### View — async read model built from events (green)
#### Automation — policy that reacts to events and emits commands (amber)
#### Outbox — reliable external publication after commit (pink)
### Design Rules
#### One fact per event — events describe what happened, not what to do
#### Commands validate — events never fail
#### Views are eventual — updated asynchronously after commit
#### Automations are policies — if event X then command Y
### Mapping to Crablet
#### Trigger → HTTP request or scheduler
#### Command → CommandHandler + CommandExecutor
#### Event → immutable record appended to EventStore
#### View → ViewProjector + async poller
#### Automation → AutomationHandler + async poller
#### Outbox → OutboxPublisher + async poller

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
### Append Patterns
#### idempotent — entity creation, duplicate prevention
#### commutative — order-independent operations
#### commutative + guard — existence check before commit
#### non-commutative — state-dependent, detects conflicts

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
