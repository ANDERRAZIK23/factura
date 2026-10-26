package ma.facture.invoicing.contract.design;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;

public final class ContractDesignDtos {
    private ContractDesignDtos() {}
    public record Request(@NotNull @Valid ContractDesignSettings settings, @PositiveOrZero Long version) {}
    public record Response(UUID companyId, ContractDesignSettings settings, Long version, Instant updatedAt, boolean customized) {}
}
