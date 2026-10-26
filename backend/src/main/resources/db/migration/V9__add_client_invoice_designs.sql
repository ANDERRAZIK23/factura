ALTER TABLE clients ADD CONSTRAINT uq_clients_id_company UNIQUE (id, company_id);

CREATE TABLE client_invoice_designs (
    client_id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    settings JSONB NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    updated_by UUID NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_invoice_design_client FOREIGN KEY (client_id, company_id)
        REFERENCES clients(id, company_id) ON DELETE CASCADE,
    CONSTRAINT ck_invoice_design_settings CHECK (jsonb_typeof(settings) = 'object'),
    CONSTRAINT ck_invoice_design_version CHECK (version >= 0)
);
CREATE INDEX idx_invoice_designs_company ON client_invoice_designs(company_id);
