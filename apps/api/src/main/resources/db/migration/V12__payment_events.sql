-- V12: payment_events — append-only forensics for the ABA PayWay payment path (Step 25).
-- Every webhook callback invocation and every check-transaction exchange is journaled here.
-- Insert-only by design: the application never updates or deletes rows.

CREATE TABLE payment_events (
  id          BIGSERIAL PRIMARY KEY,
  tran_id     VARCHAR(64),
  source      VARCHAR(16)  NOT NULL,  -- CALLBACK | CHECK
  outcome     VARCHAR(64),            -- CALLBACK: RATE_LIMITED | MISSING_TRAN_ID | ORDER_<status> | ERROR
                                      -- CHECK:    APPROVED | NOT_APPROVED
  payload     TEXT         NOT NULL,  -- raw JSON, truncated to 8000 chars by the app
  remote_ip   VARCHAR(64),
  created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_pay_events_tran ON payment_events (tran_id, created_at DESC);
