package ma.facture.invoicing.invoice.exception;

import ma.facture.invoicing.common.ConflictException;

public class InvoiceConflictException extends ConflictException {
    public InvoiceConflictException(String message) { super(message); }
}
