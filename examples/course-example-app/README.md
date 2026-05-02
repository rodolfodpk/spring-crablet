# Course Example App

A runnable example application demonstrating **multi-entity DCB constraints** with course enrollment.

## The key differentiator vs. wallet-example-app

The `SubscribeStudentToCourse` command validates course and student lifecycle facts, then enforces
two constraints simultaneously in a single decision model — one query, one stream position, one
atomic append:

1. **Course existence** — the course must have been defined
2. **Student existence** — the student must have been registered
3. **Course capacity** — the course has N seats; reject when full
4. **Student subscription limit** — each student may enroll in at most 5 courses

This is the core DCB advantage: consistency across multiple entities without distributed locks or sagas.

## Features

| Module | What it demonstrates |
|---|---|
| `crablet-eventstore` | Multi-entity decision model spanning course + student |
| `crablet-commands` | `DefineCourse`, `RegisterStudent`, `ChangeCourseCapacity`, `SubscribeStudentToCourse` handlers |
| `crablet-commands-web` | Generic HTTP command API with Swagger — POST /api/commands |
| `crablet-views` | `CourseAvailabilityViewProjector` — capacity, enrolled, seats remaining |
| `crablet-automations` | `CourseCapacityAutomation` — logs when a course reaches full capacity |
| `crablet-outbox` | LogPublisher — enrollment events forwarded to SLF4J |

## Running

```bash
# From the repo root — must run after make install
createdb course_db
make course-start
```

Or manually:
```bash
cd examples/course-example-app && ../../mvnw spring-boot:run
```

Once running (port 8081):
- Swagger UI: http://localhost:8081/swagger-ui.html
- GET course: http://localhost:8081/api/courses/{courseId}
- POST command: http://localhost:8081/api/commands

## Example workflow

```bash
# 1. Define a course with 3 seats
curl -X POST http://localhost:8081/api/commands \
  -H 'Content-Type: application/json' \
  -d '{"type":"define_course","courseId":"cs101","capacity":3}'

# 2. Register a student
curl -X POST http://localhost:8081/api/commands \
  -H 'Content-Type: application/json' \
  -d '{"type":"register_student","studentId":"alice"}'

# 3. Subscribe students
curl -X POST http://localhost:8081/api/commands \
  -H 'Content-Type: application/json' \
  -d '{"type":"subscribe_student_to_course","studentId":"alice","courseId":"cs101"}'

# 4. Check availability
curl http://localhost:8081/api/courses/cs101
# → {"courseId":"cs101","capacity":3,"enrolled":1,"available":2,"full":false}
```

## Domain

Commands, events, and handlers live in `shared-examples-domain` under `com.crablet.examples.course`.
This app wires them up with Spring Boot, adds a view projector, an automation, and a REST query endpoint.

## See also

- [Wallet Example App](../wallet-example-app/README.md) — single-aggregate patterns, full management UI
- [DCB explained](../../crablet-eventstore/docs/DCB_AND_CRABLET.md)
- [Build instructions](../../docs/user/BUILD.md)
