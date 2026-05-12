# Course Core Workflow

Course Enrollment is the first AI workflow target for Crablet codegen. The goal is modest: prove
that a user can describe one Course domain slice, let the assistant turn it into
`event-model.yaml`, review the model, run `plan`, and then generate a Spring/Crablet app.

This is smaller than the full Course showcase. It intentionally excludes capacity changes,
automations, outbox publishers, and Kubernetes.

## Target Slice

The first Course slice covers:

- admins define courses
- students register
- registered students subscribe to existing courses
- courses cannot exceed capacity
- a student cannot subscribe to the same course twice
- `CourseAvailability` shows capacity, enrolled count, and remaining seats

The checked regression inputs are:

- Course prompt fixture:
  [`embabel-codegen/src/test/resources/course/course-enrollment-prompt.md`](../../../embabel-codegen/src/test/resources/course/course-enrollment-prompt.md)
- Expected model fixture:
  [`embabel-codegen/src/test/resources/course/course-core-event-model.yaml`](../../../embabel-codegen/src/test/resources/course/course-core-event-model.yaml)

## Dialogue Shape

Start with the Event Modeling skill and ask for only this slice:

```text
/event-modeling
I want to model course enrollment.

Admins can define a course with a course id, title, and capacity.
Students can register with a student id and display name.
Registered students can subscribe to an existing course.

Rules:
- a course must exist before a student can subscribe to it
- a student must be registered before subscribing
- a course cannot exceed its capacity
- a student cannot subscribe to the same course twice

Reviewers need a CourseAvailability view that shows each course's capacity, enrolled count,
and remaining seats.
```

The assistant should translate that conversation into a small `event-model.yaml` with:

- commands: `DefineCourse`, `RegisterStudent`, `SubscribeStudentToCourse`
- events: `CourseDefined`, `StudentRegistered`, `StudentSubscribedToCourse`
- tags: `course_id`, `student_id`
- command pattern: `SubscribeStudentToCourse` is `non-commutative`
- guards: subscription requires both `CourseDefined` and `StudentRegistered`
- view: `CourseAvailability`
- scenarios: happy path, course full, duplicate subscription, missing course, missing student

## Review Before Generation

Before running generation, review the YAML as the durable domain contract:

```text
Review event-model.yaml for the Course core slice.
List missing model facts instead of guessing.
Confirm whether embabel-codegen can plan and generate this model.
```

The model is not ready if it omits:

- event fields or command fields
- tags on the events used for consistency
- `guardEvents` for subscription
- the capacity and duplicate-subscription scenarios
- the `CourseAvailability` view fields

## Plan And Generate

From an app created from `templates/crablet-app`:

```bash
make plan
make generate
make verify
make sync-scenarios
```

`make plan` is the contract between the AI workflow and codegen. It must fail fast when the model
has unsupported or incomplete structure; it should not silently continue to generation.

For contributors working in this repository, the Course workflow is covered by
`CourseWorkflowFixtureTest`, which asserts the prompt, fixture model, planned artifacts, and
fast-fail validation behavior.

## Stop Here

Do not add the following until the Course core path is reliable:

- `ChangeCourseCapacity`
- notification automations
- outbox publishing
- Kubernetes manifests

Those belong in later milestones after the plain-language Course prompt to generated app loop is
boringly repeatable.
