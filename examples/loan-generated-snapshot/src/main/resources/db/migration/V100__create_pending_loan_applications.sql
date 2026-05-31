CREATE TABLE IF NOT EXISTS pending_loan_applications (
    application_id   VARCHAR(255)  PRIMARY KEY,
    customer_id      VARCHAR(255)  NOT NULL,
    requested_amount INTEGER       NOT NULL,
    purpose          VARCHAR(1024) NOT NULL,
    status           VARCHAR(64)   NOT NULL DEFAULT 'PENDING',
    submitted_at     TIMESTAMPTZ   NOT NULL
);
