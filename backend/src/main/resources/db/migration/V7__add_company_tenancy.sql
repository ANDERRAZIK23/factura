CREATE SEQUENCE company_code_sequence AS BIGINT START WITH 2 INCREMENT BY 1 NO CYCLE;

CREATE TABLE companies (
    id UUID PRIMARY KEY,
    company_code VARCHAR(30) NOT NULL,
    company_name VARCHAR(200) NOT NULL,
    address VARCHAR(500),
    phone VARCHAR(40),
    email VARCHAR(254),
    ice VARCHAR(15),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_companies_code UNIQUE (company_code),
    CONSTRAINT ck_companies_code_format CHECK (company_code ~ '^ENT-[0-9]{6,}$'),
    CONSTRAINT ck_companies_name_not_blank CHECK (BTRIM(company_name) <> ''),
    CONSTRAINT ck_companies_ice_format CHECK (ice IS NULL OR ice ~ '^[0-9]{15}$')
);
CREATE UNIQUE INDEX uq_companies_ice_not_null ON companies(ice) WHERE ice IS NOT NULL;
CREATE INDEX idx_companies_name ON companies(company_name);
CREATE INDEX idx_companies_enabled ON companies(enabled);

INSERT INTO companies (id, company_code, company_name, address, phone, email, ice, enabled, created_at, updated_at)
VALUES ('00000000-0000-0000-0000-000000000001', 'ENT-000001', 'My Company', NULL, NULL, NULL, NULL, TRUE, NOW(), NOW());

ALTER TABLE users ADD COLUMN company_id UUID;
UPDATE users SET company_id = '00000000-0000-0000-0000-000000000001';
ALTER TABLE users ALTER COLUMN company_id SET NOT NULL;
ALTER TABLE users ADD CONSTRAINT fk_users_company FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE RESTRICT;
CREATE INDEX idx_users_company ON users(company_id);

ALTER TABLE users DROP CONSTRAINT IF EXISTS users_role_check;
ALTER TABLE users ADD CONSTRAINT ck_users_role_supported
    CHECK (role IN ('APP_ADMIN', 'ADMIN', 'INVOICE_MANAGER', 'VIEWER'));
UPDATE users SET role = 'APP_ADMIN'
 WHERE id = (SELECT id FROM users WHERE role = 'ADMIN' ORDER BY created_at, id LIMIT 1);

ALTER TABLE clients ADD COLUMN company_id UUID;
UPDATE clients SET company_id = '00000000-0000-0000-0000-000000000001';
ALTER TABLE clients ALTER COLUMN company_id SET NOT NULL;
ALTER TABLE clients ADD CONSTRAINT fk_clients_company FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE RESTRICT;
CREATE INDEX idx_clients_company ON clients(company_id);
DROP INDEX IF EXISTS uq_clients_ice_not_null;
CREATE UNIQUE INDEX uq_clients_company_ice_not_null ON clients(company_id, ice) WHERE ice IS NOT NULL;

ALTER TABLE products ADD COLUMN company_id UUID;
UPDATE products SET company_id = '00000000-0000-0000-0000-000000000001';
ALTER TABLE products ALTER COLUMN company_id SET NOT NULL;
ALTER TABLE products ADD CONSTRAINT fk_products_company FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE RESTRICT;
CREATE INDEX idx_products_company ON products(company_id);

ALTER TABLE contracts ADD COLUMN company_id UUID;
UPDATE contracts c SET company_id = cl.company_id FROM clients cl WHERE cl.id = c.client_id;
ALTER TABLE contracts ALTER COLUMN company_id SET NOT NULL;
ALTER TABLE contracts ADD CONSTRAINT fk_contracts_company FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE RESTRICT;
CREATE INDEX idx_contracts_company ON contracts(company_id);

ALTER TABLE invoices ADD COLUMN company_id UUID;
UPDATE invoices i SET company_id = cl.company_id FROM clients cl WHERE cl.id = i.client_id;
ALTER TABLE invoices ALTER COLUMN company_id SET NOT NULL;
ALTER TABLE invoices ADD CONSTRAINT fk_invoices_company FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE RESTRICT;
CREATE INDEX idx_invoices_company ON invoices(company_id);

ALTER TABLE invoices DROP CONSTRAINT IF EXISTS invoices_invoice_number_key;
ALTER TABLE invoices ADD CONSTRAINT uq_invoices_company_number UNIQUE (company_id, invoice_number);

ALTER TABLE invoice_sequences ADD COLUMN company_id UUID;
UPDATE invoice_sequences SET company_id = '00000000-0000-0000-0000-000000000001';
ALTER TABLE invoice_sequences ALTER COLUMN company_id SET NOT NULL;
ALTER TABLE invoice_sequences ADD CONSTRAINT fk_invoice_sequences_company FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE RESTRICT;
ALTER TABLE invoice_sequences DROP CONSTRAINT invoice_sequences_pkey;
ALTER TABLE invoice_sequences ADD CONSTRAINT invoice_sequences_pkey PRIMARY KEY (company_id, sequence_year);
