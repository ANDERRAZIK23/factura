package ma.facture.invoicing.invoice.dto;

import jakarta.validation.constraints.Min;

public record InvoiceVersionRequest(@Min(value = 0, message = "La version ne doit pas être négative") long version) {}
