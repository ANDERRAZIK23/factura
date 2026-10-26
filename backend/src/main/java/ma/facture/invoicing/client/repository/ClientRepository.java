package ma.facture.invoicing.client.repository;

import ma.facture.invoicing.client.entity.Client;
import org.springframework.data.jpa.repository.*;
import java.util.UUID;
import java.util.Optional;

public interface ClientRepository extends JpaRepository<Client, UUID>, JpaSpecificationExecutor<Client> {
    boolean existsByIce(String ice);
    boolean existsByIceAndIdNot(String ice, UUID id);
    boolean existsByCompanyIdAndIce(UUID companyId, String ice);
    boolean existsByCompanyIdAndIceAndIdNot(UUID companyId, String ice, UUID id);
    Optional<Client> findByIdAndCompanyId(UUID id, UUID companyId);

    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Client c where c.id = :id and c.companyId = :companyId")
    Optional<Client> findByIdAndCompanyIdForUpdate(UUID id, UUID companyId);
}
