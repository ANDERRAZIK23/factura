package ma.facture.invoicing.auth;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from RefreshTokenEntity t join fetch t.user where t.tokenHash = :hash")
    Optional<RefreshTokenEntity> findByHashForUpdate(String hash);
    @Modifying
    @Query("update RefreshTokenEntity t set t.revokedAt = :now where t.tokenFamily = :family and t.revokedAt is null")
    int revokeFamily(UUID family, Instant now);
}
