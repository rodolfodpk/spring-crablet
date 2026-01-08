-- Wallet transaction history view for audit and reporting
CREATE TABLE wallet_transaction_view (
    transaction_id VARCHAR(255) NOT NULL,
    wallet_id VARCHAR(255) NOT NULL,
    event_type VARCHAR(255) NOT NULL,
    amount DECIMAL(19, 2) NOT NULL,
    description TEXT,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    event_position BIGINT NOT NULL,
    PRIMARY KEY (transaction_id, event_position),
    FOREIGN KEY (wallet_id) REFERENCES wallet_balance_view(wallet_id) ON DELETE CASCADE
);

CREATE INDEX idx_wallet_transaction_wallet ON wallet_transaction_view(wallet_id, occurred_at DESC);
CREATE INDEX idx_wallet_transaction_position ON wallet_transaction_view(event_position);

COMMENT ON TABLE wallet_transaction_view IS 'Materialized view of wallet transactions for history and audit';
COMMENT ON COLUMN wallet_transaction_view.transaction_id IS 'Unique transaction identifier (deposit_id, withdrawal_id, or transfer_id)';
COMMENT ON COLUMN wallet_transaction_view.wallet_id IS 'Wallet identifier';
COMMENT ON COLUMN wallet_transaction_view.event_type IS 'Event type (DepositMade, WithdrawalMade, MoneyTransferred)';
COMMENT ON COLUMN wallet_transaction_view.amount IS 'Transaction amount';
COMMENT ON COLUMN wallet_transaction_view.event_position IS 'Event position for idempotency';

