package ma.facture.invoicing.client.exception;

import ma.facture.invoicing.common.ConflictException;

public class ClientConcurrencyException extends ConflictException {
    public ClientConcurrencyException() { super("Ce client a été modifié par un autre utilisateur. Rechargez les dernières données et réessayez"); }
}
