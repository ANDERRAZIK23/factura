package ma.facture.invoicing.company;

import ma.facture.invoicing.security.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
public class CompanyBrandingController {
    private final CompanyBrandingService service;

    public CompanyBrandingController(CompanyBrandingService service) { this.service = service; }

    @GetMapping("/api/v1/company/branding")
    public CompanyBrandingDtos.Response current(@AuthenticationPrincipal AuthenticatedUser actor) {
        return service.get(actor.companyId());
    }

    @PutMapping(value = "/api/v1/company/branding", consumes = "multipart/form-data")
    @PreAuthorize("hasAnyRole('ADMIN','APP_ADMIN')")
    public CompanyBrandingDtos.Response uploadCurrent(@RequestPart("logo") MultipartFile logo,
                                                       @AuthenticationPrincipal AuthenticatedUser actor) {
        return service.upload(actor.companyId(), logo);
    }

    @DeleteMapping("/api/v1/company/branding")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN','APP_ADMIN')")
    public void removeCurrent(@AuthenticationPrincipal AuthenticatedUser actor) { service.remove(actor.companyId()); }

    @GetMapping("/api/v1/companies/{companyId}/branding")
    @PreAuthorize("hasRole('APP_ADMIN')")
    public CompanyBrandingDtos.Response get(@PathVariable UUID companyId) { return service.get(companyId); }

    @PutMapping(value = "/api/v1/companies/{companyId}/branding", consumes = "multipart/form-data")
    @PreAuthorize("hasRole('APP_ADMIN')")
    public CompanyBrandingDtos.Response upload(@PathVariable UUID companyId, @RequestPart("logo") MultipartFile logo) {
        return service.upload(companyId, logo);
    }

    @DeleteMapping("/api/v1/companies/{companyId}/branding")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('APP_ADMIN')")
    public void remove(@PathVariable UUID companyId) { service.remove(companyId); }
}
