package ma.facture.invoicing.contract.exception;

public class ContractConcurrencyException extends RuntimeException { public ContractConcurrencyException() { super("Ce contrat a été modifié par un autre utilisateur. Rechargez les dernières données et réessayez"); } }
