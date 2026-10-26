package ma.facture.invoicing.product.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import ma.facture.invoicing.product.dto.*;
import ma.facture.invoicing.product.service.ProductService;
import ma.facture.invoicing.security.AuthenticatedUser;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import java.math.BigDecimal;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/products")
@Tag(name = "Products", description = "Product catalogue management without physical deletion")
public class ProductController {
    private static final String WRITERS = "hasAnyRole('APP_ADMIN','ADMIN','INVOICE_MANAGER')";
    private final ProductService service;
    public ProductController(ProductService service) { this.service = service; }

    @PostMapping @PreAuthorize(WRITERS)
    @Operation(summary = "Create a product", description = "Generates a concurrency-safe PRD-000001 style code and normalizes financial values")
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody ProductCreateRequest request, @AuthenticationPrincipal AuthenticatedUser actor) {
        ProductResponse response = service.create(request, actor);
        var location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/{id}") @Operation(summary = "Get a product")
    public ProductResponse get(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser actor) { return service.get(id, actor); }

    @GetMapping @Operation(summary = "List products", description = "Supports search, status, unit and price filters with an approved sort allowlist")
    public ProductPageResponse list(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
                                    @RequestParam(defaultValue = "productName,asc") String sort, @RequestParam(required = false) String search,
                                    @RequestParam(required = false) Boolean active, @RequestParam(required = false) String unit,
                                    @RequestParam(required = false) BigDecimal minPrice, @RequestParam(required = false) BigDecimal maxPrice,
                                    @AuthenticationPrincipal AuthenticatedUser actor) {
        return service.list(page, size, sort, search, active, unit, minPrice, maxPrice, actor);
    }

    @PutMapping("/{id}") @PreAuthorize(WRITERS)
    @Operation(summary = "Update editable product data", description = "Product code and status cannot be changed; stale versions return 409")
    public ProductResponse update(@PathVariable UUID id, @Valid @RequestBody ProductUpdateRequest request, @AuthenticationPrincipal AuthenticatedUser actor) {
        return service.update(id, request, actor);
    }

    @PatchMapping("/{id}/status") @PreAuthorize(WRITERS)
    @Operation(summary = "Activate or deactivate a product", description = "Idempotent when the requested status is already current")
    public ProductResponse status(@PathVariable UUID id, @Valid @RequestBody ProductStatusRequest request, @AuthenticationPrincipal AuthenticatedUser actor) {
        return service.updateStatus(id, request, actor);
    }
}
