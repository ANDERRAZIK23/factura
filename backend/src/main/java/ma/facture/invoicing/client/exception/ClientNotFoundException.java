package ma.facture.invoicing.client.exception;

import ma.facture.invoicing.common.NotFoundException;

public class ClientNotFoundException extends NotFoundException {
    public ClientNotFoundException() { super("Client introuvable"); }
}
