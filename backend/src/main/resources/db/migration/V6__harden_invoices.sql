UPDATE invoices SET updated_by = created_by_user_id WHERE updated_by IS NULL;
ALTER TABLE invoices ALTER COLUMN updated_by SET NOT NULL;

ALTER TABLE invoices ADD CONSTRAINT ck_invoices_payment_method_supported
    CHECK (payment_method IN ('CASH','CHECK','BANK_TRANSFER','CARD','CREDIT','OTHER'));
ALTER TABLE invoices ADD CONSTRAINT ck_invoices_financial_amounts_nonnegative
    CHECK (subtotal_excluding_tax >= 0 AND total_discount >= 0 AND taxable_amount >= 0
        AND total_vat >= 0 AND total_including_tax >= 0);
ALTER TABLE invoices ADD CONSTRAINT ck_invoices_financial_totals_consistent
    CHECK (total_discount <= subtotal_excluding_tax
        AND taxable_amount = subtotal_excluding_tax - total_discount
        AND total_including_tax = taxable_amount + total_vat);
ALTER TABLE invoices ADD CONSTRAINT ck_invoices_number_format
    CHECK (invoice_number IS NULL OR invoice_number ~ '^FA-[0-9]{4}-[0-9]{6,}$');
ALTER TABLE invoices ADD CONSTRAINT ck_invoices_number_lifecycle
    CHECK ((status = 'DRAFT' AND invoice_number IS NULL AND validated_at IS NULL AND cancelled_at IS NULL)
        OR (status IN ('VALIDATED','PAID') AND invoice_number IS NOT NULL AND validated_at IS NOT NULL AND cancelled_at IS NULL)
        OR (status = 'CANCELLED' AND cancelled_at IS NOT NULL));
ALTER TABLE invoices ADD CONSTRAINT ck_invoices_validation_snapshots
    CHECK (invoice_number IS NULL OR (validator_user_id IS NOT NULL AND client_code_snapshot IS NOT NULL
        AND client_name_snapshot IS NOT NULL AND payment_method_snapshot IS NOT NULL));

ALTER TABLE invoice_lines ADD CONSTRAINT ck_invoice_lines_financial_amounts_nonnegative
    CHECK (gross_amount_excluding_tax >= 0 AND discount_amount >= 0 AND net_amount_excluding_tax >= 0
        AND vat_amount >= 0 AND total_including_tax >= 0);
ALTER TABLE invoice_lines ADD CONSTRAINT ck_invoice_lines_financial_totals_consistent
    CHECK (discount_amount <= gross_amount_excluding_tax
        AND net_amount_excluding_tax = gross_amount_excluding_tax - discount_amount
        AND total_including_tax = net_amount_excluding_tax + vat_amount);

CREATE INDEX idx_invoices_contract_id ON invoices(contract_id);
CREATE INDEX idx_invoices_invoice_date ON invoices(invoice_date);
CREATE INDEX idx_invoices_created_at ON invoices(created_at);
CREATE INDEX idx_invoice_lines_product_id ON invoice_lines(product_id);
