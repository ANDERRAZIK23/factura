package ma.facture.invoicing.user;

import jakarta.persistence.*;
import ma.facture.invoicing.company.TenantDefaults;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class UserEntity {
    @Id private UUID id;
    @Column(name = "full_name", nullable = false, length = 200) private String fullName;
    @Column(nullable = false, unique = true, length = 320) private String email;
    @Column(name = "password_hash", nullable = false, length = 100) private String passwordHash;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private UserRole role;
    @Column(name = "company_id", nullable = false, updatable = false) private UUID companyId;
    @Column(nullable = false) private boolean enabled;
    @Version private long version;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected UserEntity() {}
    public UserEntity(String fullName, String email, String passwordHash, UserRole role) {
        this(fullName, email, passwordHash, role, TenantDefaults.DEFAULT_COMPANY_ID);
    }
    public UserEntity(String fullName, String email, String passwordHash, UserRole role, UUID companyId) {
        id = UUID.randomUUID(); this.fullName = fullName; this.email = email;
        this.passwordHash = passwordHash; this.role = role; this.companyId = companyId; enabled = true;
    }
    @PrePersist void createTimestamps() { var now = Instant.now(); createdAt = now; updatedAt = now; }
    @PreUpdate void updateTimestamp() { updatedAt = Instant.now(); }
    public UUID getId() { return id; }
    public String getFullName() { return fullName; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public UserRole getRole() { return role; }
    public UUID getCompanyId() { return companyId; }
    public boolean isEnabled() { return enabled; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void update(String fullName, String email, UserRole role, String passwordHash) {
        this.fullName = fullName; this.email = email; this.role = role;
        if (passwordHash != null) this.passwordHash = passwordHash;
    }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
