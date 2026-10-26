package ma.facture.invoicing.company;

import jakarta.validation.Valid;
import ma.facture.invoicing.security.AuthenticatedUser;
import ma.facture.invoicing.user.UserDtos;
import org.springframework.data.domain.*;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
public class CompanyController {
    private final CompanyService service;
    public CompanyController(CompanyService service) { this.service = service; }

    @GetMapping("/api/v1/company")
    public CompanyDtos.Response current(@AuthenticationPrincipal AuthenticatedUser actor) { return service.current(actor); }

    @PutMapping("/api/v1/company")
    @PreAuthorize("hasAnyRole('ADMIN','APP_ADMIN')")
    public CompanyDtos.Response updateCurrent(@Valid @RequestBody CompanyDtos.UpdateRequest request,
                                               @AuthenticationPrincipal AuthenticatedUser actor) {
        return service.updateCurrent(request, actor);
    }

    @GetMapping("/api/v1/companies")
    @PreAuthorize("hasRole('APP_ADMIN')")
    public Page<CompanyDtos.Response> list(@PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.list(pageable);
    }

    @GetMapping("/api/v1/companies/{id}")
    @PreAuthorize("hasRole('APP_ADMIN')")
    public CompanyDtos.Response get(@PathVariable UUID id) { return service.get(id); }

    @PostMapping("/api/v1/companies")
    @PreAuthorize("hasRole('APP_ADMIN')")
    public ResponseEntity<CompanyDtos.Response> create(@Valid @RequestBody CompanyDtos.CreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @GetMapping("/api/v1/companies/{id}/users")
    @PreAuthorize("hasRole('APP_ADMIN')")
    public Page<UserDtos.Response> listUsers(@PathVariable UUID id,
                                             @PageableDefault(size = 100, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.listUsers(id, pageable);
    }

    @PostMapping("/api/v1/companies/{id}/users")
    @PreAuthorize("hasRole('APP_ADMIN')")
    public ResponseEntity<UserDtos.Response> createUser(@PathVariable UUID id,
                                                         @Valid @RequestBody UserDtos.CreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createUser(id, request));
    }

    @PatchMapping("/api/v1/companies/{id}/users/{userId}/status")
    @PreAuthorize("hasRole('APP_ADMIN')")
    public UserDtos.Response userStatus(@PathVariable UUID id, @PathVariable UUID userId,
                                         @Valid @RequestBody UserDtos.StatusRequest request) {
        return service.setUserStatus(id, userId, request);
    }

    @PutMapping("/api/v1/companies/{id}")
    @PreAuthorize("hasRole('APP_ADMIN')")
    public CompanyDtos.Response update(@PathVariable UUID id, @Valid @RequestBody CompanyDtos.UpdateRequest request) {
        return service.update(id, request);
    }

    @PatchMapping("/api/v1/companies/{id}/status")
    @PreAuthorize("hasRole('APP_ADMIN')")
    public CompanyDtos.Response status(@PathVariable UUID id, @Valid @RequestBody CompanyDtos.StatusRequest request,
                                       @AuthenticationPrincipal AuthenticatedUser actor) {
        return service.setStatus(id, request, actor);
    }
}
