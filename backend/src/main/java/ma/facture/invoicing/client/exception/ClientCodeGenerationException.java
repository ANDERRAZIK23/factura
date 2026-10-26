package ma.facture.invoicing.client.exception;

public class ClientCodeGenerationException extends RuntimeException {
    public ClientCodeGenerationException() { super("Impossible d’attribuer un code client"); }
}
