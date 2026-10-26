CREATE TABLE users (
    id UUID PRIMARY KEY,
    full_name VARCHAR(200) NOT NULL,
    email VARCHAR(320) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    role VARCHAR(30) NOT NULL CHECK (role IN ('ADMIN', 'INVOICE_MANAGER', 'VIEWER')),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_users_email UNIQUE (email)
);

CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    token_family UUID NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    replaced_by_token_id UUID REFERENCES refresh_tokens(id),
    created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_family ON refresh_tokens(token_family);

CREATE TABLE clients (
    id UUID PRIMARY KEY,
    client_code VARCHAR(30) NOT NULL UNIQUE,
    client_name VARCHAR(200) NOT NULL,
    phone_number VARCHAR(40), address TEXT, email VARCHAR(320), ice VARCHAR(30),
    active BOOLEAN NOT NULL DEFAULT TRUE, version BIGINT NOT NULL DEFAULT 0,
    created_by UUID REFERENCES users(id), updated_by UUID REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_clients_search ON clients(client_code, client_name, phone_number, ice);

CREATE TABLE products (
    id UUID PRIMARY KEY,
    product_code VARCHAR(30) NOT NULL UNIQUE,
    product_name VARCHAR(200) NOT NULL, description TEXT,
    unit_price_excluding_tax NUMERIC(19,2) NOT NULL CHECK (unit_price_excluding_tax >= 0),
    default_vat_rate NUMERIC(5,2) NOT NULL CHECK (default_vat_rate BETWEEN 0 AND 100),
    unit VARCHAR(20) NOT NULL CHECK (unit IN ('PIECE','KG','METER','ROLL','LITER','SERVICE','OTHER')),
    active BOOLEAN NOT NULL DEFAULT TRUE, version BIGINT NOT NULL DEFAULT 0,
    created_by UUID REFERENCES users(id), updated_by UUID REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_products_search ON products(product_code, product_name);

CREATE TABLE contracts (
    id UUID PRIMARY KEY, contract_code VARCHAR(30) NOT NULL UNIQUE,
    client_id UUID NOT NULL REFERENCES clients(id) ON DELETE RESTRICT,
    start_date DATE NOT NULL, end_date DATE,
    payment_method VARCHAR(100) NOT NULL,
    default_discount_rate NUMERIC(5,2) NOT NULL DEFAULT 0 CHECK (default_discount_rate BETWEEN 0 AND 100),
    status VARCHAR(20) NOT NULL CHECK (status IN ('DRAFT','ACTIVE','EXPIRED','CANCELLED')),
    notes TEXT, version BIGINT NOT NULL DEFAULT 0,
    created_by UUID REFERENCES users(id), updated_by UUID REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL,
    CHECK (end_date IS NULL OR end_date >= start_date)
);
CREATE INDEX idx_contracts_client_status ON contracts(client_id, status);

CREATE TABLE contract_lines (
    id UUID PRIMARY KEY, contract_id UUID NOT NULL REFERENCES contracts(id) ON DELETE CASCADE,
    product_id UUID NOT NULL REFERENCES products(id) ON DELETE RESTRICT,
    negotiated_unit_price_excluding_tax NUMERIC(19,2) NOT NULL CHECK (negotiated_unit_price_excluding_tax >= 0),
    vat_rate NUMERIC(5,2) NOT NULL CHECK (vat_rate BETWEEN 0 AND 100),
    discount_rate NUMERIC(5,2) NOT NULL CHECK (discount_rate BETWEEN 0 AND 100),
    minimum_quantity NUMERIC(19,3) NOT NULL CHECK (minimum_quantity > 0),
    UNIQUE(contract_id, product_id)
);

CREATE TABLE invoices (
    id UUID PRIMARY KEY, invoice_number VARCHAR(30) UNIQUE, invoice_date DATE NOT NULL,
    client_id UUID NOT NULL REFERENCES clients(id) ON DELETE RESTRICT,
    contract_id UUID REFERENCES contracts(id) ON DELETE RESTRICT,
    created_by_user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    validator_user_id UUID REFERENCES users(id) ON DELETE RESTRICT,
    customer_order_reference VARCHAR(100), payment_method VARCHAR(100) NOT NULL,
    subtotal_excluding_tax NUMERIC(19,2) NOT NULL DEFAULT 0,
    total_discount NUMERIC(19,2) NOT NULL DEFAULT 0,
    taxable_amount NUMERIC(19,2) NOT NULL DEFAULT 0,
    total_vat NUMERIC(19,2) NOT NULL DEFAULT 0,
    total_including_tax NUMERIC(19,2) NOT NULL DEFAULT 0,
    amount_in_words TEXT, status VARCHAR(20) NOT NULL CHECK (status IN ('DRAFT','VALIDATED','PAID','CANCELLED')),
    pdf_file_path TEXT,
    client_code_snapshot VARCHAR(30), client_name_snapshot VARCHAR(200), client_address_snapshot TEXT,
    client_phone_snapshot VARCHAR(40), client_ice_snapshot VARCHAR(30), payment_method_snapshot VARCHAR(100),
    version BIGINT NOT NULL DEFAULT 0, updated_by UUID REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL,
    validated_at TIMESTAMPTZ, cancelled_at TIMESTAMPTZ
);
CREATE INDEX idx_invoices_client_date ON invoices(client_id, invoice_date DESC);
CREATE INDEX idx_invoices_status ON invoices(status);

CREATE TABLE invoice_lines (
    id UUID PRIMARY KEY, invoice_id UUID NOT NULL REFERENCES invoices(id) ON DELETE CASCADE,
    product_id UUID NOT NULL REFERENCES products(id) ON DELETE RESTRICT,
    product_code_snapshot VARCHAR(30), product_name_snapshot VARCHAR(200),
    product_description_snapshot TEXT, unit_snapshot VARCHAR(20),
    quantity NUMERIC(19,3) NOT NULL CHECK (quantity > 0),
    unit_price_excluding_tax NUMERIC(19,2) NOT NULL CHECK (unit_price_excluding_tax >= 0),
    vat_rate NUMERIC(5,2) NOT NULL CHECK (vat_rate BETWEEN 0 AND 100),
    discount_rate NUMERIC(5,2) NOT NULL CHECK (discount_rate BETWEEN 0 AND 100),
    gross_amount_excluding_tax NUMERIC(19,2) NOT NULL,
    discount_amount NUMERIC(19,2) NOT NULL, net_amount_excluding_tax NUMERIC(19,2) NOT NULL,
    vat_amount NUMERIC(19,2) NOT NULL, total_including_tax NUMERIC(19,2) NOT NULL
);
CREATE INDEX idx_invoice_lines_invoice_id ON invoice_lines(invoice_id);

CREATE TABLE invoice_sequences (
    sequence_year INTEGER PRIMARY KEY CHECK (sequence_year >= 2000),
    last_value BIGINT NOT NULL CHECK (last_value >= 0), updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE company_settings (
    id UUID PRIMARY KEY, company_name VARCHAR(200) NOT NULL, address TEXT NOT NULL,
    phone VARCHAR(40) NOT NULL, email VARCHAR(320) NOT NULL, ice VARCHAR(30) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0, updated_by UUID REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL
);

