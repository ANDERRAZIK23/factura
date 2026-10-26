package ma.facture.invoicing.sales;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import java.util.*;
public interface QuotationRepository extends JpaRepository<Quotation, UUID> {
    Page<Quotation> findAllByCompanyIdOrderByCreatedAtDesc(UUID companyId, Pageable pageable);
    Optional<Quotation> findByIdAndCompanyId(UUID id, UUID companyId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select q from Quotation q where q.id = :id and q.companyId = :companyId")
    Optional<Quotation> lock(UUID id, UUID companyId);
}
