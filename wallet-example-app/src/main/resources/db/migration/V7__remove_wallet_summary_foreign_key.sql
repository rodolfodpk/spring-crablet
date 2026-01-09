-- Remove foreign key constraint from wallet_summary_view to wallet_balance_view
-- This constraint causes race conditions when views are processed asynchronously,
-- as the summary view might process WalletOpened before the balance view.
-- Both views are independent materialized views populated from events.

-- Drop the foreign key constraint if it exists
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 
        FROM information_schema.table_constraints 
        WHERE constraint_name = 'wallet_summary_view_wallet_id_fkey'
        AND table_name = 'wallet_summary_view'
    ) THEN
        ALTER TABLE wallet_summary_view 
        DROP CONSTRAINT wallet_summary_view_wallet_id_fkey;
    END IF;
END $$;

COMMENT ON TABLE wallet_summary_view IS 'Aggregated wallet statistics for dashboards and reporting. Independent materialized view (no FK to wallet_balance_view to avoid race conditions).';

