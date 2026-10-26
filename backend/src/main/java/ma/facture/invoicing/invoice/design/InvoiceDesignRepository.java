package ma.facture.invoicing.invoice.design;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface InvoiceDesignRepository extends JpaRepository<InvoiceDesign, UUID> {}
