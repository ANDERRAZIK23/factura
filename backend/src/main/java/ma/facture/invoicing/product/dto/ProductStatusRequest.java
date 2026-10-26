package ma.facture.invoicing.product.dto;

import jakarta.validation.constraints.Min;

public record ProductStatusRequest(boolean active, @Min(value = 0, message = "La version ne doit pas être négative") long version) {}
