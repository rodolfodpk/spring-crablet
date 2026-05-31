# Crablet Adoption Evaluation

Perspective: a developer who understands event sourcing, DCB, and event modeling evaluating
whether to adopt Crablet. Written May 2026, against the pre-1.0 SNAPSHOT.

---

## What Crablet Does Well

**DCB implementation is the real deal.** The three-way taxonomy — `appendCommutative`,
`appendNonCommutative`, `appendIdempotent` — is a clean, correct mapping of DCB semantics into
a Java API. The `CommandDecision` sealed interface enforces which variant a handler returns at
compile time. `CommutativeGuarded` even validates at construction that the lifecycle query doesn't
overlap with the appended event types — the framework catches a common design mistake before it
runs.

The course enrollment example (`SubscribeStudentToCourseCommandHandler`) is the strongest proof:
capacity limit + student subscription limit enforced in a single decision model across two
"aggregates", no eventual consistency, no process manager. That's the DCB killer use case, and it
works correctly.

**API surface is minimal and well-scoped.** `EventStore` has 3 append methods + `project` +
`executeInTransaction`. The `@Stable`/`@Internal` annotations separate the contract from the
plumbing. The public API guide is honest about what's stable and what isn't.

**Testing story is solid.** `InMemoryEventStore` + `AbstractInMemoryHandlerTest` (given/when/then, no
Docker) + `AbstractCrabletTest` (Testcontainers integration, shared container) covers the full
pyramid. This is better test tooling than most event sourcing frameworks provide out of the box.

**Documentation is above average.** `DCB_AND_CRABLET.md` explains the SQL, the timeline,
performance numbers (~350 req/s on withdrawals), and *why* `appendIdempotent` needs advisory locks
when `appendNonCommutative` doesn't. That level of honesty about implementation details is rare and
valuable.

---

## Where to Be Careful

**Everything is 1.0.0-SNAPSHOT.** The `@Stable` docs explicitly say compatibility is not enforced
by a checker, and "after 1.0, breaking changes should use deprecation first." Production adoption
today means tracking a moving API with no binary compatibility guarantee.

**`StateProjector` is verbose.** The transition pattern uses string-based switches
(`case String s when s.equals(type(...))`). The `SubscribeStudentToCourseCommandHandler` projector
is 60+ lines of case logic for 4 event types. At 10–15 event types per aggregate this becomes a
real ergonomic problem. Annotated handler methods or a fluent builder would help; right now it's
all manual.

**No built-in retry on `ConcurrencyException`.** The docs state this explicitly: "CommandExecutor
does not retry automatically." Every application must wire Resilience4j or equivalent itself. For a
framework that encapsulates DCB semantics, leaving retry unaddressed means every team rediscovers
the same wiring — and some will get it wrong.

**AI-first workflow is preview, not production.** The docs say it directly: "This workflow is
currently a preview direction while the generator matures." Setup requires building the JAR,
copying it, LLM API calls, and a repair loop. The manual path (commands-first) is the only stable
path today.

**PostgreSQL is a hard constraint.** MVCC, `pg_notify`, GIN indexes on `TEXT[]` tags — these are
core, not optional.

---

## Adoption Decision Matrix

| Situation | Verdict |
|---|---|
| Greenfield, committed to Postgres, want DCB correctness | Adopt the manual path now. Start with `crablet-eventstore` + `crablet-commands`. |
| Need multi-aggregate consistency without eventual consistency | Strong fit. The course enrollment example is the use case the framework was built for. |
| Need API stability guarantees before production | Wait for 1.0 or treat it as a strategic bet with eyes open. |
| Want AI-first codegen as primary workflow | Wait. The generator is not production-ready. |
| Need database portability | Not a fit. |
| Small team, simple aggregates, no DCB need | Axon or EventStore DB are more mature alternatives. |

---

## Bottom Line

Crablet's DCB core is technically sound and better documented than most. The API design choices
(sealed `CommandDecision`, explicit concurrency variants, sensible test tooling) show a framework
that understands the domain. The pre-1.0 status and the missing built-in retry story are the
concrete gaps to address before broader adoption. The manual commands-first path is adoptable today
with eyes open; the AI-first workflow is not yet.

---

## Open Items Worth Addressing Before 1.0

- Binary compatibility checker for `@Stable` APIs (e.g., Revapi or japicmp in CI)
- `StateProjector` ergonomics: reduce the string-switch boilerplate at scale
- Optional built-in `ConcurrencyException` retry in `CommandExecutor` (configurable, off by default)
- `ViewProjector` datasource injection should be made explicit in the interface contract or docs
