package ma.facture.invoicing.company;

import java.time.Instant;

public final class CompanyBrandingDtos {
    private CompanyBrandingDtos() {}

    public record Response(String companyName, String logoDataUrl, Instant updatedAt) {}
}
