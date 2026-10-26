package ma.facture.invoicing.security;

import ma.facture.invoicing.user.UserRole;
import ma.facture.invoicing.company.TenantDefaults;
import java.util.UUID;

public record AuthenticatedUser(UUID id, String email, UserRole role, UUID companyId) {
    public AuthenticatedUser(UUID id, String email, UserRole role) { this(id, email, role, TenantDefaults.DEFAULT_COMPANY_ID); }
}
