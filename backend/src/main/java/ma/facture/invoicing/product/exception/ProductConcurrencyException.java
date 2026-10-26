package ma.facture.invoicing.product.exception;

import ma.facture.invoicing.common.ConflictException;

public class ProductConcurrencyException extends ConflictException {
    public ProductConcurrencyException() { super("Ce produit a été modifié par un autre utilisateur. Rechargez les dernières données et réessayez"); }
}
