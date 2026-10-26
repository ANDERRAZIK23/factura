package ma.facture.invoicing.invoice.settlement;

import jakarta.persistence.*;
import ma.facture.invoicing.contract.entity.PaymentMethod;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;

@Entity @Table(name = "invoice_payments")
public class InvoicePayment {
    @Id private UUID id;
    @Column(name = "invoice_id", nullable = false) private UUID invoiceId;
    @Column(nullable = false, precision = 19, scale = 2) private BigDecimal amount;
    @Column(name = "payment_date") private LocalDate paymentDate;
    @Enumerated(EnumType.STRING) @Column(name = "payment_method", nullable = false, length = 100) private PaymentMethod paymentMethod;
    @Column(nullable = false, length = 200) private String reference;
    @Column(name = "entry_type", nullable = false, length = 20) private String entryType;
    @Column(name = "created_by", nullable = false) private UUID createdBy;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "reversed_by") private UUID reversedBy;
    @Column(name = "reversed_at") private Instant reversedAt;
    @Column(name = "reversal_reason", length = 300) private String reversalReason;
    protected InvoicePayment() {}
    public InvoicePayment(UUID invoiceId, BigDecimal amount, LocalDate date, PaymentMethod method,
                          String reference, String entryType, UUID actor, Instant now) {
        id = UUID.randomUUID(); this.invoiceId = invoiceId; this.amount = amount; paymentDate = date;
        paymentMethod = method; this.reference = reference; this.entryType = entryType; createdBy = actor; createdAt = now;
    }
    public void reverse(UUID actor, String reason, Instant now) { reversedBy = actor; reversalReason = reason; reversedAt = now; }
    public UUID getId() { return id; }
    public UUID getInvoiceId() { return invoiceId; }
    public BigDecimal getAmount() { return amount; }
    public LocalDate getPaymentDate() { return paymentDate; }
    public PaymentMethod getPaymentMethod() { return paymentMethod; }
    public String getReference() { return reference; }
    public String getEntryType() { return entryType; }
    public UUID getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getReversedAt() { return reversedAt; }
    public String getReversalReason() { return reversalReason; }
}
