-- Step 27: index for the daily subscription lifecycle scan
-- (SubscriptionLifecycleJob -> findBySubscriptionEndsAtBeforeAndSubscriptionStatusNotIn)
CREATE INDEX idx_owner_sub_ends_status
    ON owner_accounts(subscription_ends_at, subscription_status);
