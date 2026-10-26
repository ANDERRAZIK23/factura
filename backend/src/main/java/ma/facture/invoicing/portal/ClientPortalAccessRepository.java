package ma.facture.invoicing.portal;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface ClientPortalAccessRepository extends JpaRepository<ClientPortalAccess, UUID> {
    Optional<ClientPortalAccess> findByTokenHash(String tokenHash);
    Optional<ClientPortalAccess> findByClientIdAndRevokedAtIsNull(UUID clientId);
}
