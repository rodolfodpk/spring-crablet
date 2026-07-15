# Course Enrollment Showcase

Course Enrollment is the compact showcase for Crablet's AI tooling loop. It is small enough to
review in one pass, but it still exercises the pieces that make generated Crablet applications
interesting: DCB tags, command patterns, guard events, a view, an automation, an outbox publisher,
and BDD scenario scaffolding.

The generator-ready model is
[course-enrollment-showcase-event-model.yaml](../examples/course-enrollment-showcase-event-model.yaml).

## Workshop Input

Start from Event Modeling workshop material rather than from Java classes:

- outcomes: students can subscribe to courses, and staff can manage capacity
- core commands: `DefineCourse`, `RegisterStudent`, `ChangeCourseCapacity`,
  `SubscribeStudentToCourse`
- core events: `CourseDefined`, `StudentRegistered`, `CourseCapacityChanged`,
  `StudentSubscribedToCourse`
- small automation follow-up: `NotifyStudentSubscription` emits
  `SendStudentSubscriptionNotification`, which records `StudentSubscriptionNotificationSent`
- sidecars: command patterns, guard events, DCB tags, BDD scenarios, view needs, automation, and
  external publication

The AI assistant's job is to translate that workshop material into a reviewable `event-model.yaml`.
The model is the source of truth; diagrams and generated code are projections from it.

## Why The Tags Matter

Crablet's consistency boundary is expressed through event tags:

- `course_id` scopes course lifecycle and capacity decisions.
- `student_id` scopes student registration.
- `StudentSubscribedToCourse` carries both `course_id` and `student_id`, making subscription a
  multi-entity decision.

`SubscribeStudentToCourse` is `non-commutative` because capacity and duplicate-subscription checks
depend on the current facts for the course/student pair. Its `guardEvents` declare the lifecycle
preconditions: the course must have been defined, and the student must have been registered.

## Generated Flow

From the `spring-crablet` repository, copy the model into an app created from
`templates/crablet-app` and use it as the app-level `event-model.yaml`:

```bash
cp docs/user/examples/course-enrollment-showcase-event-model.yaml ../course-service/event-model.yaml
cd ../course-service
make plan
make generate
make verify
```

`make plan` is deterministic and shows the artifacts that would be generated. `make generate`
creates the Spring/Crablet structure for events, commands, handlers, projections, automation,
outbox, migrations, and scenario test scaffolds. `make verify` compiles and tests the generated
application.

The scenario section is deliberately embedded in the model. These are workshop sidecars captured
next to the command/event facts, not separate `.feature` files.
