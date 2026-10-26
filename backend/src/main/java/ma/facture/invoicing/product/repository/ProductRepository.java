package ma.facture.invoicing.product.repository;

import ma.facture.invoicing.product.entity.Product;
import org.springframework.data.jpa.repository.*;
import java.util.UUID;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, UUID>, JpaSpecificationExecutor<Product> {
    Optional<Product> findByIdAndCompanyId(UUID id, UUID companyId);
}
