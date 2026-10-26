package ma.facture.invoicing.product.exception;

public class ProductCodeGenerationException extends RuntimeException {
    public ProductCodeGenerationException() { super("Impossible d’attribuer un code produit"); }
}
