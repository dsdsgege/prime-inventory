-- ==========================================
-- Prime Mobile Inventory - V2 Migration
-- Add mandatory expected margin (%) and expected sale price (HUF) on receiving
-- ==========================================

ALTER TABLE inventory_items
    ADD COLUMN IF NOT EXISTS expected_margin_percent DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS expected_sale_price BIGINT;
