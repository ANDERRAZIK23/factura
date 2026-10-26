package ma.facture.invoicing.company;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "company_branding")
public class CompanyBranding {
    @Id @Column(name = "company_id") private UUID companyId;
    @Column(name = "logo_content_type", nullable = false, length = 30) private String logoContentType;
    @Column(name = "logo_data", nullable = false) private byte[] logoData;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected CompanyBranding() {}

    public CompanyBranding(UUID companyId, String logoContentType, byte[] logoData) {
        this.companyId = companyId;
        update(logoContentType, logoData);
    }

    public void update(String logoContentType, byte[] logoData) {
        this.logoContentType = logoContentType;
        this.logoData = logoData.clone();
        this.updatedAt = Instant.now();
    }

    public UUID getCompanyId() { return companyId; }
    public String getLogoContentType() { return logoContentType; }
    public byte[] getLogoData() { return logoData.clone(); }
    public Instant getUpdatedAt() { return updatedAt; }
}
