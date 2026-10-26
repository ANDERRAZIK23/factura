package ma.facture.invoicing.contract.exception;

import ma.facture.invoicing.common.ConflictException;

public class ContractConflictException extends ConflictException { public ContractConflictException(String message) { super(message); } }
