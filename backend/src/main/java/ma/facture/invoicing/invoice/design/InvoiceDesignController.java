package ma.facture.invoicing.invoice.design;

import jakarta.validation.Valid;
import ma.facture.invoicing.security.AuthenticatedUser;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/invoice-designs")
@PreAuthorize("hasAnyRole('APP_ADMIN','ADMIN')")
public class InvoiceDesignController {
    private final InvoiceDesignService service;
    public InvoiceDesignController(InvoiceDesignService service) { this.service = service; }
    @GetMapping
    public InvoiceDesignDtos.Response get(@RequestParam(required = false) UUID companyId,
            @AuthenticationPrincipal AuthenticatedUser actor) { return service.get(companyId, actor); }
    @PutMapping
    public InvoiceDesignDtos.Response save(@RequestParam(required = false) UUID companyId,
            @Valid @RequestBody InvoiceDesignDtos.Request request, @AuthenticationPrincipal AuthenticatedUser actor) {
        return service.save(companyId, request, actor);
    }
    @PostMapping(value = "/preview", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> preview(@RequestParam(required = false) UUID companyId,
            @Valid @RequestBody InvoiceDesignSettings settings, @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=apercu-facture.pdf")
                .body(service.preview(companyId, settings, actor));
    }
}
