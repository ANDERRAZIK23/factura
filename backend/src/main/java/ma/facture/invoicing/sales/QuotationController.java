package ma.facture.invoicing.sales;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import ma.facture.invoicing.invoice.dto.*;
import ma.facture.invoicing.security.AuthenticatedUser;
import org.springframework.data.domain.Page;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.UUID;

@RestController @RequestMapping("/api/v1/quotations")
public class QuotationController {
    private static final String MANAGERS = "hasAnyRole('APP_ADMIN','ADMIN','INVOICE_MANAGER')";
    private final QuotationService service;
    private final QuotationPdfRenderer pdf;
    public QuotationController(QuotationService service, QuotationPdfRenderer pdf) { this.service = service; this.pdf = pdf; }
    public record Create(@NotNull @Valid InvoiceCreateRequest document, @NotNull LocalDate validUntil) {}
    public record Convert(@PositiveOrZero long version, LocalDate dueDate) {}
    @GetMapping public Page<Quotation> list(@RequestParam(defaultValue = "0") int page, @AuthenticationPrincipal AuthenticatedUser actor) { return service.list(page, actor); }
    @GetMapping("/{id}") public Quotation get(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser actor) { return service.get(id, actor); }
    @PostMapping @PreAuthorize(MANAGERS) @ResponseStatus(HttpStatus.CREATED)
    public Quotation create(@Valid @RequestBody Create request, @AuthenticationPrincipal AuthenticatedUser actor) { return service.create(request.document(), request.validUntil(), actor); }
    @PostMapping("/{id}/convert") @PreAuthorize(MANAGERS)
    public InvoiceResponse convert(@PathVariable UUID id, @Valid @RequestBody Convert request, @AuthenticationPrincipal AuthenticatedUser actor) { return service.convert(id, request.version(), request.dueDate(), actor); }
    @PostMapping("/{id}/cancel") @PreAuthorize(MANAGERS)
    public Quotation cancel(@PathVariable UUID id, @Valid @RequestBody InvoiceVersionRequest request, @AuthenticationPrincipal AuthenticatedUser actor) { return service.cancel(id, request.version(), actor); }
    @GetMapping(value = "/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> pdf(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser actor) {
        Quotation quote = service.get(id, actor);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + quote.getQuoteNumber() + ".pdf\"").body(pdf.render(quote));
    }
}
