package ma.facture.invoicing.sales;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import java.time.LocalDate;
import java.util.*;
public interface RecurringInvoiceRepository extends JpaRepository<RecurringInvoice, UUID> {
    Page<RecurringInvoice> findAllByCompanyIdOrderByCreatedAtDesc(UUID companyId, Pageable pageable);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RecurringInvoice r where r.id = :id")
    Optional<RecurringInvoice> lock(UUID id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RecurringInvoice r where r.id = :id and r.companyId = :companyId")
    Optional<RecurringInvoice> lockOwned(UUID id, UUID companyId);
    @Query("select r from RecurringInvoice r where r.enabled = true and r.nextDate <= :date order by r.nextDate, r.id")
    List<RecurringInvoice> due(LocalDate date, Pageable pageable);
}
