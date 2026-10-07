-- V11: Subscriptions, ABA PayWay Integration, and Administrator Configuration.
-- 1 Month Free Trial for newly registered hosts.
-- Configurable subscription plans and PayWay payment gateway settings managed by Administrator.

-- 1. Extend owner_accounts with subscription tracking
ALTER TABLE owner_accounts
  ADD COLUMN subscription_status VARCHAR(32) NOT NULL DEFAULT 'TRIAL',
  ADD COLUMN trial_ends_at       TIMESTAMPTZ NOT NULL DEFAULT (now() + INTERVAL '30 days'),
  ADD COLUMN subscription_ends_at TIMESTAMPTZ NOT NULL DEFAULT (now() + INTERVAL '30 days'),
  ADD COLUMN current_plan_id     BIGINT;

-- 2. System settings for administrator (ABA PayWay credentials, trial duration, policies)
CREATE TABLE system_settings (
  key         VARCHAR(100) PRIMARY KEY,
  value       TEXT NOT NULL,
  description TEXT,
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Seed default settings
INSERT INTO system_settings (key, value, description) VALUES
  ('payway_merchant_id', 'ec438992', 'ABA PayWay Merchant ID (Sandbox/Production)'),
  ('payway_api_key', '4c05336bf1f621375d86242aebe3daee7fa5a1c3', 'ABA PayWay Hash/API Secret Key'),
  ('payway_api_url', 'https://checkout-sandbox.payway.com.kh/api/payment-gateway/v1/payments/purchase', 'ABA PayWay Purchase Endpoint'),
  ('payway_check_url', 'https://checkout-sandbox.payway.com.kh/api/payment-gateway/v1/payments/check-transaction', 'ABA PayWay Check Transaction Endpoint'),
  ('payway_sandbox_mode', 'true', 'Enable Sandbox Mode (true/false)'),
  ('payway_enabled', 'true', 'Enable PayWay Payment Gateway (true/false)'),
  ('free_trial_days', '30', 'Free trial duration in days upon host registration'),
  ('grace_period_days', '3', 'Grace period days before hard subscription block'),
  ('enforce_subscription', 'true', 'Enforce subscription limits on host operations');

-- 3. Subscription plans configured by Administrator
CREATE TABLE subscription_plans (
  id              BIGSERIAL PRIMARY KEY,
  code            VARCHAR(50) NOT NULL UNIQUE,
  name            VARCHAR(100) NOT NULL,
  description     TEXT,
  price_minor     BIGINT NOT NULL CHECK (price_minor >= 0),
  currency        CHAR(3) NOT NULL REFERENCES currencies(code),
  duration_months INTEGER NOT NULL CHECK (duration_months > 0),
  max_groups      INTEGER NOT NULL DEFAULT -1,  -- -1 for unlimited
  max_members     INTEGER NOT NULL DEFAULT -1, -- -1 for unlimited
  features_json   TEXT,
  badge           VARCHAR(50),
  sort_order      INTEGER NOT NULL DEFAULT 0,
  is_active       BOOLEAN NOT NULL DEFAULT TRUE,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Seed initial subscription plans
INSERT INTO subscription_plans (code, name, description, price_minor, currency, duration_months, max_groups, max_members, features_json, badge, sort_order, is_active) VALUES
  ('STARTER_MONTHLY', 'Gói Khởi Nghiệp (Starter)', 'Phù hợp cho chủ hụi nhỏ và người mới bắt đầu', 900, 'USD', 1, 5, 50, '["Tối đa 5 dây hụi", "Tối đa 50 hội viên", "Đấu giá kín tự động", "Báo cáo sổ cái thời gian thực", "Hỗ trợ kỹ thuật tiêu chuẩn"]', 'Khởi nghiệp', 1, true),
  ('PRO_MONTHLY', 'Gói Chuyên Nghiệp (Pro Monthly)', 'Dành cho chủ hụi chuyên nghiệp quản lý nhiều dây', 2500, 'USD', 1, -1, -1, '["Không giới hạn số dây hụi", "Không giới hạn hội viên", "Đấu giá kín & Tự động kết toán", "Cổng thông tin hội viên tra cứu", "Thông báo biến động tức thì", "Hỗ trợ ưu tiên 24/7"]', 'Phổ biến nhất', 2, true),
  ('PRO_YEARLY', 'Gói Chuyên Nghiệp Năm (Pro Yearly)', 'Tiết kiệm 20% khi thanh toán cả năm', 24000, 'USD', 12, -1, -1, '["Toàn bộ quyền lợi gói Pro", "Thời hạn sử dụng 12 tháng trọn gói", "Tiết kiệm 20% chi phí hàng năm", "Đào tạo & Hỗ trợ riêng 1-1", "Sao lưu dữ liệu nâng cao"]', 'Tiết kiệm 20%', 3, true),
  ('PRO_KHR_MONTHLY', 'Gói Tiêu Chuẩn KHR (Campuchia)', 'Thanh toán trực tiếp bằng đồng Riel Campuchia', 100000, 'KHR', 1, -1, -1, '["Không giới hạn dây hụi và hội viên", "Tích hợp thanh toán ABA Pay KHQR", "Báo cáo song tệ USD & KHR", "Hỗ trợ tiếng Khmer & Tiếng Việt"]', 'Bản địa KHR', 4, true);

-- 4. Subscription orders and ABA PayWay transaction logs
CREATE TABLE subscription_orders (
  id                     BIGSERIAL PRIMARY KEY,
  owner_id               BIGINT NOT NULL REFERENCES owner_accounts(id),
  plan_id                BIGINT NOT NULL REFERENCES subscription_plans(id),
  tran_id                VARCHAR(64) NOT NULL UNIQUE,
  amount_minor           BIGINT NOT NULL CHECK (amount_minor >= 0),
  currency               CHAR(3) NOT NULL REFERENCES currencies(code),
  status                 VARCHAR(32) NOT NULL DEFAULT 'PENDING',  -- PENDING | PAID | FAILED | CANCELLED
  payment_gateway        VARCHAR(32) NOT NULL DEFAULT 'ABA_PAYWAY',
  gateway_tran_id        VARCHAR(100),
  gateway_response_json  TEXT,
  payway_hash            TEXT,
  req_time               VARCHAR(32),
  paid_at                TIMESTAMPTZ,
  created_at             TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_sub_orders_owner ON subscription_orders(owner_id);
CREATE INDEX idx_sub_orders_tran_id ON subscription_orders(tran_id);
