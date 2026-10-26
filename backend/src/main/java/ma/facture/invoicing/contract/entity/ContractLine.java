package ma.facture.invoicing.contract.entity;

import jakarta.persistence.*;
import ma.facture.invoicing.product.entity.Product;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "contract_lines", uniqueConstraints = @UniqueConstraint(columnNames = {"contract_id", "product_id"}))
public class ContractLine {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "contract_id", nullable = false) private Contract contract;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "product_id", nullable = false) private Product product;
    @Column(name = "negotiated_unit_price_excluding_tax", nullable = false, precision = 19, scale = 2) private BigDecimal negotiatedUnitPriceExcludingTax;
    @Column(name = "vat_rate", nullable = false, precision = 5, scale = 2) private BigDecimal vatRate;
    @Column(name = "discount_rate", nullable = false, precision = 5, scale = 2) private BigDecimal discountRate;
    @Column(name = "minimum_quantity", precision = 19, scale = 3) private BigDecimal minimumQuantity;
    @Column(length = 1000) private String notes;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version @Column(nullable = false) private long version;

    protected ContractLine() {}
    public ContractLine(Contract contract, Product product, BigDecimal price, BigDecimal vat, BigDecimal discount, BigDecimal minimumQuantity, String notes) {
        this.id = UUID.randomUUID(); this.contract = contract;
        updateTerms(product, price, vat, discount, minimumQuantity, notes);
    }
    @PrePersist void initializeTimestamps() { Instant now = Instant.now(); createdAt = now; updatedAt = now; }
    @PreUpdate void updateTimestamp() { updatedAt = Instant.now(); }
    public void updateTerms(Product product, BigDecimal price, BigDecimal vat, BigDecimal discount, BigDecimal minimumQuantity, String notes) {
        this.product = product; this.negotiatedUnitPriceExcludingTax = price; this.vatRate = vat;
        this.discountRate = discount; this.minimumQuantity = minimumQuantity; this.notes = notes;
    }
    public UUID getId() { return id; }
    public Contract getContract() { return contract; }
    public Product getProduct() { return product; }
    public BigDecimal getNegotiatedUnitPriceExcludingTax() { return negotiatedUnitPriceExcludingTax; }
    public BigDecimal getVatRate() { return vatRate; }
    public BigDecimal getDiscountRate() { return discountRate; }
    public BigDecimal getMinimumQuantity() { return minimumQuantity; }
    public String getNotes() { return notes; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
