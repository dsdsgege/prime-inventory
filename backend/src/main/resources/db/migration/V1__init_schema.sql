-- ==========================================
-- Prime Mobile Inventory - Initial Schema (V1)
-- Faithful 3-Stage Retail Lifecycle + Audit Logs + Batch Excel Sales
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

-- Seed initial admin user (BCrypt password: 'admin')
INSERT INTO users (username, password_hash, role, full_name, is_active)
VALUES ('admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAf2801i', 'ROLE_ADMIN', 'Rendszeradminisztrátor', TRUE)
ON CONFLICT (username) DO NOTHING;

-- 2. Suppliers
CREATE TABLE suppliers (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    tax_number VARCHAR(50) NOT NULL UNIQUE,
    eu_tax_number VARCHAR(50),
    address VARCHAR(500),
    bank_account VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_suppliers_name ON suppliers(name);
CREATE INDEX idx_suppliers_tax ON suppliers(tax_number);

-- 3. Invoices
CREATE TABLE invoices (
    id BIGSERIAL PRIMARY KEY,
    supplier_id BIGINT NOT NULL REFERENCES suppliers(id) ON DELETE RESTRICT,
    invoice_number VARCHAR(100) NOT NULL UNIQUE,
    issue_date DATE,
    delivery_date DATE,
    due_date DATE,
    payment_method VARCHAR(50),
    total_net_amount BIGINT,
    total_vat_amount BIGINT,
    total_gross_amount BIGINT,
    pdf_filename VARCHAR(255) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE', -- 'ACTIVE', 'COMPLETED'
    uploaded_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_invoices_supplier ON invoices(supplier_id);
CREATE INDEX idx_invoices_number ON invoices(invoice_number);
CREATE INDEX idx_invoices_status ON invoices(status);

-- 4. Inventory Items (3-Stage Retail States: ORDERED -> PARTIAL -> RECEIVED)
CREATE TABLE inventory_items (
    id BIGSERIAL PRIMARY KEY,
    invoice_id BIGINT NOT NULL REFERENCES invoices(id) ON DELETE CASCADE,
    supplier_id BIGINT NOT NULL REFERENCES suppliers(id) ON DELETE RESTRICT,
    line_index INT NOT NULL DEFAULT 1,
    product_name VARCHAR(255) NOT NULL,
    raw_description TEXT,
    data_wipe_codes TEXT, -- JSON array of NMHH wipe code strings
    unit VARCHAR(20) NOT NULL DEFAULT 'db',
    initial_quantity INT NOT NULL DEFAULT 1,
    received_quantity INT NOT NULL DEFAULT 0,
    sold_quantity INT NOT NULL DEFAULT 0,
    current_quantity INT NOT NULL DEFAULT 0,
    unit_price_net BIGINT NOT NULL DEFAULT 0,
    vat_rate VARCHAR(20) NOT NULL DEFAULT 'K.AFA',
    total_net BIGINT NOT NULL DEFAULT 0,
    total_vat BIGINT NOT NULL DEFAULT 0,
    total_gross BIGINT NOT NULL DEFAULT 0,
    status VARCHAR(30) NOT NULL DEFAULT 'ORDERED', -- 'ORDERED', 'PARTIAL', 'RECEIVED'
    received_at TIMESTAMP WITH TIME ZONE,
    is_fully_deducted BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX idx_items_invoice ON inventory_items(invoice_id);
CREATE INDEX idx_items_supplier ON inventory_items(supplier_id);
CREATE INDEX idx_items_product ON inventory_items(product_name);
CREATE INDEX idx_items_status ON inventory_items(status);

-- 5. Sales (Retail POS & Margin Realization + Excel Imported Sales)
CREATE TABLE sales (
    id BIGSERIAL PRIMARY KEY,
    inventory_item_id BIGINT REFERENCES inventory_items(id) ON DELETE CASCADE,
    invoice_id BIGINT REFERENCES invoices(id) ON DELETE CASCADE,
    supplier_id BIGINT REFERENCES suppliers(id) ON DELETE RESTRICT,
    order_number VARCHAR(100),
    quantity INT NOT NULL DEFAULT 1,
    unit_cost_net BIGINT NOT NULL DEFAULT 0,
    unit_cost_gross BIGINT NOT NULL DEFAULT 0,
    sale_price BIGINT NOT NULL DEFAULT 0, -- Total gross retail price (HUF)
    profit BIGINT NOT NULL DEFAULT 0,     -- sale_price - (unit_cost_gross * quantity)
    profit_net BIGINT NOT NULL DEFAULT 0,
    used_wipe_code VARCHAR(100),
    customer_name VARCHAR(255),
    note TEXT,
    source VARCHAR(30) NOT NULL DEFAULT 'POS', -- 'POS', 'EXCEL_IMPORT'
    sold_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_sales_item ON sales(inventory_item_id);
CREATE INDEX idx_sales_invoice ON sales(invoice_id);
CREATE INDEX idx_sales_supplier ON sales(supplier_id);
CREATE INDEX idx_sales_sold_at ON sales(sold_at);

-- 6. Transaction Logs (Full Audit Trail)
CREATE TABLE transaction_logs (
    id BIGSERIAL PRIMARY KEY,
    inventory_item_id BIGINT NOT NULL REFERENCES inventory_items(id) ON DELETE CASCADE,
    invoice_id BIGINT NOT NULL REFERENCES invoices(id) ON DELETE CASCADE,
    supplier_id BIGINT NOT NULL REFERENCES suppliers(id) ON DELETE RESTRICT,
    action_type VARCHAR(40) NOT NULL, -- 'INITIAL_IMPORT', 'STOCK_RECEIVE', 'STOCK_RECEIVE_CANCEL', 'SALE', 'SALE_CANCEL', 'DEDUCT', 'RESTORE'
    quantity_changed INT NOT NULL,
    quantity_before INT NOT NULL,
    quantity_after INT NOT NULL,
    used_wipe_code VARCHAR(100),
    note TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_logs_item ON transaction_logs(inventory_item_id);
CREATE INDEX idx_logs_invoice ON transaction_logs(invoice_id);
CREATE INDEX idx_logs_supplier ON transaction_logs(supplier_id);
CREATE INDEX idx_logs_action ON transaction_logs(action_type);
CREATE INDEX idx_logs_created_at ON transaction_logs(created_at);
