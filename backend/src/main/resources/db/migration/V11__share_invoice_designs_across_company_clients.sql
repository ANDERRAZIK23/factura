CREATE TABLE company_invoice_designs (
    company_id UUID PRIMARY KEY REFERENCES companies(id) ON DELETE CASCADE,
    settings JSONB NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    updated_by UUID NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_company_invoice_design_settings CHECK (jsonb_typeof(settings) = 'object'),
    CONSTRAINT ck_company_invoice_design_version CHECK (version >= 0)
);

-- Promote the latest saved client design to the shared company model.
-- Keep the original client records for recovery; they are no longer used at runtime.
INSERT INTO company_invoice_designs (company_id, settings, version, updated_by, updated_at)
SELECT DISTINCT ON (company_id) company_id, settings, version, updated_by, updated_at
FROM client_invoice_designs
ORDER BY company_id, updated_at DESC, client_id;
