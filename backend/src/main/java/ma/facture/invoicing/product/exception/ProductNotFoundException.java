package ma.facture.invoicing.product.exception;

import ma.facture.invoicing.common.NotFoundException;

public class ProductNotFoundException extends NotFoundException {
    public ProductNotFoundException() { super("Produit introuvable"); }
}
