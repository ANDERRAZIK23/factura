package ma.facture.invoicing.invoice.settlement;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface InvoicePaymentRepository extends JpaRepository<InvoicePayment, UUID> {
    List<InvoicePayment> findAllByInvoiceIdOrderByCreatedAtDesc(UUID invoiceId);
    Optional<InvoicePayment> findByIdAndInvoiceId(UUID id, UUID invoiceId);
}
