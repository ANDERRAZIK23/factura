CREATE SEQUENCE product_code_sequence AS BIGINT START WITH 1 INCREMENT BY 1 NO CYCLE;

DO $$
DECLARE
    highest_existing_code BIGINT;
BEGIN
    SELECT COALESCE(MAX(SUBSTRING(product_code FROM '^PRD-([0-9]+)$')::BIGINT), 0)
      INTO highest_existing_code
      FROM products
     WHERE product_code ~ '^PRD-[0-9]{6,}$';
    IF highest_existing_code > 0 THEN
        PERFORM setval('product_code_sequence', highest_existing_code, true);
    END IF;
END $$;

ALTER TABLE products ALTER COLUMN product_code TYPE VARCHAR(30);
ALTER TABLE products ALTER COLUMN description TYPE VARCHAR(1000);

DO $$
DECLARE
    audit_user UUID;
BEGIN
    IF EXISTS (SELECT 1 FROM products WHERE created_by IS NULL OR updated_by IS NULL) THEN
        SELECT id INTO audit_user FROM users ORDER BY created_at, id LIMIT 1;
        IF audit_user IS NULL THEN
            RAISE EXCEPTION 'Cannot backfill product audit columns because no user exists';
        END IF;
        UPDATE products
           SET created_by = COALESCE(created_by, audit_user),
               updated_by = COALESCE(updated_by, created_by, audit_user)
         WHERE created_by IS NULL OR updated_by IS NULL;
    END IF;
END $$;

ALTER TABLE products ALTER COLUMN created_by SET NOT NULL;
ALTER TABLE products ALTER COLUMN updated_by SET NOT NULL;

ALTER TABLE products ADD CONSTRAINT ck_products_code_format
    CHECK (product_code ~ '^PRD-[0-9]{6,}$');
ALTER TABLE products ADD CONSTRAINT ck_products_name_not_blank
    CHECK (BTRIM(product_name) <> '');
ALTER TABLE products ADD CONSTRAINT ck_products_price_nonnegative
    CHECK (unit_price_excluding_tax >= 0);
ALTER TABLE products ADD CONSTRAINT ck_products_vat_range
    CHECK (default_vat_rate BETWEEN 0 AND 100);
ALTER TABLE products ADD CONSTRAINT ck_products_unit_supported
    CHECK (unit IN ('PIECE','KG','METER','ROLL','LITER','SERVICE','OTHER'));

CREATE INDEX idx_products_product_name ON products(product_name);
CREATE INDEX idx_products_unit ON products(unit);
CREATE INDEX idx_products_active ON products(active);
CREATE INDEX idx_products_created_at ON products(created_at);
