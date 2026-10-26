package ma.facture.invoicing.invoice.settlement;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "invoice_reminders")
public class InvoiceReminder {
    @Id private UUID id;
    @Column(name = "invoice_id", nullable = false) private UUID invoiceId;
    @Column(length = 254) private String recipient;
    @Column(nullable = false, length = 300) private String subject;
    @Column(nullable = false, columnDefinition = "text") private String body;
    @Column(name = "created_by", nullable = false) private UUID createdBy;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    protected InvoiceReminder() {}
    public InvoiceReminder(UUID invoice, String recipient, String subject, String body, UUID actor, Instant now) {
        id = UUID.randomUUID(); invoiceId = invoice; this.recipient = recipient; this.subject = subject;
        this.body = body; createdBy = actor; createdAt = now;
    }
    public UUID getId() { return id; }
    public String getRecipient() { return recipient; }
    public String getSubject() { return subject; }
    public String getBody() { return body; }
    public Instant getCreatedAt() { return createdAt; }
}
