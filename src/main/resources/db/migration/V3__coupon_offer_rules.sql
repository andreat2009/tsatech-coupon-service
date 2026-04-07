ALTER TABLE coupon_service_coupon
    ADD COLUMN IF NOT EXISTS offer_scope VARCHAR(16) NOT NULL DEFAULT 'ORDER',
    ADD COLUMN IF NOT EXISTS auto_apply BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS stackable BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS customer_group_code VARCHAR(64),
    ADD COLUMN IF NOT EXISTS priority INT NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_coupon_service_coupon_scope_priority
    ON coupon_service_coupon(active, auto_apply, offer_scope, priority);
