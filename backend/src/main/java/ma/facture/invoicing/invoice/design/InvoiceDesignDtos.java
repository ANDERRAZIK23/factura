package ma.facture.invoicing.invoice.design;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;

public final class InvoiceDesignDtos {
    private InvoiceDesignDtos() {}
    public record Request(@NotNull @Valid InvoiceDesignSettings settings, @PositiveOrZero Long version) {}
    public record Response(UUID companyId, InvoiceDesignSettings settings, Long version, Instant updatedAt, boolean customized) {}
}
