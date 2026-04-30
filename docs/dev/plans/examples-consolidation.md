# Plan: Examples consolidation + course-example-app

## Context

Phase 2b of the roadmap. The wallet example covers single-aggregate patterns well. The Course
domain in `shared-examples-domain` demonstrates the key differentiator: multi-aggregate DCB
(one `SubscribeStudentToCourse` command enforces both course capacity AND student subscription
limit in a single consistency boundary). Making it runnable requires a second app.

Both runnable examples belong together under `examples/` to signal they are peer demonstrations,
not framework internals.

---

## Target layout

```
examples/
  wallet-example-app/     ← moved from root
  course-example-app/     ← new
shared-examples-domain/   ← stays at root (used by framework modules in test scope)
```

---

## Step 1 — Move `wallet-example-app` into `examples/`

### 1a. Move the directory

```bash
mkdir examples
git mv wallet-example-app examples/wallet-example-app
```

### 1b. Update `pom.xml` (root)

The reactor comment block referencing `wallet-example-app`:
```xml
<!-- Build separately: cd wallet-example-app && ../mvnw install -->
<!-- <module>wallet-example-app</module> -->
```
Update path to `examples/wallet-example-app`.

### 1c. Update `Makefile`

Affected targets (current path → new path):
- `start`: `cd wallet-example-app` → `cd examples/wallet-example-app`
- `wallet-dev`: same
- `clean`: `cd wallet-example-app` → `cd examples/wallet-example-app`
- Help text referencing `wallet-example-app` directory

### 1d. Update docs

Files referencing `wallet-example-app` path that need updating:
- `docs/user/BUILD.md` — build instructions and path examples
- `docs/user/QUICKSTART.md`
- `docs/user/TUTORIAL.md`
- `docs/user/MODULES.md`
- `docs/user/MANAGEMENT_API.md`
- `docs/user/OBSERVABILITY.md`
- `docs/user/CORRELATION_CAUSATION.md`
- `docs/user/CREATE_A_CRABLET_APP.md`
- `docs/user/ai-tooling/EVENT_MODELING.md`
- `docs/user/ai-tooling/FEATURE_SLICE_WORKFLOW.md`
- `CLAUDE.md`

### 1e. Verify

```bash
make install   # reactor build must still pass
make start     # wallet app must still start
```

---

## Step 2 — Create `course-example-app`

### Module coverage

| Module | What it exercises |
|---|---|
| `crablet-eventstore` | Multi-entity DCB: course + student in one decision model |
| `crablet-commands` | 3 handlers: `DefineCourse`, `ChangeCourseCapacity`, `SubscribeStudentToCourse` |
| `crablet-commands-web` | Generic HTTP command API + Swagger |
| `crablet-views` | Course availability view (capacity, enrolled, seats remaining) |
| `crablet-automations` | React to `StudentSubscribedToCourse` — notify when course reaches capacity |
| `crablet-outbox` | Publish enrollment events externally |
| `crablet-metrics-micrometer` | Implicit via auto-config |

### 2a. Maven module

`examples/course-example-app/pom.xml`:
- Parent: root `pom.xml`
- Dependencies: all framework modules + `shared-examples-domain`
- Excluded from reactor (same pattern as wallet app)
- Update root `pom.xml` comment block to mention both apps

### 2b. Spring Boot application

`CourseApplication.java` — standard `@SpringBootApplication`.

`application.yml`:
- `crablet.views.enabled=true`
- `crablet.automations.enabled=true`
- `crablet.outbox.enabled=true`
- datasource: `jdbc:postgresql://localhost:5432/course_db`

### 2c. Flyway migrations

`V1__eventstore_schema.sql` — copy from `crablet-test-support` (events + commands tables).
`V2__course_views.sql` — create view tables:
```sql
CREATE TABLE course_availability (
    course_id       VARCHAR(255) PRIMARY KEY,
    name            VARCHAR(255),
    capacity        INT,
    enrolled        INT,
    updated_at      TIMESTAMP WITH TIME ZONE
);
```

### 2d. View projector

`CourseAvailabilityViewProjector extends AbstractTypedViewProjector<CourseEvent>`:
- Subscribes to `CourseDefined`, `CourseCapacityChanged`, `StudentSubscribedToCourse`
- Upserts into `course_availability` on each event
- View name: `"course-availability"`

### 2e. Automation

`CourseCapacityAutomation implements AutomationHandler`:
- Listens to `StudentSubscribedToCourse`
- Reads current enrollment from the event tags
- If `enrolled == capacity`, returns `AutomationDecision.ExecuteCommand` with a
  `NotifyCourseFullCommand` (or `AutomationDecision.NoOp` if not yet full)
- Alternatively: publish directly via outbox if no command is needed

### 2f. Outbox publisher

`CourseEnrollmentPublisher implements OutboxPublisher`:
- Topic: `"course-enrollments"`
- Publishes `StudentSubscribedToCourse` events to a configurable webhook URL
- Demonstrates reliable external delivery

### 2g. HTTP endpoints

`CourseCommandController` (or use `crablet-commands-web` generic endpoint):
- `POST /api/commands` — all three commands via the generic API
- `GET /api/courses/{courseId}` — reads from `course_availability` view

### 2h. Makefile targets

```makefile
course-start:
    cd examples/course-example-app && ../mvnw spring-boot:run

course-dev:
    cd examples/course-example-app && ../mvnw spring-boot:run
```

Update help text and `clean` target.

### 2i. Verify

```bash
make course-start  # app starts, Swagger available at /swagger-ui.html
# POST define-course, subscribe students, verify availability view updates
# verify automation fires when capacity is reached
```

---

## Files created / modified

**New files:**
- `examples/course-example-app/pom.xml`
- `examples/course-example-app/src/main/java/.../CourseApplication.java`
- `examples/course-example-app/src/main/resources/application.yml`
- `examples/course-example-app/src/main/resources/db/migration/V1__eventstore_schema.sql`
- `examples/course-example-app/src/main/resources/db/migration/V2__course_views.sql`
- `examples/course-example-app/src/main/java/.../view/CourseAvailabilityViewProjector.java`
- `examples/course-example-app/src/main/java/.../automation/CourseCapacityAutomation.java`
- `examples/course-example-app/src/main/java/.../outbox/CourseEnrollmentPublisher.java`
- `examples/course-example-app/src/main/java/.../api/CourseQueryController.java`
- `examples/course-example-app/src/main/java/.../config/CrabletConfig.java`

**Modified files (Step 1):**
- `Makefile`
- `pom.xml` (root)
- `CLAUDE.md`
- `docs/user/BUILD.md`, `QUICKSTART.md`, `TUTORIAL.md`, `MODULES.md`, and 6 others

**Moved files (Step 1):**
- `wallet-example-app/` → `examples/wallet-example-app/`
