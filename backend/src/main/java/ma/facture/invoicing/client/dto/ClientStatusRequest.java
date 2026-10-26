package ma.facture.invoicing.client.dto;

import jakarta.validation.constraints.Min;

public record ClientStatusRequest(boolean active, @Min(value = 0, message = "La version ne doit pas être négative") long version) {}
