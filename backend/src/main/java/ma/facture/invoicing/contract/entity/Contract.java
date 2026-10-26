package ma.facture.invoicing.contract.entity;

import jakarta.persistence.*;
import ma.facture.invoicing.client.entity.Client;
import ma.facture.invoicing.product.entity.Product;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Entity
@Table(name = "contracts")
public class Contract {
    @Id private UUID id;
    @Column(name = "contract_code", nullable = false, unique = true, length = 40, updatable = false) private String contractCode;
    @Column(name = "company_id", nullable = false, updatable = false) private UUID companyId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "client_id", nullable = false) private Client client;
    @Column(name = "start_date", nullable = false) private LocalDate startDate;
    @Column(name = "end_date") private LocalDate endDate;
    @Enumerated(EnumType.STRING) @Column(name = "payment_method", nullable = false, length = 30) private PaymentMethod paymentMethod;
    @Column(name = "default_discount_rate", nullable = false, precision = 5, scale = 2) private BigDecimal defaultDiscountRate;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private ContractStatus status;
    @Column(length = 2000) private String notes;
    @OneToMany(mappedBy = "contract", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC") private List<ContractLine> lines = new ArrayList<>();
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "created_by", nullable = false, updatable = false) private UUID createdBy;
    @Column(name = "updated_by", nullable = false) private UUID updatedBy;
    @Column(name = "activated_at") private Instant activatedAt;
    @Column(name = "activated_by") private UUID activatedBy;
    @Column(name = "cancelled_at") private Instant cancelledAt;
    @Column(name = "cancelled_by") private UUID cancelledBy;
    @Column(name = "cancellation_reason", length = 500) private String cancellationReason;
    @Version @Column(nullable = false) private long version;

    protected Contract() {}
    public Contract(String code, Client client, LocalDate startDate, LocalDate endDate, PaymentMethod paymentMethod,
                    BigDecimal defaultDiscountRate, String notes, UUID actorId) {
        this.id = UUID.randomUUID(); this.contractCode = code; this.status = ContractStatus.DRAFT;
        this.companyId = client.getCompanyId();
        this.createdBy = actorId; this.updatedBy = actorId;
        updateDraft(client, startDate, endDate, paymentMethod, defaultDiscountRate, notes, actorId);
    }
    @PrePersist void initializeTimestamps() { Instant now = Instant.now(); createdAt = now; updatedAt = now; }
    @PreUpdate void updateTimestamp() { updatedAt = Instant.now(); }
    public void updateDraft(Client client, LocalDate startDate, LocalDate endDate, PaymentMethod paymentMethod, BigDecimal defaultDiscountRate, String notes, UUID actorId) {
        this.client = client; this.startDate = startDate; this.endDate = endDate; this.paymentMethod = paymentMethod;
        this.defaultDiscountRate = defaultDiscountRate; this.notes = notes; this.updatedBy = actorId;
    }
    public ContractLine addLine(Product product, BigDecimal price, BigDecimal vat, BigDecimal discount, BigDecimal minimum, String notes) {
        ContractLine line = new ContractLine(this, product, price, vat, discount, minimum, notes); lines.add(line); return line;
    }
    public void removeLine(ContractLine line) { lines.remove(line); }
    public void activate(UUID actorId, Instant now) { status = ContractStatus.ACTIVE; activatedAt = now; activatedBy = actorId; updatedBy = actorId; }
    public void cancel(UUID actorId, Instant now, String reason) { status = ContractStatus.CANCELLED; cancelledAt = now; cancelledBy = actorId; cancellationReason = reason; updatedBy = actorId; }
    public UUID getId() { return id; }
    public UUID getCompanyId() { return companyId; }
    public String getContractCode() { return contractCode; }
    public Client getClient() { return client; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public PaymentMethod getPaymentMethod() { return paymentMethod; }
    public BigDecimal getDefaultDiscountRate() { return defaultDiscountRate; }
    public ContractStatus getStatus() { return status; }
    public String getNotes() { return notes; }
    public List<ContractLine> getLines() { return lines; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public UUID getCreatedBy() { return createdBy; }
    public UUID getUpdatedBy() { return updatedBy; }
    public Instant getActivatedAt() { return activatedAt; }
    public UUID getActivatedBy() { return activatedBy; }
    public Instant getCancelledAt() { return cancelledAt; }
    public UUID getCancelledBy() { return cancelledBy; }
    public String getCancellationReason() { return cancellationReason; }
    public long getVersion() { return version; }
}
