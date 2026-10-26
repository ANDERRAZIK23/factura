package ma.facture.invoicing.invoice.design;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "company_invoice_designs")
public class InvoiceDesign {
    @Id @Column(name = "company_id") private UUID companyId;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false, columnDefinition = "jsonb") private InvoiceDesignSettings settings;
    @Version private Long version;
    @Column(name = "updated_by", nullable = false) private UUID updatedBy;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    protected InvoiceDesign() {}
    public InvoiceDesign(UUID companyId, InvoiceDesignSettings settings, UUID actorId, Instant now) {
        this.companyId = companyId;
        update(settings, actorId, now);
    }
    public void update(InvoiceDesignSettings settings, UUID actorId, Instant now) {
        this.settings = settings;
        this.updatedBy = actorId;
        this.updatedAt = now;
    }
    public UUID getCompanyId() { return companyId; }
    public InvoiceDesignSettings getSettings() { return settings; }
    public Long getVersion() { return version; }
    public Instant getUpdatedAt() { return updatedAt; }
}
