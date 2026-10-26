package ma.facture.invoicing.invoice.entity;

import jakarta.persistence.*;
import ma.facture.invoicing.client.entity.Client;
import ma.facture.invoicing.contract.entity.Contract;
import ma.facture.invoicing.contract.entity.PaymentMethod;
import ma.facture.invoicing.invoice.calculation.InvoiceCalculation;
import ma.facture.invoicing.product.entity.Product;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Entity
@Table(name = "invoices")
public class Invoice {
    @Id private UUID id;
    // A draft has no number; the immutable number is assigned exactly once during validation.
    @Column(name = "invoice_number", length = 30) private String invoiceNumber;
    @Column(name = "company_id", nullable = false, updatable = false) private UUID companyId;
    @Column(name = "invoice_date", nullable = false) private LocalDate invoiceDate;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "client_id", nullable = false) private Client client;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "contract_id") private Contract contract;
    @Column(name = "created_by_user_id", nullable = false, updatable = false) private UUID createdBy;
    @Column(name = "validator_user_id") private UUID validatorUserId;
    @Column(name = "customer_order_reference", length = 100) private String customerOrderReference;
    @Enumerated(EnumType.STRING) @Column(name = "payment_method", nullable = false, length = 100) private PaymentMethod paymentMethod;
    @Column(name = "subtotal_excluding_tax", nullable = false, precision = 19, scale = 2) private BigDecimal subtotalExcludingTax;
    @Column(name = "total_discount", nullable = false, precision = 19, scale = 2) private BigDecimal totalDiscount;
    @Column(name = "taxable_amount", nullable = false, precision = 19, scale = 2) private BigDecimal taxableAmount;
    @Column(name = "total_vat", nullable = false, precision = 19, scale = 2) private BigDecimal totalVat;
    @Column(name = "total_including_tax", nullable = false, precision = 19, scale = 2) private BigDecimal totalIncludingTax;
    @Column(name = "paid_amount", nullable = false, precision = 19, scale = 2) private BigDecimal paidAmount = BigDecimal.ZERO.setScale(2);
    @Column(name = "due_date") private LocalDate dueDate;
    @Column(name = "amount_in_words") private String amountInWords;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private InvoiceStatus status;
    @Column(name = "pdf_file_path") private String pdfFilePath;
    @Column(name = "client_code_snapshot", length = 30) private String clientCodeSnapshot;
    @Column(name = "client_name_snapshot", length = 200) private String clientNameSnapshot;
    @Column(name = "client_address_snapshot") private String clientAddressSnapshot;
    @Column(name = "client_phone_snapshot", length = 40) private String clientPhoneSnapshot;
    @Column(name = "client_ice_snapshot", length = 30) private String clientIceSnapshot;
    @Column(name = "payment_method_snapshot", length = 100) private String paymentMethodSnapshot;
    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC") private List<InvoiceLine> lines = new ArrayList<>();
    @Column(name = "updated_by") private UUID updatedBy;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "validated_at") private Instant validatedAt;
    @Column(name = "cancelled_at") private Instant cancelledAt;
    @Version @Column(nullable = false) private long version;

    protected Invoice() {}

    public Invoice(LocalDate invoiceDate, Client client, Contract contract, PaymentMethod paymentMethod,
                   String customerOrderReference, UUID actorId) {
        this.id = UUID.randomUUID();
        this.companyId = client.getCompanyId();
        this.status = InvoiceStatus.DRAFT;
        this.createdBy = actorId;
        this.updatedBy = actorId;
        this.subtotalExcludingTax = BigDecimal.ZERO.setScale(2);
        this.totalDiscount = BigDecimal.ZERO.setScale(2);
        this.taxableAmount = BigDecimal.ZERO.setScale(2);
        this.totalVat = BigDecimal.ZERO.setScale(2);
        this.totalIncludingTax = BigDecimal.ZERO.setScale(2);
        updateDraft(invoiceDate, client, contract, paymentMethod, customerOrderReference, actorId);
    }

    @PrePersist void initializeTimestamps() { Instant now = Instant.now(); createdAt = now; updatedAt = now; }
    @PreUpdate void updateTimestamp() { updatedAt = Instant.now(); }

    public void updateDraft(LocalDate invoiceDate, Client client, Contract contract, PaymentMethod paymentMethod,
                            String customerOrderReference, UUID actorId) {
        this.invoiceDate = invoiceDate;
        this.client = client;
        this.contract = contract;
        this.paymentMethod = paymentMethod;
        this.customerOrderReference = customerOrderReference;
        this.updatedBy = actorId;
    }

    public InvoiceLine addLine(Product product, BigDecimal quantity, BigDecimal price, BigDecimal vatRate,
                               BigDecimal discountRate) {
        InvoiceLine line = new InvoiceLine(this, product, quantity, price, vatRate, discountRate);
        lines.add(line);
        return line;
    }

    public void clearLines() {
        lines.clear();
        subtotalExcludingTax = BigDecimal.ZERO.setScale(2);
        totalDiscount = BigDecimal.ZERO.setScale(2);
        taxableAmount = BigDecimal.ZERO.setScale(2);
        totalVat = BigDecimal.ZERO.setScale(2);
        totalIncludingTax = BigDecimal.ZERO.setScale(2);
    }

    public void applyCalculation(InvoiceCalculation calculation) {
        if (calculation.lines().size() != lines.size()) throw new IllegalArgumentException("Le nombre de lignes calculées ne correspond pas à la facture");
        for (int index = 0; index < lines.size(); index++) lines.get(index).applyCalculation(calculation.lines().get(index));
        subtotalExcludingTax = calculation.subtotalExcludingTax();
        totalDiscount = calculation.totalDiscount();
        taxableAmount = calculation.taxableAmount();
        totalVat = calculation.totalVat();
        totalIncludingTax = calculation.totalIncludingTax();
    }

    public void validate(String number, UUID actorId, Instant now) {
        invoiceNumber = number;
        validatorUserId = actorId;
        validatedAt = now;
        status = InvoiceStatus.VALIDATED;
        updatedBy = actorId;
        clientCodeSnapshot = client.getClientCode();
        clientNameSnapshot = client.getClientName();
        clientAddressSnapshot = client.getAddress();
        clientPhoneSnapshot = client.getPhoneNumber();
        clientIceSnapshot = client.getIce();
        paymentMethodSnapshot = paymentMethod.name();
        lines.forEach(InvoiceLine::captureProductSnapshot);
    }

    public void markPaid(UUID actorId) { paidAmount = totalIncludingTax; status = InvoiceStatus.PAID; updatedBy = actorId; }
    public void recordPayment(BigDecimal amount, UUID actorId) {
        BigDecimal next = paidAmount.add(amount);
        if (next.signum() < 0 || next.compareTo(totalIncludingTax) > 0)
            throw new IllegalArgumentException("Le règlement dépasse le solde de la facture");
        paidAmount = next;
        status = next.compareTo(totalIncludingTax) == 0 ? InvoiceStatus.PAID : InvoiceStatus.VALIDATED;
        updatedBy = actorId;
    }
    public void setDueDate(LocalDate date, UUID actorId) { dueDate = date; updatedBy = actorId; }
    public void cancel(UUID actorId, Instant now) { status = InvoiceStatus.CANCELLED; cancelledAt = now; updatedBy = actorId; }
    public void attachPdf(String relativePath, String words) {
        if (invoiceNumber == null || status != InvoiceStatus.VALIDATED)
            throw new IllegalStateException("Le PDF ne peut être joint que lors de la validation de la facture");
        if (relativePath == null || relativePath.isBlank()) throw new IllegalArgumentException("Le chemin du PDF est requis");
        if (words == null || words.isBlank()) throw new IllegalArgumentException("Le montant en lettres est requis");
        pdfFilePath = relativePath;
        amountInWords = words;
    }

    public UUID getId() { return id; }
    public UUID getCompanyId() { return companyId; }
    public String getInvoiceNumber() { return invoiceNumber; }
    public LocalDate getInvoiceDate() { return invoiceDate; }
    public Client getClient() { return client; }
    public Contract getContract() { return contract; }
    public UUID getCreatedBy() { return createdBy; }
    public UUID getValidatorUserId() { return validatorUserId; }
    public String getCustomerOrderReference() { return customerOrderReference; }
    public PaymentMethod getPaymentMethod() { return paymentMethod; }
    public BigDecimal getSubtotalExcludingTax() { return subtotalExcludingTax; }
    public BigDecimal getTotalDiscount() { return totalDiscount; }
    public BigDecimal getTaxableAmount() { return taxableAmount; }
    public BigDecimal getTotalVat() { return totalVat; }
    public BigDecimal getTotalIncludingTax() { return totalIncludingTax; }
    public BigDecimal getPaidAmount() { return paidAmount; }
    public BigDecimal getOutstandingAmount() { return status == InvoiceStatus.CANCELLED ? BigDecimal.ZERO.setScale(2) : totalIncludingTax.subtract(paidAmount); }
    public LocalDate getDueDate() { return dueDate; }
    public String getAmountInWords() { return amountInWords; }
    public InvoiceStatus getStatus() { return status; }
    public String getPdfFilePath() { return pdfFilePath; }
    public String getClientCodeSnapshot() { return clientCodeSnapshot; }
    public String getClientNameSnapshot() { return clientNameSnapshot; }
    public String getClientAddressSnapshot() { return clientAddressSnapshot; }
    public String getClientPhoneSnapshot() { return clientPhoneSnapshot; }
    public String getClientIceSnapshot() { return clientIceSnapshot; }
    public String getPaymentMethodSnapshot() { return paymentMethodSnapshot; }
    public List<InvoiceLine> getLines() { return lines; }
    public UUID getUpdatedBy() { return updatedBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getValidatedAt() { return validatedAt; }
    public Instant getCancelledAt() { return cancelledAt; }
    public long getVersion() { return version; }
}
