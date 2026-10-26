package ma.facture.invoicing.auth;

import jakarta.persistence.*;
import ma.facture.invoicing.user.UserEntity;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_tokens")
public class RefreshTokenEntity {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id") private UserEntity user;
    @Column(name = "token_hash", nullable = false, unique = true, length = 64) private String tokenHash;
    @Column(name = "token_family", nullable = false) private UUID tokenFamily;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(name = "revoked_at") private Instant revokedAt;
    @OneToOne(fetch = FetchType.LAZY) @JoinColumn(name = "replaced_by_token_id") private RefreshTokenEntity replacedBy;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "created_by_ip", length = 64) private String createdByIp;
    @Column(name = "user_agent", length = 512) private String userAgent;

    protected RefreshTokenEntity() {}
    public RefreshTokenEntity(UserEntity user, String tokenHash, UUID family, Instant expiresAt, String ip, String agent) {
        id = UUID.randomUUID(); this.user = user; this.tokenHash = tokenHash; tokenFamily = family;
        this.expiresAt = expiresAt; createdByIp = ip; userAgent = agent; createdAt = Instant.now();
    }
    public UserEntity getUser() { return user; }
    public UUID getTokenFamily() { return tokenFamily; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public RefreshTokenEntity getReplacedBy() { return replacedBy; }
    public boolean isExpired(Instant now) { return !expiresAt.isAfter(now); }
    public boolean isRevoked() { return revokedAt != null; }
    public void revoke(Instant now, RefreshTokenEntity replacement) { revokedAt = now; replacedBy = replacement; }
}
