-- Step 14: In-app notifications (01-DOMAIN §13), MVP = in-app feed, sync transitions only.
-- Recipients are USERS with logins (members + host). Notification failures must never
-- roll back the surrounding financial transaction (handled in NotificationService).
CREATE TABLE notifications (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES users(id),
    type       VARCHAR(32) NOT NULL,
    title      VARCHAR(120) NOT NULL,
    body       VARCHAR(500) NOT NULL,
    read_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_notifications_user_created
    ON notifications (user_id, created_at DESC);