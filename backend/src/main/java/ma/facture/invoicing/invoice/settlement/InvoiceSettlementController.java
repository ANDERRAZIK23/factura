package ma.facture.invoicing.invoice.settlement;
import jakarta.validation.Valid;
import ma.facture.invoicing.invoice.dto.*;
import ma.facture.invoicing.security.AuthenticatedUser;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/api/v1")
public class InvoiceSettlementController {
    private static final String MANAGERS = "hasAnyRole('APP_ADMIN','ADMIN','INVOICE_MANAGER')";
    private final InvoiceSettlementService service;
    public InvoiceSettlementController(InvoiceSettlementService service) { this.service = service; }
    @GetMapping("/invoices/{id}/payments")
    public List<SettlementDtos.Payment> payments(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser actor) { return service.payments(id, actor); }
    @PostMapping("/invoices/{id}/payments") @PreAuthorize(MANAGERS)
    public InvoiceResponse pay(@PathVariable UUID id, @Valid @RequestBody SettlementDtos.PaymentRequest request, @AuthenticationPrincipal AuthenticatedUser actor) { return service.pay(id, request, actor); }
    @PostMapping("/invoices/{id}/payments/{paymentId}/reverse") @PreAuthorize(MANAGERS)
    public InvoiceResponse reverse(@PathVariable UUID id, @PathVariable UUID paymentId, @Valid @RequestBody SettlementDtos.ReversalRequest request, @AuthenticationPrincipal AuthenticatedUser actor) { return service.reverse(id, paymentId, request, actor); }
    @PutMapping("/invoices/{id}/due-date") @PreAuthorize(MANAGERS)
    public InvoiceResponse due(@PathVariable UUID id, @Valid @RequestBody SettlementDtos.DueDateRequest request, @AuthenticationPrincipal AuthenticatedUser actor) { return service.dueDate(id, request, actor); }
    @GetMapping("/collections/overdue")
    public Page<InvoiceSummaryResponse> overdue(@RequestParam(defaultValue = "0") int page, @AuthenticationPrincipal AuthenticatedUser actor) { return service.overdue(page, actor); }
    @GetMapping("/invoices/{id}/reminders")
    public List<SettlementDtos.Reminder> reminders(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser actor) { return service.reminders(id, actor); }
    @PostMapping("/invoices/{id}/reminders") @PreAuthorize(MANAGERS)
    public SettlementDtos.Reminder prepare(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser actor) { return service.prepare(id, actor); }
}
