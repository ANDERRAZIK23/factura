package ma.facture.invoicing.contract.repository;

import ma.facture.invoicing.contract.entity.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.*;
import java.util.*;

public interface ContractRepository extends JpaRepository<Contract, UUID>, JpaSpecificationExecutor<Contract> {
    @EntityGraph(attributePaths = {"client", "lines", "lines.product"})
    @Query("select c from Contract c where c.id = :id") Optional<Contract> findDetailedById(@Param("id") UUID id);
    @EntityGraph(attributePaths = {"client", "lines", "lines.product"})
    @Query("select c from Contract c where c.id = :id and c.companyId = :companyId")
    Optional<Contract> findDetailedByIdAndCompanyId(@Param("id") UUID id, @Param("companyId") UUID companyId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Contract c set c.status = ma.facture.invoicing.contract.entity.ContractStatus.EXPIRED, c.updatedAt = :now " +
           "where c.status = ma.facture.invoicing.contract.entity.ContractStatus.ACTIVE and c.endDate < :businessDate")
    int expireEligible(@Param("businessDate") LocalDate businessDate, @Param("now") Instant now);

    @Override
    Page<Contract> findAll(Specification<Contract> specification, Pageable pageable);
}
