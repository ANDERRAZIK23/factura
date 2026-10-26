package ma.facture.invoicing.client.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Complete client representation")
public record ClientResponse(UUID id, String clientCode, String clientName, String phoneNumber,
                             String address, String email, String ice, boolean active,
                             Instant createdAt, Instant updatedAt, UUID createdBy, UUID updatedBy,
                             long version) {}
