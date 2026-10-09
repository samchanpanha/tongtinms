-- V17 (Step 35): host Telegram chat id. A third-party identifier (BIGINT, signed
-- 64-bit), never a foreign key to an app row. NULL = host has not linked Telegram.

ALTER TABLE owner_accounts ADD COLUMN telegram_chat_id BIGINT;