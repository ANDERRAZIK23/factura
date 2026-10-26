package ma.facture.invoicing.invoice.pdf;

import ma.facture.invoicing.common.NotFoundException;

public class InvoicePdfNotFoundException extends NotFoundException {
    public InvoicePdfNotFoundException() { super("PDF de la facture introuvable"); }
}
