package ma.facture.invoicing.contract.dto;

import java.util.UUID;

public record ContractClientResponse(UUID id, String clientCode, String clientName, boolean active) {}
