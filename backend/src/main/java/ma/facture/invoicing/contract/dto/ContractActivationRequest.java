package ma.facture.invoicing.contract.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;

@Schema(description = "Activates a valid DRAFT and permanently freezes its commercial terms")
public record ContractActivationRequest(
        @Min(value = 0, message = "La version ne doit pas être négative")
        @Schema(example = "0", description = "Optimistic-lock version last read by the caller") long version) {}
