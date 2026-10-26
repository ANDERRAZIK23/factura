package ma.facture.invoicing.company;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "companies")
public class Company {
    @Id private UUID id;
    @Column(name = "company_code", nullable = false, unique = true, length = 30, updatable = false) private String companyCode;
    @Column(name = "company_name", nullable = false, length = 200) private String companyName;
    @Column(length = 500) private String address;
    @Column(length = 40) private String phone;
    @Column(length = 254) private String email;
    @Column(length = 15) private String ice;
    @Column(nullable = false) private boolean enabled;
    @Version @Column(nullable = false) private long version;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected Company() {}

    public Company(String companyCode, String companyName, String address, String phone, String email, String ice) {
        this.id = UUID.randomUUID();
        this.companyCode = companyCode;
        this.enabled = true;
        update(companyName, address, phone, email, ice);
    }

    @PrePersist void initializeTimestamps() { Instant now = Instant.now(); createdAt = now; updatedAt = now; }
    @PreUpdate void updateTimestamp() { updatedAt = Instant.now(); }

    public void update(String companyName, String address, String phone, String email, String ice) {
        this.companyName = companyName;
        this.address = address;
        this.phone = phone;
        this.email = email;
        this.ice = ice;
    }

    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public UUID getId() { return id; }
    public String getCompanyCode() { return companyCode; }
    public String getCompanyName() { return companyName; }
    public String getAddress() { return address; }
    public String getPhone() { return phone; }
    public String getEmail() { return email; }
    public String getIce() { return ice; }
    public boolean isEnabled() { return enabled; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
