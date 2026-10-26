package ma.facture.invoicing.portal;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import ma.facture.invoicing.security.AuthenticatedUser;
import org.springframework.core.io.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/api/v1")
public class ClientPortalController {
    private static final String MANAGERS = "hasAnyRole('APP_ADMIN','ADMIN','INVOICE_MANAGER')";
    private final ClientPortalService service;
    public ClientPortalController(ClientPortalService service) { this.service = service; }
    public record CreateAccess(@Min(1) @Max(90) int days) {}
    @ModelAttribute public void headers(HttpServletResponse response) { response.setHeader("Cache-Control", "no-store"); response.setHeader("Referrer-Policy", "no-referrer"); }
    @GetMapping("/clients/{id}/portal-access") @PreAuthorize(MANAGERS)
    public ClientPortalService.AccessInfo info(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser actor) { return service.info(id, actor); }
    @PostMapping("/clients/{id}/portal-access") @PreAuthorize(MANAGERS)
    public ClientPortalService.NewAccess create(@PathVariable UUID id, @Valid @RequestBody CreateAccess request, @AuthenticationPrincipal AuthenticatedUser actor) { return service.create(id, request.days(), actor); }
    @DeleteMapping("/clients/{id}/portal-access") @PreAuthorize(MANAGERS) @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser actor) { service.revoke(id, actor); }
    @GetMapping("/portal/invoices")
    public ClientPortalService.PortalPage list(@RequestHeader(value = "Authorization", required = false) String authorization, @RequestParam(defaultValue = "0") int page) { return service.list(authorization, page); }
    @GetMapping("/portal/invoices/{id}/payments")
    public List<ClientPortalService.PublicPayment> payments(@RequestHeader(value = "Authorization", required = false) String authorization, @PathVariable UUID id) { return service.payments(authorization, id); }
    @GetMapping(value = "/portal/invoices/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<Resource> pdf(@RequestHeader(value = "Authorization", required = false) String authorization, @PathVariable UUID id) {
        var file = service.pdf(authorization, id);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.APPLICATION_PDF).contentLength(file.size())
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.filename() + "\"").body(new FileSystemResource(file.path()));
    }
}
