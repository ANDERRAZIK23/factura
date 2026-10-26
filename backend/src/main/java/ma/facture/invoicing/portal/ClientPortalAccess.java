package ma.facture.invoicing.portal;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "client_portal_access")
public class ClientPortalAccess {
    @Id private UUID id;
    @Column(name = "company_id", nullable = false) private UUID companyId;
    @Column(name = "client_id", nullable = false) private UUID clientId;
    @Column(name = "token_hash", nullable = false, length = 64) private String tokenHash;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(name = "revoked_at") private Instant revokedAt;
    @Column(name = "created_by", nullable = false) private UUID createdBy;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    protected ClientPortalAccess() {}
    public ClientPortalAccess(UUID company, UUID client, String hash, Instant expiry, UUID actor, Instant now) {
        id = UUID.randomUUID(); companyId = company; clientId = client; tokenHash = hash; expiresAt = expiry; createdBy = actor; createdAt = now;
    }
    public void revoke(Instant now) { revokedAt = now; }
    public UUID getId() { return id; }
    public UUID getCompanyId() { return companyId; }
    public UUID getClientId() { return clientId; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getRevokedAt() { return revokedAt; }
}
