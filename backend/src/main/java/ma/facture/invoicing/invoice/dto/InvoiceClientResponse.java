package ma.facture.invoicing.invoice.dto;

import java.util.UUID;

public record InvoiceClientResponse(UUID id, String clientCode, String clientName, String address,
                                    String phoneNumber, String ice, boolean active) {}
