CREATE SEQUENCE client_code_sequence AS BIGINT START WITH 1 INCREMENT BY 1 NO CYCLE;

DO $$
DECLARE
    highest_existing_code BIGINT;
BEGIN
    SELECT COALESCE(MAX(SUBSTRING(client_code FROM '^CLI-([0-9]{6})$')::BIGINT), 0)
      INTO highest_existing_code
      FROM clients
     WHERE client_code ~ '^CLI-[0-9]{6}$';
    IF highest_existing_code > 0 THEN
        PERFORM setval('client_code_sequence', highest_existing_code, true);
    END IF;
END $$;

ALTER TABLE clients ALTER COLUMN client_code TYPE VARCHAR(20);
ALTER TABLE clients ALTER COLUMN phone_number TYPE VARCHAR(30);
ALTER TABLE clients ALTER COLUMN address TYPE VARCHAR(500);
ALTER TABLE clients ALTER COLUMN email TYPE VARCHAR(254);
ALTER TABLE clients ALTER COLUMN ice TYPE VARCHAR(15);
ALTER TABLE clients ALTER COLUMN created_by SET NOT NULL;
ALTER TABLE clients ALTER COLUMN updated_by SET NOT NULL;

ALTER TABLE clients ADD CONSTRAINT ck_clients_code_format
    CHECK (client_code ~ '^CLI-[0-9]{6}$');
ALTER TABLE clients ADD CONSTRAINT ck_clients_name_not_blank
    CHECK (BTRIM(client_name) <> '');
ALTER TABLE clients ADD CONSTRAINT ck_clients_ice_format
    CHECK (ice IS NULL OR ice ~ '^[0-9]{15}$');

CREATE INDEX idx_clients_client_name ON clients(client_name);
CREATE INDEX idx_clients_ice ON clients(ice);
CREATE INDEX idx_clients_phone_number ON clients(phone_number);
CREATE INDEX idx_clients_email ON clients(email);
CREATE INDEX idx_clients_active ON clients(active);
CREATE UNIQUE INDEX uq_clients_ice_not_null ON clients(ice) WHERE ice IS NOT NULL;
