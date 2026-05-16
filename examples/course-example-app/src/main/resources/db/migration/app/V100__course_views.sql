-- Materialized view for course availability (capacity vs. enrollment)
CREATE TABLE course_availability (
    course_id   VARCHAR(255) PRIMARY KEY,
    capacity    INT          NOT NULL DEFAULT 0,
    enrolled    INT          NOT NULL DEFAULT 0,
    updated_at  TIMESTAMP WITH TIME ZONE
);
