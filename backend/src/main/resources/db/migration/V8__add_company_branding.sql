CREATE TABLE company_branding (
    company_id UUID PRIMARY KEY,
    logo_content_type VARCHAR(30) NOT NULL,
    logo_data BYTEA NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_company_branding_company
        FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE CASCADE,
    CONSTRAINT ck_company_branding_content_type
        CHECK (logo_content_type IN ('image/png', 'image/jpeg')),
    CONSTRAINT ck_company_branding_logo_size
        CHECK (OCTET_LENGTH(logo_data) BETWEEN 1 AND 524288)
);
