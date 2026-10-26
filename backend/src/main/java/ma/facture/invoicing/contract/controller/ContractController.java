package ma.facture.invoicing.contract.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import ma.facture.invoicing.contract.dto.*;
import ma.facture.invoicing.contract.service.ContractService;
import ma.facture.invoicing.security.AuthenticatedUser;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/contracts")
@Tag(name = "Contracts", description = "Versioned commercial agreements connecting clients to negotiated product terms. ADMIN and INVOICE_MANAGER manage the DRAFT → ACTIVE → EXPIRED/CANCELLED lifecycle; VIEWER is read-only. All endpoints require authentication.")
public class ContractController {
    private static final String MANAGERS = "hasAnyRole('APP_ADMIN','ADMIN','INVOICE_MANAGER')";
    private final ContractService service;
    public ContractController(ContractService service) { this.service = service; }

    @PostMapping @PreAuthorize(MANAGERS)
    @Operation(summary = "Create a DRAFT contract", description = "Generates an immutable CTR-YEAR-SEQUENCE code inside the transaction. Dates must form a valid range; product lines are unique; financial values use HALF_UP normalization and omitted line discounts inherit the contract default.")
    @ApiResponses({@ApiResponse(responseCode = "201", description = "Contract created"), @ApiResponse(responseCode = "400", description = "Validation failure, including duplicate products"), @ApiResponse(responseCode = "401", description = "Authentication required"), @ApiResponse(responseCode = "403", description = "ADMIN or INVOICE_MANAGER role required"), @ApiResponse(responseCode = "404", description = "Client or product not found")})
    public ResponseEntity<ContractResponse> create(@Valid @RequestBody ContractCreateRequest request,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        ContractResponse response = service.create(request, actor);
        var location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/{id}") @Operation(summary = "Get complete contract details", description = "Returns fields, product lines, lifecycle audit and optimistic-lock versions. Historical CANCELLED and EXPIRED contracts remain readable.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Contract found"), @ApiResponse(responseCode = "401", description = "Authentication required"), @ApiResponse(responseCode = "404", description = "Contract not found")})
    public ContractResponse get(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser actor) { return service.get(id, actor); }

    @GetMapping @Operation(summary = "List contracts", description = "Searches code, client and notes; supports client, status, payment, date and current-usability filters. Example: ?page=0&size=20&sort=contractCode,asc&status=ACTIVE&usable=true. Size is limited to 100 and sorting uses an allowlist.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Paginated contract summaries"), @ApiResponse(responseCode = "400", description = "Invalid filter, date range, page size or sort"), @ApiResponse(responseCode = "401", description = "Authentication required")})
    public ContractPageResponse list(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort, @RequestParam(required = false) String search,
            @RequestParam(required = false) UUID clientId, @RequestParam(required = false) String status,
            @RequestParam(required = false) String paymentMethod,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDateTo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDateTo,
            @RequestParam(required = false) Boolean usable, @AuthenticationPrincipal AuthenticatedUser actor) {
        return service.list(page, size, sort, search, clientId, status, paymentMethod, startDateFrom, startDateTo, endDateFrom, endDateTo, usable, actor);
    }

    @PutMapping("/{id}") @PreAuthorize(MANAGERS)
    @Operation(summary = "Update a DRAFT contract", description = "Reconciles owned lines and rejects stale versions, foreign line IDs and immutable statuses. Contract code, lifecycle status and audit fields are server-managed.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "DRAFT updated"), @ApiResponse(responseCode = "400", description = "Validation failure"), @ApiResponse(responseCode = "403", description = "Manager role required"), @ApiResponse(responseCode = "404", description = "Contract not found"), @ApiResponse(responseCode = "409", description = "Stale version, foreign line or immutable contract")})
    public ContractResponse update(@PathVariable UUID id, @Valid @RequestBody ContractUpdateRequest request,
            @AuthenticationPrincipal AuthenticatedUser actor) { return service.update(id, request, actor); }

    @PostMapping("/{id}/activate") @PreAuthorize(MANAGERS)
    @Operation(summary = "Activate a DRAFT contract", description = "Requires at least one line, a non-expired date range and active client/products. Records activation audit and permanently freezes all commercial terms.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Contract activated"), @ApiResponse(responseCode = "404", description = "Contract not found"), @ApiResponse(responseCode = "409", description = "Stale version or invalid transition/activation state")})
    public ContractResponse activate(@PathVariable UUID id, @Valid @RequestBody ContractActivationRequest request,
            @AuthenticationPrincipal AuthenticatedUser actor) { return service.activate(id, request, actor); }

    @PostMapping("/{id}/cancel") @PreAuthorize(MANAGERS)
    @Operation(summary = "Cancel a DRAFT or ACTIVE contract", description = "ACTIVE cancellation requires a reason. Cancellation is audit-recorded, idempotent for an already CANCELLED contract and preserves all historical terms.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Contract cancelled or existing cancellation returned"), @ApiResponse(responseCode = "404", description = "Contract not found"), @ApiResponse(responseCode = "409", description = "Stale version, missing ACTIVE reason or invalid transition")})
    public ContractResponse cancel(@PathVariable UUID id, @Valid @RequestBody ContractCancellationRequest request,
            @AuthenticationPrincipal AuthenticatedUser actor) { return service.cancel(id, request, actor); }

    @GetMapping("/{id}/pricing")
    @Operation(summary = "Get usable contract pricing", description = "Returns negotiated HT prices, VAT, discounts, minimum quantities, payment method and TTC previews only while an ACTIVE contract is inside its inclusive validity period. No invoice is created.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Usable negotiated pricing"), @ApiResponse(responseCode = "404", description = "Contract not found"), @ApiResponse(responseCode = "409", description = "Contract is not ACTIVE or not currently usable")})
    public ContractPricingResponse pricing(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser actor) { return service.pricing(id, actor); }
}
