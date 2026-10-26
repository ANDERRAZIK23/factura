package ma.facture.invoicing.sales;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.*;
import java.util.UUID;

@Entity @Table(name = "recurring_invoices")
public class RecurringInvoice {
    @Id private UUID id;
    @Column(name = "company_id", nullable = false) private UUID companyId;
    @Column(nullable = false, length = 200) private String title;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false, columnDefinition = "jsonb") private CommercialTerms terms;
    @Column(name = "anchor_date", nullable = false) private LocalDate anchorDate;
    @Column(name = "next_date", nullable = false) private LocalDate nextDate;
    @Column(name = "end_date") private LocalDate endDate;
    @Column(name = "interval_months", nullable = false) private int intervalMonths;
    @Column(nullable = false) private int occurrence;
    @Column(name = "payment_terms_days", nullable = false) private int paymentTermsDays;
    @Column(nullable = false) private boolean enabled;
    @Column(name = "created_by", nullable = false) private UUID createdBy;
    @Column(name = "execution_user_id", nullable = false) private UUID executionUserId;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "last_invoice_id") private UUID lastInvoiceId;
    @Column(name = "last_error", length = 500) private String lastError;
    @Version private long version;
    protected RecurringInvoice() {}
    public RecurringInvoice(UUID company, String title, CommercialTerms terms, LocalDate start, LocalDate end,
            int months, int days, UUID actor, Instant now) {
        id = UUID.randomUUID(); companyId = company; this.title = title; this.terms = terms; anchorDate = start;
        nextDate = start; endDate = end; intervalMonths = months; paymentTermsDays = days;
        enabled = true; createdBy = actor; executionUserId = actor; createdAt = now;
    }
    public void generated(UUID invoice) {
        lastInvoiceId = invoice; lastError = null; occurrence++;
        // Calculate from the original day to avoid drifting after February or a 30-day month.
        nextDate = anchorDate.plusMonths((long) occurrence * intervalMonths);
        if (endDate != null && nextDate.isAfter(endDate)) enabled = false;
    }
    public void setEnabled(boolean value, UUID actor) { enabled = value; lastError = null; if (value) executionUserId = actor; }
    public void fail(String error) { lastError = error; enabled = false; }
    public UUID getId() { return id; }
    public UUID getCompanyId() { return companyId; }
    public String getTitle() { return title; }
    public CommercialTerms getTerms() { return terms; }
    public LocalDate getAnchorDate() { return anchorDate; }
    public LocalDate getNextDate() { return nextDate; }
    public LocalDate getEndDate() { return endDate; }
    public int getIntervalMonths() { return intervalMonths; }
    public int getPaymentTermsDays() { return paymentTermsDays; }
    public boolean isEnabled() { return enabled; }
    public UUID getCreatedBy() { return createdBy; }
    public UUID getExecutionUserId() { return executionUserId; }
    public Instant getCreatedAt() { return createdAt; }
    public UUID getLastInvoiceId() { return lastInvoiceId; }
    public String getLastError() { return lastError; }
    public long getVersion() { return version; }
}
