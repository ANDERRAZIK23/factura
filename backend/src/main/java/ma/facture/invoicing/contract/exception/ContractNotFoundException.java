package ma.facture.invoicing.contract.exception;

import ma.facture.invoicing.common.NotFoundException;

public class ContractNotFoundException extends NotFoundException { public ContractNotFoundException() { super("Contrat introuvable"); } }
