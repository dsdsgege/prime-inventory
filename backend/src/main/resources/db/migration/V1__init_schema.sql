-- ==========================================
-- Prime Mobile Inventory - Initial Schema (V1)
-- ==========================================

-- 1. Users table (Spring Security)
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL DEFAULT 'ROLE_USER',
    full_name VARCHAR(150),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Seed initial admin user (BCrypt password: 'admin', change upon first login)
-- Hash generated using standard BCrypt 10 rounds for 'admin': $2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAf2801i
INSERT INTO users (username, password_hash, role, full_name, is_active)
VALUES ('admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAf2801i', 'ROLE_ADMIN', 'Rendszeradminisztrátor', TRUE)
ON CONFLICT (username) DO NOTHING;

-- 2. Suppliers
CREATE TABLE suppliers (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    tax_number VARCHAR(50),
    eu_tax_number VARCHAR(50),
    address TEXT,
    bank_account VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_suppliers_name ON suppliers(name);

-- 3. Invoices
CREATE TABLE invoices (
    id BIGSERIAL PRIMARY KEY,
    supplier_id BIGINT REFERENCES suppliers(id) ON DELETE SET NULL,
    invoice_number VARCHAR(100) NOT NULL UNIQUE,
    issue_date DATE,
    delivery_date DATE,
    due_date DATE,
    payment_method VARCHAR(100),
    total_net_amount BIGINT NOT NULL DEFAULT 0,
    total_vat_amount BIGINT NOT NULL DEFAULT 0,
    total_gross_amount BIGINT NOT NULL DEFAULT 0,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    raw_pdf_path VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_invoices_number ON invoices(invoice_number);
CREATE INDEX idx_invoices_status ON invoices(status);

-- 4. Invoice Items (Original incoming invoice lines)
CREATE TABLE invoice_items (
    id BIGSERIAL PRIMARY KEY,
    invoice_id BIGINT NOT NULL REFERENCES invoices(id) ON DELETE CASCADE,
    line_index INT NOT NULL DEFAULT 1,
    product_name VARCHAR(255) NOT NULL,
    raw_description TEXT,
    unit VARCHAR(20) NOT NULL DEFAULT 'db',
    initial_quantity INT NOT NULL DEFAULT 1,
    current_quantity INT NOT NULL DEFAULT 1,
    unit_price_net BIGINT NOT NULL DEFAULT 0,
    vat_rate VARCHAR(50) NOT NULL DEFAULT 'K.AFA',
    total_net BIGINT NOT NULL DEFAULT 0,
    total_vat BIGINT NOT NULL DEFAULT 0,
    total_gross BIGINT NOT NULL DEFAULT 0,
    is_fully_deducted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_invoice_items_invoice ON invoice_items(invoice_id);
CREATE INDEX idx_invoice_items_product ON invoice_items(product_name);

-- 5. NMHH Data Wipe Codes
CREATE TABLE nmhh_wipe_codes (
    id BIGSERIAL PRIMARY KEY,
    invoice_item_id BIGINT REFERENCES invoice_items(id) ON DELETE CASCADE,
    wipe_code VARCHAR(100) NOT NULL,
    is_used BOOLEAN NOT NULL DEFAULT FALSE,
    used_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_wipe_codes_item ON nmhh_wipe_codes(invoice_item_id);
CREATE INDEX idx_wipe_codes_code ON nmhh_wipe_codes(wipe_code);

-- 6. Physical Inventory Items (Tracked in stock)
-- Lifecycle statuses: RECEIVED (on invoice, arrived in store) -> IN_STOCK (available for sale) -> SOLD -> RETURNED
CREATE TABLE inventory_items (
    id BIGSERIAL PRIMARY KEY,
    invoice_item_id BIGINT REFERENCES invoice_items(id) ON DELETE SET NULL,
    product_name VARCHAR(255) NOT NULL,
    serial_number VARCHAR(100),
    wipe_code VARCHAR(100),
    acquisition_net_price BIGINT NOT NULL DEFAULT 0,
    acquisition_gross_price BIGINT NOT NULL DEFAULT 0,
    received_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(50) NOT NULL DEFAULT 'IN_STOCK',
    selling_price_net BIGINT,
    selling_price_gross BIGINT,
    sold_at TIMESTAMP WITH TIME ZONE,
    notes TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_inventory_status ON inventory_items(status);
CREATE INDEX idx_inventory_product ON inventory_items(product_name);

-- 7. Sales Orders (Webshop or Store Batch Imports)
CREATE TABLE sales_orders (
    id BIGSERIAL PRIMARY KEY,
    order_number VARCHAR(100) NOT NULL,
    order_date DATE,
    customer_name VARCHAR(255),
    total_selling_price_gross BIGINT NOT NULL DEFAULT 0,
    total_cost_price_gross BIGINT NOT NULL DEFAULT 0,
    total_profit_gross BIGINT NOT NULL DEFAULT 0,
    total_profit_net BIGINT NOT NULL DEFAULT 0,
    source VARCHAR(50) NOT NULL DEFAULT 'MANUAL', -- 'EXCEL_IMPORT', 'MANUAL_POS'
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_sales_orders_number ON sales_orders(order_number);
CREATE INDEX idx_sales_orders_date ON sales_orders(order_date);

-- 8. Sale Items (Individual sales line items tied to inventory)
CREATE TABLE sale_items (
    id BIGSERIAL PRIMARY KEY,
    sales_order_id BIGINT REFERENCES sales_orders(id) ON DELETE CASCADE,
    inventory_item_id BIGINT REFERENCES inventory_items(id) ON DELETE SET NULL,
    product_name VARCHAR(255) NOT NULL,
    quantity INT NOT NULL DEFAULT 1,
    selling_price_gross BIGINT NOT NULL DEFAULT 0,
    cost_price_gross BIGINT NOT NULL DEFAULT 0,
    profit_gross BIGINT NOT NULL DEFAULT 0,
    profit_net BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_sale_items_order ON sale_items(sales_order_id);
CREATE INDEX idx_sale_items_inventory ON sale_items(inventory_item_id);
