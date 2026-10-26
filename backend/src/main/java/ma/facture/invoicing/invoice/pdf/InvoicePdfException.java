package ma.facture.invoicing.invoice.pdf;

public class InvoicePdfException extends RuntimeException {
    public InvoicePdfException(String message) { super(message); }
    public InvoicePdfException(String message, Throwable cause) { super(message, cause); }
}
