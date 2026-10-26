package ma.facture.invoicing.contract.design;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "company_contract_designs")
public class ContractDesign {
    @Id @Column(name = "company_id") private UUID companyId;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false, columnDefinition = "jsonb") private ContractDesignSettings settings;
    @Version private Long version;
    @Column(name = "updated_by", nullable = false) private UUID updatedBy;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected ContractDesign() {}
    public ContractDesign(UUID companyId, ContractDesignSettings settings, UUID actorId, Instant now) {
        this.companyId = companyId;
        update(settings, actorId, now);
    }
    public void update(ContractDesignSettings settings, UUID actorId, Instant now) {
        this.settings = settings;
        this.updatedBy = actorId;
        this.updatedAt = now;
    }
    public UUID getCompanyId() { return companyId; }
    public ContractDesignSettings getSettings() { return settings; }
    public Long getVersion() { return version; }
    public Instant getUpdatedAt() { return updatedAt; }
}
