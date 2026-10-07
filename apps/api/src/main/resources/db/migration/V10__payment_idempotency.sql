-- V10 (Step 16, Hardening): payment idempotency key.
-- Release gate "retrying never duplicates money": POST /groups/{id}/payments may
-- carry an Idempotency-Key header (<=64 chars); the same key + identical payload
-- replays the original payment instead of creating a new one, a different payload
-- under a used key -> 409 (app). The partial unique index also stops concurrent
-- double-submits at the DB level (raises -> app maps to 409).
-- Never delete rows: an existing payment keeps its key forever (append-only).

ALTER TABLE payments ADD COLUMN idempotency_key TEXT;

CREATE UNIQUE INDEX uq_payments_group_idempotency
  ON payments(group_id, idempotency_key)
  WHERE idempotency_key IS NOT NULL;