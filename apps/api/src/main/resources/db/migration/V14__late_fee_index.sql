-- Step 29: late-fee assessment. groups.late_fee_type / late_fee_value existed since V3;
-- this migration only adds the lookup index for the "already charged" delta sum
-- (LateFeeService -> sum of existing LATE_FEE rows per source cycle + share).
-- LATE_FEE is excluded from uq_ledger_cycle_share_type (V7) on purpose:
-- cumulative delta rows may be added over successive days.
CREATE INDEX idx_ledger_late_fee_cycle_share
    ON ledger_entries(cycle_id, share_id)
    WHERE type = 'LATE_FEE';
