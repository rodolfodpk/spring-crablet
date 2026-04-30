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

### 1b. Fix `mvnw` paths

From `examples/wallet-example-app`, the wrapper is two levels up. Update all invocations:
- `cd wallet-example-app && ../mvnw` → `cd examples/wallet-example-app && ../../mvnw`

Affected: `Makefile` (`start`, `wallet-dev`, `clean` targets).

### 1c. Update `pom.xml` (root)

Update the reactor comment block:
```xml
<!-- Build separately: cd examples/wallet-example-app && ../../mvnw install -->
<!-- <module>examples/wallet-example-app</module> -->
```

### 1d. Update all path references

Rather than a hand-maintained list, find every reference with:
```bash
rg "wallet-example-app|cd wallet-example-app" --type md --type java --type xml --type yaml --type properties -l
```
Update each occurrence. Known locations: `Makefile`, `pom.xml`, `CLAUDE.md`,
`docs/user/BUILD.md`, `QUICKSTART.md`, `TUTORIAL.md`, `MODULES.md`,
`MANAGEMENT_API.md`, `OBSERVABILITY.md`, `CORRELATION_CAUSATION.md`,
`CREATE_A_CRABLET_APP.md`, `ai-tooling/EVENT_MODELING.md`,
`ai-tooling/FEATURE_SLICE_WORKFLOW.md`, and any `docs/dev/` plan files.

### 1e. Verify

```bash
make install        # reactor build still passes
make start          # wallet app starts from new path
```

---

## Step 2 — Promote course command handlers into `shared-examples-domain`

The three course command handlers currently live in `crablet-commands/src/test/java` and are
not available as a compile dependency. They must be moved into `shared-examples-domain` so
`course-example-app` can use them.

Files to move to `shared-examples-domain/src/main/java/com/crablet/examples/course/handlers/`:
- `DefineCourseCommandHandler.java`
- `ChangeCourseCapacityCommandHandler.java`
- `SubscribeStudentToCourseCommandHandler.java`

After moving, update the `crablet-commands` test classes that reference them to import from the
new location (or remove duplication if tests already use `shared-examples-domain` in test scope).

---

## Step 3 — Create `course-example-app`

### 3a. Maven module

`examples/course-example-app/pom.xml`:
- Parent: root `pom.xml`
- Dependencies: all framework modules + `shared-examples-domain`
- Excluded from reactor (same pattern as wallet app — comment in root `pom.xml`)
- All `mvnw` invocations use `../../mvnw`

### 3b. Spring Boot application

`CourseApplication.java` — standard `@SpringBootApplication`.

`application.properties`:
```properties
crablet.views.enabled=true
crablet.automations.enabled=true
crablet.outbox.enabled=true
crablet.outbox.topics.topics.course-enrollments.publishers=LogPublisher
crablet.outbox.topics.topics.course-enrollments.required-tags=student_id
spring.datasource.url=jdbc:postgresql://localhost:5432/course_db
```

### 3c. `CommandApiExposedCommands` bean

Required for `crablet-commands-web` generic endpoint to accept course commands:
```java
@Bean
public CommandApiExposedCommands commandApiExposedCommands() {
    return CommandApiExposedCommands.fromPackages("com.crablet.examples.course");
}
```

### 3d. Flyway migrations

Copy only the **framework** migrations from `wallet-example-app/src/main/resources/db/migration/`.
Do **not** copy wallet-specific migrations (V4–V8 wallet view tables, V10 seed rows).
Add a course-specific view migration in their place.

| Migration | Source | Purpose |
|---|---|---|
| `V1__eventstore_schema.sql` | copy from wallet | `events` + `commands` tables |
| `V3__view_progress_schema.sql` | copy from wallet | View processor progress tracking |
| `V9__reaction_progress_schema.sql` | copy from wallet | Creates `reaction_progress` table |
| `V10__rename_reaction_progress_to_automation_progress.sql` | copy from wallet | Renames to `automation_progress` (required by `crablet-automations`) |
| `V11__correlation_causation.sql` | copy from wallet (was V12 in wallet) | Correlation/causation columns on events |
| `V12__outbox_schema.sql` | copy from wallet (was V13) | Outbox scan progress |
| `V13__shared_fetch_scan_progress.sql` | copy from wallet (was V14) | Shared-fetch progress (if enabled) |
| `V2__course_views.sql` | new | App-specific: `course_availability` view table |

