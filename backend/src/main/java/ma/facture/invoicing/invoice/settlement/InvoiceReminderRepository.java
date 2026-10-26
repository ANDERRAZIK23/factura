package ma.facture.invoicing.invoice.settlement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.*;
import java.util.UUID;
public interface InvoiceReminderRepository extends JpaRepository<InvoiceReminder, UUID> {
    Page<InvoiceReminder> findAllByInvoiceIdOrderByCreatedAtDesc(UUID invoiceId, Pageable pageable);
}
