package ma.facture.invoicing.invoice.repository;

import ma.facture.invoicing.invoice.entity.Invoice;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.*;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID>, JpaSpecificationExecutor<Invoice> {
    @EntityGraph(attributePaths = {"client", "contract", "lines", "lines.product"})
    @Query("select i from Invoice i where i.id = :id")
    Optional<Invoice> findDetailedById(@Param("id") UUID id);
    @EntityGraph(attributePaths = {"client", "contract", "lines", "lines.product"})
    @Query("select i from Invoice i where i.id = :id and i.companyId = :companyId")
    Optional<Invoice> findDetailedByIdAndCompanyId(@Param("id") UUID id, @Param("companyId") UUID companyId);

    @Override
    Page<Invoice> findAll(Specification<Invoice> specification, Pageable pageable);
}
