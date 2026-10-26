package ma.facture.invoicing.client.entity;

import jakarta.persistence.*;
import ma.facture.invoicing.company.TenantDefaults;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "clients")
public class Client {
    @Id private UUID id;
    @Column(name = "client_code", nullable = false, unique = true, length = 20, updatable = false) private String clientCode;
    @Column(name = "company_id", nullable = false, updatable = false) private UUID companyId;
    @Column(name = "client_name", nullable = false, length = 200) private String clientName;
    @Column(name = "phone_number", length = 30) private String phoneNumber;
    @Column(length = 500) private String address;
    @Column(length = 254) private String email;
    @Column(length = 15) private String ice;
    @Column(nullable = false) private boolean active;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "created_by", nullable = false, updatable = false) private UUID createdBy;
    @Column(name = "updated_by", nullable = false) private UUID updatedBy;
    @Version @Column(nullable = false) private long version;

    protected Client() {}

    public Client(String clientCode, String clientName, String phoneNumber, String address,
                  String email, String ice, UUID actorId) {
        this(clientCode, clientName, phoneNumber, address, email, ice, actorId, TenantDefaults.DEFAULT_COMPANY_ID);
    }

    public Client(String clientCode, String clientName, String phoneNumber, String address,
                  String email, String ice, UUID actorId, UUID companyId) {
        this.id = UUID.randomUUID();
        this.clientCode = clientCode;
        this.companyId = companyId;
        this.clientName = clientName;
        this.phoneNumber = phoneNumber;
        this.address = address;
        this.email = email;
        this.ice = ice;
        this.active = true;
        this.createdBy = actorId;
        this.updatedBy = actorId;
    }

    @PrePersist void initializeTimestamps() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate void updateTimestamp() { updatedAt = Instant.now(); }

    public void update(String clientName, String phoneNumber, String address, String email, String ice, UUID actorId) {
        this.clientName = clientName;
        this.phoneNumber = phoneNumber;
        this.address = address;
        this.email = email;
        this.ice = ice;
        this.updatedBy = actorId;
    }

    public void setStatus(boolean active, UUID actorId) {
        this.active = active;
        this.updatedBy = actorId;
    }

    public UUID getId() { return id; }
    public UUID getCompanyId() { return companyId; }
    public String getClientCode() { return clientCode; }
    public String getClientName() { return clientName; }
    public String getPhoneNumber() { return phoneNumber; }
    public String getAddress() { return address; }
    public String getEmail() { return email; }
    public String getIce() { return ice; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public UUID getCreatedBy() { return createdBy; }
    public UUID getUpdatedBy() { return updatedBy; }
    public long getVersion() { return version; }
}
