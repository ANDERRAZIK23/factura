package ma.facture.invoicing.sales;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import ma.facture.invoicing.invoice.dto.*;
import ma.facture.invoicing.security.AuthenticatedUser;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.UUID;

@RestController @RequestMapping("/api/v1/recurring-invoices")
public class RecurringInvoiceController {
    private static final String MANAGERS = "hasAnyRole('APP_ADMIN','ADMIN','INVOICE_MANAGER')";
    private final RecurringInvoiceService service;
    public RecurringInvoiceController(RecurringInvoiceService service) { this.service = service; }
    public record Create(@NotNull UUID invoiceId, @NotBlank @Size(max = 200) String title,
            @NotNull LocalDate startDate, LocalDate endDate, @Min(1) @Max(12) int intervalMonths, @Min(0) @Max(365) int paymentTermsDays) {}
    public record Toggle(@PositiveOrZero long version, boolean enabled) {}
    @GetMapping public Page<RecurringInvoice> list(@RequestParam(defaultValue = "0") int page, @AuthenticationPrincipal AuthenticatedUser actor) { return service.list(page, actor); }
    @PostMapping @PreAuthorize(MANAGERS)
    public RecurringInvoice create(@Valid @RequestBody Create request, @AuthenticationPrincipal AuthenticatedUser actor) { return service.create(request, actor); }
    @PutMapping("/{id}/status") @PreAuthorize(MANAGERS)
    public RecurringInvoice toggle(@PathVariable UUID id, @Valid @RequestBody Toggle request, @AuthenticationPrincipal AuthenticatedUser actor) { return service.toggle(id, request.version(), request.enabled(), actor); }
    @PostMapping("/{id}/generate") @PreAuthorize(MANAGERS)
    public InvoiceResponse generate(@PathVariable UUID id, @Valid @RequestBody InvoiceVersionRequest request, @AuthenticationPrincipal AuthenticatedUser actor) { return service.generate(id, request.version(), actor); }
}
