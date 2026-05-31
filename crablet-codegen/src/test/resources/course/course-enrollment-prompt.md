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
