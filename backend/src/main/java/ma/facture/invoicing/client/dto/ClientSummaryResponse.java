package ma.facture.invoicing.client.dto;

import java.time.Instant;
import java.util.UUID;

public record ClientSummaryResponse(UUID id, String clientCode, String clientName, String phoneNumber,
                                    String email, String ice, boolean active, Instant createdAt,
                                    Instant updatedAt, long version) {}
