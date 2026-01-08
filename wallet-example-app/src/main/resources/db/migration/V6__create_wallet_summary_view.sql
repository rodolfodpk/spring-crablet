-- Wallet summary view for aggregated statistics
CREATE TABLE wallet_summary_view (
    wallet_id VARCHAR(255) PRIMARY KEY,
    total_deposits DECIMAL(19, 2) NOT NULL DEFAULT 0,
    total_withdrawals DECIMAL(19, 2) NOT NULL DEFAULT 0,
    total_transfers_in DECIMAL(19, 2) NOT NULL DEFAULT 0,
    total_transfers_out DECIMAL(19, 2) NOT NULL DEFAULT 0,
    current_balance DECIMAL(19, 2) NOT NULL DEFAULT 0,
    last_transaction_at TIMESTAMP WITH TIME ZONE
    -- Note: No foreign key constraint to wallet_balance_view to avoid race conditions
    -- when views are processed asynchronously. Both views are independent materialized views.
);

COMMENT ON TABLE wallet_summary_view IS 'Aggregated wallet statistics for dashboards and reporting';
COMMENT ON COLUMN wallet_summary_view.total_deposits IS 'Total amount deposited into wallet';
COMMENT ON COLUMN wallet_summary_view.total_withdrawals IS 'Total amount withdrawn from wallet';
COMMENT ON COLUMN wallet_summary_view.total_transfers_in IS 'Total amount received via transfers';
COMMENT ON COLUMN wallet_summary_view.total_transfers_out IS 'Total amount sent via transfers';
COMMENT ON COLUMN wallet_summary_view.current_balance IS 'Current wallet balance';

