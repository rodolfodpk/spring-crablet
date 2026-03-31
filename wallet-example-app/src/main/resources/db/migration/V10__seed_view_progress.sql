-- Seed initial view_progress rows for all registered views.
-- This ensures rows always exist so pause/resume/reset operations
-- (which use UPDATE, not UPSERT) work from the first operation.

INSERT INTO view_progress (view_name, status, last_position, last_updated_at, created_at)
VALUES
    ('wallet-balance-view',     'ACTIVE', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('wallet-transaction-view', 'ACTIVE', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('wallet-summary-view',     'ACTIVE', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('wallet-statement-view',   'ACTIVE', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (view_name) DO NOTHING;
