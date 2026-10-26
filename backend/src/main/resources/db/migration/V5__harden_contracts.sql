CREATE TABLE contract_sequences (
    sequence_year INTEGER PRIMARY KEY CHECK (sequence_year >= 2000),
    last_value BIGINT NOT NULL CHECK (last_value >= 0),
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0)
);

INSERT INTO contract_sequences(sequence_year, last_value, version)
SELECT code_year, MAX(code_value), 0
FROM (
    SELECT SUBSTRING(contract_code FROM '^CTR-([0-9]{4})-[0-9]{6,}$')::INTEGER AS code_year,
           SUBSTRING(contract_code FROM '^CTR-[0-9]{4}-([0-9]{6,})$')::BIGINT AS code_value
    FROM contracts
    WHERE contract_code ~ '^CTR-[0-9]{4}-[0-9]{6,}$'
) existing_codes
GROUP BY code_year;

ALTER TABLE contracts ALTER COLUMN contract_code TYPE VARCHAR(40);
ALTER TABLE contracts ALTER COLUMN payment_method TYPE VARCHAR(30);
ALTER TABLE contracts ALTER COLUMN notes TYPE VARCHAR(2000);
ALTER TABLE contracts ADD COLUMN activated_at TIMESTAMPTZ;
ALTER TABLE contracts ADD COLUMN activated_by UUID REFERENCES users(id) ON DELETE RESTRICT;
ALTER TABLE contracts ADD COLUMN cancelled_at TIMESTAMPTZ;
ALTER TABLE contracts ADD COLUMN cancelled_by UUID REFERENCES users(id) ON DELETE RESTRICT;
ALTER TABLE contracts ADD COLUMN cancellation_reason VARCHAR(500);

DO $$
DECLARE audit_user UUID;
BEGIN
    IF EXISTS (SELECT 1 FROM contracts WHERE created_by IS NULL OR updated_by IS NULL) THEN
        SELECT id INTO audit_user FROM users ORDER BY created_at, id LIMIT 1;
        IF audit_user IS NULL THEN
            RAISE EXCEPTION 'Cannot backfill contract audit columns because no user exists';
        END IF;
        UPDATE contracts SET created_by = COALESCE(created_by, audit_user),
               updated_by = COALESCE(updated_by, created_by, audit_user)
         WHERE created_by IS NULL OR updated_by IS NULL;
    END IF;
END $$;

ALTER TABLE contracts ALTER COLUMN created_by SET NOT NULL;
ALTER TABLE contracts ALTER COLUMN updated_by SET NOT NULL;

ALTER TABLE contract_lines ALTER COLUMN minimum_quantity DROP NOT NULL;
ALTER TABLE contract_lines ADD COLUMN notes VARCHAR(1000);
ALTER TABLE contract_lines ADD COLUMN created_at TIMESTAMPTZ;
ALTER TABLE contract_lines ADD COLUMN updated_at TIMESTAMPTZ;
ALTER TABLE contract_lines ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

UPDATE contract_lines line SET created_at = contract.created_at, updated_at = contract.updated_at
FROM contracts contract WHERE contract.id = line.contract_id
AND (line.created_at IS NULL OR line.updated_at IS NULL);

ALTER TABLE contract_lines ALTER COLUMN created_at SET NOT NULL;
ALTER TABLE contract_lines ALTER COLUMN updated_at SET NOT NULL;

ALTER TABLE contracts ADD CONSTRAINT ck_contracts_payment_method CHECK (payment_method IN ('CASH','CHECK','BANK_TRANSFER','CARD','CREDIT','OTHER'));
ALTER TABLE contracts ADD CONSTRAINT ck_contracts_default_discount_range CHECK (default_discount_rate BETWEEN 0 AND 100);
ALTER TABLE contracts ADD CONSTRAINT ck_contracts_date_range CHECK (end_date IS NULL OR start_date <= end_date);
ALTER TABLE contracts ADD CONSTRAINT ck_contracts_status_supported CHECK (status IN ('DRAFT','ACTIVE','EXPIRED','CANCELLED'));
ALTER TABLE contracts ADD CONSTRAINT ck_contracts_cancellation_reason CHECK (cancellation_reason IS NULL OR BTRIM(cancellation_reason) <> '');
ALTER TABLE contract_lines ADD CONSTRAINT ck_contract_lines_price_nonnegative CHECK (negotiated_unit_price_excluding_tax >= 0);
ALTER TABLE contract_lines ADD CONSTRAINT ck_contract_lines_vat_range CHECK (vat_rate BETWEEN 0 AND 100);
ALTER TABLE contract_lines ADD CONSTRAINT ck_contract_lines_discount_range CHECK (discount_rate BETWEEN 0 AND 100);
ALTER TABLE contract_lines ADD CONSTRAINT ck_contract_lines_minimum_quantity CHECK (minimum_quantity IS NULL OR minimum_quantity > 0);

CREATE INDEX idx_contracts_contract_code ON contracts(contract_code);
CREATE INDEX idx_contracts_client_id ON contracts(client_id);
CREATE INDEX idx_contracts_status ON contracts(status);
CREATE INDEX idx_contracts_start_date ON contracts(start_date);
CREATE INDEX idx_contracts_end_date ON contracts(end_date);
CREATE INDEX idx_contracts_payment_method ON contracts(payment_method);
CREATE INDEX idx_contracts_created_at ON contracts(created_at);
CREATE INDEX idx_contract_lines_contract_id ON contract_lines(contract_id);
CREATE INDEX idx_contract_lines_product_id ON contract_lines(product_id);
