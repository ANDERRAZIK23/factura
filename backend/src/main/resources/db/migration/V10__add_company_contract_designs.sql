CREATE TABLE company_contract_designs (
    company_id UUID PRIMARY KEY REFERENCES companies(id) ON DELETE CASCADE,
    settings JSONB NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    updated_by UUID NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_contract_design_settings CHECK (jsonb_typeof(settings) = 'object'),
    CONSTRAINT ck_contract_design_version CHECK (version >= 0)
);
