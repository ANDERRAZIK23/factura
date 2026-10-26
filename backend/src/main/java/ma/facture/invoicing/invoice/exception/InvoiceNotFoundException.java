package ma.facture.invoicing.invoice.exception;

import ma.facture.invoicing.common.NotFoundException;

public class InvoiceNotFoundException extends NotFoundException {
    public InvoiceNotFoundException() { super("Facture introuvable"); }
}
