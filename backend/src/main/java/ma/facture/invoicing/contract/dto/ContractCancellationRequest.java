package ma.facture.invoicing.contract.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

@Schema(description = "Cancels a DRAFT or ACTIVE contract; ACTIVE contracts require a reason")
public record ContractCancellationRequest(
        @Size(max = 500, message = "Le motif d’annulation ne doit pas dépasser 500 caractères")
        @Schema(example = "Commercial agreement terminated") String reason,
        @Min(value = 0, message = "La version ne doit pas être négative") @Schema(example = "1") long version) {}
