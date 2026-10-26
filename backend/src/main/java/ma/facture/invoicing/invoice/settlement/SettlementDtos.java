package ma.facture.invoicing.invoice.settlement;
import jakarta.validation.constraints.*;
import ma.facture.invoicing.contract.entity.PaymentMethod;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;

public final class SettlementDtos {
    private SettlementDtos() {}
    public record PaymentRequest(@PositiveOrZero long version,
            @NotNull @DecimalMin("0.01") @Digits(integer = 17, fraction = 2) BigDecimal amount,
            @NotNull LocalDate paymentDate, @NotNull PaymentMethod paymentMethod,
            @NotNull @Size(max = 200) String reference) {}
    public record ReversalRequest(@PositiveOrZero long version, @NotBlank @Size(max = 300) String reason) {}
    public record DueDateRequest(@PositiveOrZero long version, LocalDate dueDate) {}
    public record Payment(UUID id, BigDecimal amount, LocalDate paymentDate, PaymentMethod paymentMethod,
            String reference, String entryType, Instant createdAt, Instant reversedAt, String reversalReason) {
        public static Payment from(InvoicePayment p) { return new Payment(p.getId(), p.getAmount(), p.getPaymentDate(),
                p.getPaymentMethod(), p.getReference(), p.getEntryType(), p.getCreatedAt(), p.getReversedAt(), p.getReversalReason()); }
    }
    public record Reminder(UUID id, String recipient, String subject, String body, Instant createdAt) {
        public static Reminder from(InvoiceReminder r) { return new Reminder(r.getId(), r.getRecipient(), r.getSubject(), r.getBody(), r.getCreatedAt()); }
    }
}
