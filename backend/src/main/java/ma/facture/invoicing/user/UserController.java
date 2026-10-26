package ma.facture.invoicing.user;

import jakarta.validation.Valid;
import org.springframework.data.domain.*;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
import ma.facture.invoicing.security.AuthenticatedUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

@RestController @RequestMapping("/api/v1/users") @PreAuthorize("hasAnyRole('ADMIN','APP_ADMIN')")
public class UserController {
    private final UserService service;
    public UserController(UserService service) { this.service = service; }
    @GetMapping public Page<UserDtos.Response> list(@PageableDefault(size=20, sort="createdAt", direction=Sort.Direction.DESC) Pageable pageable, @AuthenticationPrincipal AuthenticatedUser actor) { return service.list(pageable, actor); }
    @GetMapping("/{id}") public UserDtos.Response get(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser actor) { return service.get(id, actor); }
    @PostMapping public ResponseEntity<UserDtos.Response> create(@Valid @RequestBody UserDtos.CreateRequest request, @AuthenticationPrincipal AuthenticatedUser actor) { return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request, actor)); }
    @PutMapping("/{id}") public UserDtos.Response update(@PathVariable UUID id, @Valid @RequestBody UserDtos.UpdateRequest request, @AuthenticationPrincipal AuthenticatedUser actor) { return service.update(id, request, actor); }
    @PatchMapping("/{id}/status") public UserDtos.Response status(@PathVariable UUID id, @Valid @RequestBody UserDtos.StatusRequest request, @AuthenticationPrincipal AuthenticatedUser actor) { return service.setStatus(id, request.enabled(), actor); }
}
