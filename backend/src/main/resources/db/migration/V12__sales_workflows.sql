-- Additive migration: existing PDFs and invoice numbers remain unchanged.
ALTER TABLE invoices ADD COLUMN paid_amount NUMERIC(19,2) NOT NULL DEFAULT 0;
ALTER TABLE invoices ADD COLUMN due_date DATE;
UPDATE invoices SET paid_amount = total_including_tax WHERE status = 'PAID';
ALTER TABLE invoices ADD CONSTRAINT ck_invoice_paid_amount CHECK (paid_amount >= 0 AND paid_amount <= total_including_tax);
ALTER TABLE invoices ADD CONSTRAINT ck_invoice_due_date CHECK (due_date IS NULL OR due_date >= invoice_date);
CREATE INDEX idx_invoice_due ON invoices(company_id, due_date) WHERE status = 'VALIDATED';

CREATE TABLE invoice_payments (
    id UUID PRIMARY KEY,
    invoice_id UUID NOT NULL REFERENCES invoices(id) ON DELETE RESTRICT,
    amount NUMERIC(19,2) NOT NULL CHECK (amount > 0),
    payment_date DATE,
    payment_method VARCHAR(100) NOT NULL,
    reference VARCHAR(200) NOT NULL,
    entry_type VARCHAR(20) NOT NULL CHECK (entry_type IN ('PAYMENT','SETTLEMENT','LEGACY')),
    created_by UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL,
    reversed_by UUID REFERENCES users(id) ON DELETE RESTRICT,
    reversed_at TIMESTAMPTZ,
    reversal_reason VARCHAR(300),
    CHECK (payment_date IS NOT NULL OR entry_type = 'LEGACY'),
    CHECK ((reversed_at IS NULL AND reversed_by IS NULL AND reversal_reason IS NULL)
        OR (reversed_at IS NOT NULL AND reversed_by IS NOT NULL AND reversal_reason IS NOT NULL AND BTRIM(reversal_reason) <> ''))
);
CREATE INDEX idx_payments_invoice ON invoice_payments(invoice_id, created_at);
-- The old Paid flag did not record an actual payment date. Keep that date unknown.
INSERT INTO invoice_payments(id, invoice_id, amount, payment_method, reference, entry_type, created_by, created_at)
SELECT gen_random_uuid(), id, total_including_tax, payment_method,
       'Reprise du statut Payée antérieur au suivi des règlements', 'LEGACY', updated_by, NOW()
FROM invoices WHERE status = 'PAID' AND total_including_tax > 0;

CREATE TABLE invoice_reminders (
    id UUID PRIMARY KEY,
    invoice_id UUID NOT NULL REFERENCES invoices(id) ON DELETE RESTRICT,
    recipient VARCHAR(254), subject VARCHAR(300) NOT NULL, body TEXT NOT NULL,
    created_by UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_reminders_invoice ON invoice_reminders(invoice_id, created_at DESC);

CREATE TABLE quote_sequences (
    company_id UUID NOT NULL REFERENCES companies(id) ON DELETE RESTRICT,
    sequence_year INTEGER NOT NULL, last_value BIGINT NOT NULL CHECK (last_value > 0),
    PRIMARY KEY(company_id, sequence_year)
);
CREATE TABLE quotations (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL REFERENCES companies(id) ON DELETE RESTRICT,
    quote_number VARCHAR(40) NOT NULL,
    issue_date DATE NOT NULL, valid_until DATE NOT NULL CHECK (valid_until >= issue_date),
    terms JSONB NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('OPEN','CONVERTED','CANCELLED')),
    invoice_id UUID UNIQUE REFERENCES invoices(id) ON DELETE RESTRICT,
    created_by UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL, version BIGINT NOT NULL DEFAULT 0,
    UNIQUE(company_id, quote_number),
    CHECK ((status = 'CONVERTED') = (invoice_id IS NOT NULL))
);
CREATE INDEX idx_quotations_company ON quotations(company_id, created_at DESC);

CREATE TABLE recurring_invoices (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL REFERENCES companies(id) ON DELETE RESTRICT,
    title VARCHAR(200) NOT NULL CHECK (BTRIM(title) <> ''), terms JSONB NOT NULL,
    anchor_date DATE NOT NULL, next_date DATE NOT NULL, end_date DATE,
    interval_months INTEGER NOT NULL CHECK (interval_months BETWEEN 1 AND 12),
    occurrence INTEGER NOT NULL DEFAULT 0 CHECK (occurrence >= 0),
    payment_terms_days INTEGER NOT NULL CHECK (payment_terms_days BETWEEN 0 AND 365),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_by UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    execution_user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL,
    last_invoice_id UUID REFERENCES invoices(id) ON DELETE RESTRICT,
    last_error VARCHAR(500), version BIGINT NOT NULL DEFAULT 0,
    CHECK (end_date IS NULL OR end_date >= anchor_date)
);
CREATE INDEX idx_recurring_company ON recurring_invoices(company_id, created_at DESC);
CREATE INDEX idx_recurring_due ON recurring_invoices(next_date) WHERE enabled;
CREATE TABLE recurring_invoice_runs (
    schedule_id UUID NOT NULL REFERENCES recurring_invoices(id) ON DELETE RESTRICT,
    scheduled_date DATE NOT NULL,
    invoice_id UUID NOT NULL UNIQUE REFERENCES invoices(id) ON DELETE RESTRICT,
    PRIMARY KEY(schedule_id, scheduled_date)
);

CREATE TABLE client_portal_access (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL REFERENCES companies(id) ON DELETE RESTRICT,
    client_id UUID NOT NULL REFERENCES clients(id) ON DELETE RESTRICT,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL, revoked_at TIMESTAMPTZ,
    created_by UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL
);
CREATE UNIQUE INDEX uq_portal_active_client ON client_portal_access(client_id) WHERE revoked_at IS NULL;
