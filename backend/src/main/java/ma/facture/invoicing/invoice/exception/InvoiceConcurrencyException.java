package ma.facture.invoicing.invoice.exception;

public class InvoiceConcurrencyException extends RuntimeException {
    public InvoiceConcurrencyException() { super("Cette facture a été modifiée par un autre utilisateur. Rechargez-la et réessayez"); }
}
