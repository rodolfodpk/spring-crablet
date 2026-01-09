-- Wallet statement view for tracking statement periods with period totals
CREATE TABLE wallet_statement_view (
    statement_id VARCHAR(255) PRIMARY KEY,
    wallet_id VARCHAR(255) NOT NULL,
    year INTEGER NOT NULL,
    month INTEGER,
    day INTEGER,
    hour INTEGER,
    
    -- Balances
    opening_balance DECIMAL(19, 2) NOT NULL,
    closing_balance DECIMAL(19, 2),
    
    -- Period totals (activity during this period)
    total_deposits DECIMAL(19, 2) NOT NULL DEFAULT 0,
    total_withdrawals DECIMAL(19, 2) NOT NULL DEFAULT 0,
    total_transfers_in DECIMAL(19, 2) NOT NULL DEFAULT 0,
    total_transfers_out DECIMAL(19, 2) NOT NULL DEFAULT 0,
    transaction_count INTEGER NOT NULL DEFAULT 0,
    
    -- Timestamps
    opened_at TIMESTAMP WITH TIME ZONE NOT NULL,
    closed_at TIMESTAMP WITH TIME ZONE,
    
    -- Constraints
    CONSTRAINT chk_period_consistency CHECK (
        (hour IS NULL OR day IS NOT NULL) AND
        (day IS NULL OR month IS NOT NULL) AND
        (month IS NULL OR (month >= 1 AND month <= 12)) AND
        (day IS NULL OR (day >= 1 AND day <= 31)) AND
        (hour IS NULL OR (hour >= 0 AND hour <= 23))
    ),
    CONSTRAINT chk_closing_balance CHECK (
        (closed_at IS NULL OR closing_balance IS NOT NULL)
    )
);

-- Junction table for tracking processed events (idempotency)
CREATE TABLE statement_transactions (
    statement_id VARCHAR(255) NOT NULL,
    event_position BIGINT NOT NULL,
    PRIMARY KEY (statement_id, event_position),
    FOREIGN KEY (statement_id) REFERENCES wallet_statement_view(statement_id) ON DELETE CASCADE
);

-- Indexes for wallet_statement_view
CREATE INDEX idx_wallet_statement_wallet ON wallet_statement_view(wallet_id, year DESC, month DESC NULLS LAST, day DESC NULLS LAST, hour DESC NULLS LAST);
CREATE INDEX idx_wallet_statement_open ON wallet_statement_view(wallet_id, closed_at) WHERE closed_at IS NULL;
CREATE INDEX idx_wallet_statement_period ON wallet_statement_view(wallet_id, year, month, day, hour);

-- Index for statement_transactions
CREATE INDEX idx_statement_transactions_statement ON statement_transactions(statement_id);
CREATE INDEX idx_statement_transactions_position ON statement_transactions(event_position);

COMMENT ON TABLE wallet_statement_view IS 'Materialized view of wallet statement periods with period totals for reconciliation and reporting';
COMMENT ON COLUMN wallet_statement_view.statement_id IS 'Unique statement identifier (format: wallet:{walletId}:{year}-{month}...)';
COMMENT ON COLUMN wallet_statement_view.wallet_id IS 'Wallet identifier';
COMMENT ON COLUMN wallet_statement_view.year IS 'Statement year';
COMMENT ON COLUMN wallet_statement_view.month IS 'Statement month (1-12) or NULL for yearly periods';
COMMENT ON COLUMN wallet_statement_view.day IS 'Statement day (1-31) or NULL for monthly/yearly periods';
COMMENT ON COLUMN wallet_statement_view.hour IS 'Statement hour (0-23) or NULL for daily/monthly/yearly periods';
COMMENT ON COLUMN wallet_statement_view.opening_balance IS 'Balance at the start of the statement period';
COMMENT ON COLUMN wallet_statement_view.closing_balance IS 'Balance at the end of the statement period (NULL until closed)';
COMMENT ON COLUMN wallet_statement_view.total_deposits IS 'Total amount deposited during this period';
COMMENT ON COLUMN wallet_statement_view.total_withdrawals IS 'Total amount withdrawn during this period';
COMMENT ON COLUMN wallet_statement_view.total_transfers_in IS 'Total amount received via transfers during this period';
COMMENT ON COLUMN wallet_statement_view.total_transfers_out IS 'Total amount sent via transfers during this period';
COMMENT ON COLUMN wallet_statement_view.transaction_count IS 'Number of transactions in this period';
COMMENT ON COLUMN wallet_statement_view.opened_at IS 'When the statement period was opened';
COMMENT ON COLUMN wallet_statement_view.closed_at IS 'When the statement period was closed (NULL if still open)';

COMMENT ON TABLE statement_transactions IS 'Junction table tracking which events have been processed for each statement (idempotency)';
COMMENT ON COLUMN statement_transactions.statement_id IS 'Statement identifier';
COMMENT ON COLUMN statement_transactions.event_position IS 'Event position from events table';

