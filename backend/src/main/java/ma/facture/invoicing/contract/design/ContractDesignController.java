package ma.facture.invoicing.contract.design;

import jakarta.validation.Valid;
import ma.facture.invoicing.security.AuthenticatedUser;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/companies/{companyId}/contract-design")
@PreAuthorize("hasRole('APP_ADMIN')")
public class ContractDesignController {
    private final ContractDesignService service;
    public ContractDesignController(ContractDesignService service) { this.service = service; }
    @GetMapping
    public ContractDesignDtos.Response get(@PathVariable UUID companyId, @AuthenticationPrincipal AuthenticatedUser actor) {
        return service.get(companyId, actor);
    }
    @PutMapping
    public ContractDesignDtos.Response save(@PathVariable UUID companyId, @Valid @RequestBody ContractDesignDtos.Request request,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        return service.save(companyId, request, actor);
    }
    @PostMapping(value = "/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> pdf(@PathVariable UUID companyId, @Valid @RequestBody ContractDesignSettings settings,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=modele-contrat.pdf")
                .body(service.pdf(companyId, settings, actor));
    }
}
