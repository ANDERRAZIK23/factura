package ma.facture.invoicing.client.exception;

import ma.facture.invoicing.common.ConflictException;

public class DuplicateClientIceException extends ConflictException {
    public DuplicateClientIceException() { super("Un client avec cet ICE existe déjà"); }
}
