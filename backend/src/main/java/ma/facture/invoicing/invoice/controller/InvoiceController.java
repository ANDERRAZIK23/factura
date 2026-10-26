package ma.facture.invoicing.invoice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import ma.facture.invoicing.invoice.dto.*;
import ma.facture.invoicing.invoice.pdf.InvoicePdfDownload;
import ma.facture.invoicing.invoice.service.InvoiceService;
import ma.facture.invoicing.security.AuthenticatedUser;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.core.io.PathResource;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/invoices")
@Tag(name = "Invoices", description = "Draft preparation, authoritative totals, final numbering, PDF generation and auditable lifecycle.")
public class InvoiceController {
    private static final String MANAGERS = "hasAnyRole('APP_ADMIN','ADMIN','INVOICE_MANAGER')";
    private final InvoiceService service;

    public InvoiceController(InvoiceService service) { this.service = service; }

    @PostMapping @PreAuthorize(MANAGERS)
    @Operation(summary = "Create a DRAFT invoice", description = "Contract terms override client-supplied pricing. Drafts have no final invoice number and may be saved without lines.")
    @ApiResponses({@ApiResponse(responseCode = "201", description = "Draft created"), @ApiResponse(responseCode = "400", description = "Invalid request"), @ApiResponse(responseCode = "403", description = "Manager role required"), @ApiResponse(responseCode = "404", description = "Client, contract or product not found"), @ApiResponse(responseCode = "409", description = "Contract is not usable")})
    public ResponseEntity<InvoiceResponse> create(@Valid @RequestBody InvoiceCreateRequest request,
                                                   @AuthenticationPrincipal AuthenticatedUser actor) {
        InvoiceResponse response = service.create(request, actor);
        var location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get invoice details", description = "Validated, paid and cancelled responses use captured client and product snapshots.")
    public InvoiceResponse get(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser actor) { return service.get(id, actor); }

    @GetMapping
    @Operation(summary = "List invoices", description = "Supports search, client, contract, status, date range, pagination and allowlisted sorting. Page size is limited to 100.")
    public InvoicePageResponse list(@RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "20") int size,
                                    @RequestParam(defaultValue = "createdAt,desc") String sort,
                                    @RequestParam(required = false) String search,
                                    @RequestParam(required = false) UUID clientId,
                                    @RequestParam(required = false) UUID contractId,
                                    @RequestParam(required = false) String status,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate invoiceDateFrom,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate invoiceDateTo,
                                    @AuthenticationPrincipal AuthenticatedUser actor) {
        return service.list(page, size, sort, search, clientId, contractId, status, invoiceDateFrom, invoiceDateTo, actor);
    }

    @PutMapping("/{id}") @PreAuthorize(MANAGERS)
    @Operation(summary = "Replace a DRAFT invoice", description = "Requires the last-read version. Validated business data is immutable.")
    public InvoiceResponse update(@PathVariable UUID id, @Valid @RequestBody InvoiceUpdateRequest request,
                                  @AuthenticationPrincipal AuthenticatedUser actor) {
        return service.update(id, request, actor);
    }

    @PostMapping("/{id}/validate") @PreAuthorize(MANAGERS)
    @Operation(summary = "Validate, number and render an invoice", description = "Rechecks client, contract and products, recalculates every amount, captures snapshots, allocates the final FA-YEAR-SEQUENCE number and stores the PDF in one rollback-safe transaction.")
    public InvoiceResponse validate(@PathVariable UUID id, @Valid @RequestBody InvoiceVersionRequest request,
                                    @AuthenticationPrincipal AuthenticatedUser actor) {
        return service.validate(id, request, actor);
    }

    @PostMapping("/{id}/mark-paid") @PreAuthorize(MANAGERS)
    @Operation(summary = "Mark a VALIDATED invoice as PAID")
    public InvoiceResponse markPaid(@PathVariable UUID id, @Valid @RequestBody InvoiceVersionRequest request,
                                    @AuthenticationPrincipal AuthenticatedUser actor) {
        return service.markPaid(id, request, actor);
    }

    @PostMapping("/{id}/cancel") @PreAuthorize(MANAGERS)
    @Operation(summary = "Cancel an invoice", description = "Cancellation is audit-preserving and never releases an allocated invoice number.")
    public InvoiceResponse cancel(@PathVariable UUID id, @Valid @RequestBody InvoiceVersionRequest request,
                                  @AuthenticationPrincipal AuthenticatedUser actor) {
        return service.cancel(id, request, actor);
    }

    @GetMapping("/{id}/pdf")
    @Operation(summary = "Download the generated invoice PDF", description = "Available to every authenticated role. The server resolves only the normalized path stored during validation.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "PDF download"), @ApiResponse(responseCode = "401", description = "Authentication required"), @ApiResponse(responseCode = "404", description = "Invoice or PDF not found")})
    public ResponseEntity<PathResource> downloadPdf(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser actor) {
        InvoicePdfDownload download = service.downloadPdf(id, actor);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(download.size())
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(download.filename()).build().toString())
                .body(new PathResource(download.path()));
    }
}
