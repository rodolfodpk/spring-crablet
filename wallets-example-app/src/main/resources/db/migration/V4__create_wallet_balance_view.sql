-- Wallet balance view for fast balance lookups
CREATE TABLE wallet_balance_view (
    wallet_id VARCHAR(255) PRIMARY KEY,
    owner VARCHAR(255) NOT NULL,
    balance DECIMAL(19, 2) NOT NULL DEFAULT 0,
    last_updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_wallet_balance_owner ON wallet_balance_view(owner);

COMMENT ON TABLE wallet_balance_view IS 'Materialized view of wallet balances for fast API queries';
COMMENT ON COLUMN wallet_balance_view.wallet_id IS 'Unique wallet identifier';
COMMENT ON COLUMN wallet_balance_view.owner IS 'Wallet owner name';
COMMENT ON COLUMN wallet_balance_view.balance IS 'Current wallet balance';
COMMENT ON COLUMN wallet_balance_view.last_updated_at IS 'Last time the balance was updated';

