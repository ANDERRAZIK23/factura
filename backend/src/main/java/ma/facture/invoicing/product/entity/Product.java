package ma.facture.invoicing.product.entity;

import jakarta.persistence.*;
import ma.facture.invoicing.company.TenantDefaults;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "products")
public class Product {
    @Id private UUID id;
    @Column(name = "product_code", nullable = false, unique = true, length = 30, updatable = false) private String productCode;
    @Column(name = "company_id", nullable = false, updatable = false) private UUID companyId;
    @Column(name = "product_name", nullable = false, length = 200) private String productName;
    @Column(length = 1000) private String description;
    @Column(name = "unit_price_excluding_tax", nullable = false, precision = 19, scale = 2) private BigDecimal unitPriceExcludingTax;
    @Column(name = "default_vat_rate", nullable = false, precision = 5, scale = 2) private BigDecimal defaultVatRate;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private ProductUnit unit;
    @Column(nullable = false) private boolean active;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "created_by", nullable = false, updatable = false) private UUID createdBy;
    @Column(name = "updated_by", nullable = false) private UUID updatedBy;
    @Version @Column(nullable = false) private long version;

    protected Product() {}

    public Product(String productCode, String productName, String description, BigDecimal unitPriceExcludingTax,
                   BigDecimal defaultVatRate, ProductUnit unit, UUID actorId) {
        this(productCode, productName, description, unitPriceExcludingTax, defaultVatRate, unit, actorId, TenantDefaults.DEFAULT_COMPANY_ID);
    }

    public Product(String productCode, String productName, String description, BigDecimal unitPriceExcludingTax,
                   BigDecimal defaultVatRate, ProductUnit unit, UUID actorId, UUID companyId) {
        this.id = UUID.randomUUID();
        this.productCode = productCode;
        this.companyId = companyId;
        this.productName = productName;
        this.description = description;
        this.unitPriceExcludingTax = unitPriceExcludingTax;
        this.defaultVatRate = defaultVatRate;
        this.unit = unit;
        this.active = true;
        this.createdBy = actorId;
        this.updatedBy = actorId;
    }

    @PrePersist void initializeTimestamps() { Instant now = Instant.now(); createdAt = now; updatedAt = now; }
    @PreUpdate void updateTimestamp() { updatedAt = Instant.now(); }

    public void update(String productName, String description, BigDecimal unitPriceExcludingTax,
                       BigDecimal defaultVatRate, ProductUnit unit, UUID actorId) {
        this.productName = productName;
        this.description = description;
        this.unitPriceExcludingTax = unitPriceExcludingTax;
        this.defaultVatRate = defaultVatRate;
        this.unit = unit;
        this.updatedBy = actorId;
    }

    public void setStatus(boolean active, UUID actorId) { this.active = active; this.updatedBy = actorId; }
    public UUID getId() { return id; }
    public UUID getCompanyId() { return companyId; }
    public String getProductCode() { return productCode; }
    public String getProductName() { return productName; }
    public String getDescription() { return description; }
    public BigDecimal getUnitPriceExcludingTax() { return unitPriceExcludingTax; }
    public BigDecimal getDefaultVatRate() { return defaultVatRate; }
    public ProductUnit getUnit() { return unit; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public UUID getCreatedBy() { return createdBy; }
    public UUID getUpdatedBy() { return updatedBy; }
    public long getVersion() { return version; }
}
