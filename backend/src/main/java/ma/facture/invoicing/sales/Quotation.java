package ma.facture.invoicing.sales;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.*;
import java.util.UUID;

@Entity @Table(name = "quotations")
public class Quotation {
    @Id private UUID id;
    @Column(name = "company_id", nullable = false) private UUID companyId;
    @Column(name = "quote_number", nullable = false, length = 40) private String quoteNumber;
    @Column(name = "issue_date", nullable = false) private LocalDate issueDate;
    @Column(name = "valid_until", nullable = false) private LocalDate validUntil;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false, columnDefinition = "jsonb") private CommercialTerms terms;
    @Column(nullable = false, length = 20) private String status;
    @Column(name = "invoice_id") private UUID invoiceId;
    @Column(name = "created_by", nullable = false) private UUID createdBy;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Version private long version;
    protected Quotation() {}
    public Quotation(UUID company, String number, LocalDate date, LocalDate until, CommercialTerms terms, UUID actor, Instant now) {
        id = UUID.randomUUID(); companyId = company; quoteNumber = number; issueDate = date; validUntil = until;
        this.terms = terms; createdBy = actor; createdAt = now; status = "OPEN";
    }
    public void convert(UUID invoice) { status = "CONVERTED"; invoiceId = invoice; }
    public void cancel() { status = "CANCELLED"; }
    public UUID getId() { return id; }
    public UUID getCompanyId() { return companyId; }
    public String getQuoteNumber() { return quoteNumber; }
    public LocalDate getIssueDate() { return issueDate; }
    public LocalDate getValidUntil() { return validUntil; }
    public CommercialTerms getTerms() { return terms; }
    public String getStatus() { return status; }
    public UUID getInvoiceId() { return invoiceId; }
    public Instant getCreatedAt() { return createdAt; }
    public long getVersion() { return version; }
}