**Note on version numbering:** Flyway versions must be sequential and gap-free within the app.
Renumber framework migrations as needed (wallet's V12/V13/V14 become V11/V12/V13 here since
wallet V10 is a seed row migration that is not copied).

`V2__course_views.sql`:
```sql
CREATE TABLE course_availability (
    course_id   VARCHAR(255) PRIMARY KEY,
    capacity    INT          NOT NULL DEFAULT 0,
    enrolled    INT          NOT NULL DEFAULT 0,
    updated_at  TIMESTAMP WITH TIME ZONE
);
```

### 3e. View projector

`CourseAvailabilityViewProjector extends AbstractTypedViewProjector<CourseEvent>`:
- Subscribes via `ViewProjector.subscription("CourseDefined", "CourseCapacityChanged", "StudentSubscribedToCourse")`
- Upserts into `course_availability` on each event
- View name: `"course-availability"`

### 3f. Automation

`CourseCapacityAutomation implements AutomationHandler`:
- Listens to `StudentSubscribedToCourse`
- **Does not read from event tags** — tags only carry `course_id` and `student_id`, not enrollment counts
- Projects current enrollment and capacity from the event store using `CourseQueryPatterns.subscriptionDecisionModel`
- If enrollment == capacity: returns `AutomationDecision.NoOp("course-full-logged")` and logs a
  capacity-reached message — **no `NotifyCourseFullCommand` is defined**, so keep this simple
- Otherwise: returns `AutomationDecision.NoOp("enrollment-ok")`

**Decision:** keep the automation as a read-and-log pattern, not a command-dispatch pattern.
Adding a `NotifyCourseFullCommand` + handler + event would be a disproportionate addition for
a demo; the outbox publisher already handles external notification. The automation demonstrates
the `decide()` contract and event-store projection inside an automation, which is the goal.

### 3g. Outbox publisher

Use the built-in `LogPublisher` (already provided by the framework) — no custom publisher class needed.
Topic and tag filter are declared entirely in `application.properties`:

```properties
crablet.outbox.topics.topics.course-enrollments.publishers=LogPublisher
crablet.outbox.topics.topics.course-enrollments.required-tags=student_id
```

The `required-tags=student_id` filter ensures only `StudentSubscribedToCourse` events (which tag
both `course_id` and `student_id`) are picked up by this topic — `CourseDefined` and
`CourseCapacityChanged` only tag `course_id` and will not match.

### 3h. HTTP query endpoint

`CourseQueryController`:
- `GET /api/courses/{courseId}` — reads from `course_availability` view table

### 3i. Makefile targets

```makefile
course-start:
	cd examples/course-example-app && ../../mvnw spring-boot:run

course-dev:
	cd examples/course-example-app && ../../mvnw spring-boot:run
```

Add `clean` entry and help text.

### 3j. Verify

```bash
make course-start
# POST /api/commands — DefineCourse, SubscribeStudentToCourse
# GET /api/courses/{id} — verify availability view updates
# verify automation fires when enrollment reaches capacity
```

---

## Files created / modified

**Moved (Step 1):**
- `wallet-example-app/` → `examples/wallet-example-app/`

**Moved (Step 2):**
- `crablet-commands/src/test/java/.../handlers/courses/*.java` → `shared-examples-domain/src/main/java/.../course/handlers/`

**New files (Step 3):**
- `examples/course-example-app/pom.xml`
- `examples/course-example-app/src/main/java/.../CourseApplication.java`
- `examples/course-example-app/src/main/java/.../config/CrabletConfig.java`
- `examples/course-example-app/src/main/resources/application.properties`
- `examples/course-example-app/src/main/resources/db/migration/V1–V14 + V2__course_views.sql`
- `examples/course-example-app/src/main/java/.../view/CourseAvailabilityViewProjector.java`
- `examples/course-example-app/src/main/java/.../automation/CourseCapacityAutomation.java`
- `examples/course-example-app/src/main/java/.../api/CourseQueryController.java`
- (no custom outbox publisher — uses built-in `LogPublisher`)

**Modified (Steps 1–3):**
- `Makefile`
- `pom.xml` (root)
- `CLAUDE.md`
- All doc files found by `rg "wallet-example-app"` sweep
