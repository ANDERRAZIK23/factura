package ma.facture.invoicing.client.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import ma.facture.invoicing.client.dto.*;
import ma.facture.invoicing.client.service.ClientService;
import ma.facture.invoicing.security.AuthenticatedUser;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/clients")
@Tag(name = "Clients", description = "Client management without physical deletion")
public class ClientController {
    private static final String WRITERS = "hasAnyRole('APP_ADMIN','ADMIN','INVOICE_MANAGER')";
    private final ClientService service;

    public ClientController(ClientService service) { this.service = service; }

    @PostMapping
    @PreAuthorize(WRITERS)
    @Operation(summary = "Create a client", description = "Generates a concurrency-safe CLI-000001 style code")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Client created"),
            @ApiResponse(responseCode = "400", description = "Field validation failed", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "ICE already belongs to another client", content = @Content(schema = @Schema(implementation = ProblemDetail.class)))})
    public ResponseEntity<ClientResponse> create(@Valid @RequestBody ClientCreateRequest request,
                                                  @AuthenticationPrincipal AuthenticatedUser actor) {
        ClientResponse response = service.create(request, actor);
        var location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a client")
    public ClientResponse get(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser actor) { return service.get(id, actor); }

    @GetMapping
    @Operation(summary = "List clients", description = "Searches code, name, phone, email and ICE; size is limited to 100")
    public ClientPageResponse list(@RequestParam(defaultValue = "0") int page,
                                   @RequestParam(defaultValue = "20") int size,
                                   @RequestParam(defaultValue = "clientName,asc") String sort,
                                   @RequestParam(required = false) String search,
                                   @RequestParam(required = false) Boolean active,
                                   @AuthenticationPrincipal AuthenticatedUser actor) {
        return service.list(page, size, sort, search, active, actor);
    }

    @PutMapping("/{id}")
    @PreAuthorize(WRITERS)
    @Operation(summary = "Update editable client data", description = "Returns 409 when the supplied version is stale")
    public ClientResponse update(@PathVariable UUID id, @Valid @RequestBody ClientUpdateRequest request,
                                 @AuthenticationPrincipal AuthenticatedUser actor) {
        return service.update(id, request, actor);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize(WRITERS)
    @Operation(summary = "Activate or deactivate a client", description = "Idempotent when the requested status is already current")
    public ClientResponse status(@PathVariable UUID id, @Valid @RequestBody ClientStatusRequest request,
                                 @AuthenticationPrincipal AuthenticatedUser actor) {
        return service.updateStatus(id, request, actor);
    }
}
