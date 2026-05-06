# AI Tooling Course Enrollment Showcase

## Goal

Create a generator-ready Course Enrollment showcase that demonstrates the full AI tooling loop:
Event Modeling workshop notes and sidecars become `event-model.yaml`, then `make plan`,
`make generate`, and `make verify` turn that model into a Spring/Crablet application structure.

## Scope

- Add a user-facing example model at
  `docs/user/examples/course-enrollment-showcase-event-model.yaml`.
- Add a walkthrough at `docs/user/ai-tooling/COURSE_ENROLLMENT_SHOWCASE.md`.
- Use existing event-model fields only: `events.tags`, `commands.pattern`,
  `commands.guardEvents`, `views`, `automations`, `outbox`, `scenarios`, and optional
  `diagram` metadata.
- Keep Course Enrollment as the canonical multi-entity DCB demo.

## Showcase Model

Core commands:

- `DefineCourse`
- `RegisterStudent`
- `ChangeCourseCapacity`
- `SubscribeStudentToCourse`

Small automation follow-up command:

- `SendStudentSubscriptionNotification`

Core events:

- `CourseDefined`
- `StudentRegistered`
- `CourseCapacityChanged`
- `StudentSubscribedToCourse`

Small automation follow-up event:

- `StudentSubscriptionNotificationSent`

DCB tags:

- `course_id`
- `student_id`
- `[course_id, student_id]` for the subscription fact

Command patterns:

- `idempotent` for defining courses and registering students
- `non-commutative` for capacity changes and subscriptions

Lifecycle guards:

- `ChangeCourseCapacity` requires `CourseDefined`
- `SubscribeStudentToCourse` requires both `CourseDefined` and `StudentRegistered`

Downstream model:

- `CourseAvailability` view
- `NotifyStudentSubscription` automation
- `CourseEventsPublisher` outbox publisher to `course-events`

BDD scenarios:

- student subscribes successfully
- course is full
- student is already subscribed
- course does not exist
- student is not registered

## Verification

- Validate `docs/user/examples/course-enrollment-showcase-event-model.yaml` against
  `docs/user/examples/event-model-schema.json`.
- Run `make codegen-plan-example` to confirm the existing documented planner fixture still works.
- Run `make codegen-check` if codegen docs, schema, or checked fixtures change.
