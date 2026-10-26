package ma.facture.invoicing.contract.design;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface ContractDesignRepository extends JpaRepository<ContractDesign, UUID> {}
