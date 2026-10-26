package ma.facture.invoicing.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.*;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<UserEntity, UUID> {
    Optional<UserEntity> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCaseAndIdNot(String email, UUID id);
    long countByRoleAndEnabledTrue(UserRole role);
    Page<UserEntity> findAllByCompanyId(UUID companyId, Pageable pageable);
    Optional<UserEntity> findByIdAndCompanyId(UUID id, UUID companyId);
    long countByCompanyIdAndRoleAndEnabledTrue(UUID companyId, UserRole role);
}
