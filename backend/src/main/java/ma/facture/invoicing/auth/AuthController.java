package ma.facture.invoicing.auth;

import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import ma.facture.invoicing.security.AuthenticatedUser;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService service; private final RefreshCookieService cookies;
    public AuthController(AuthService service, RefreshCookieService cookies) { this.service = service; this.cookies = cookies; }
    @PostMapping("/login")
    public ResponseEntity<AuthDtos.TokenResponse> login(@Valid @RequestBody AuthDtos.LoginRequest request, HttpServletRequest servletRequest) {
        var session = service.login(request.email(), request.password(), servletRequest.getRemoteAddr(), servletRequest.getHeader("User-Agent"));
        return withCookie(session);
    }
    @PostMapping("/refresh")
    public ResponseEntity<AuthDtos.TokenResponse> refresh(@CookieValue(name=RefreshCookieService.COOKIE_NAME, required=false) String token, HttpServletRequest request) {
        return withCookie(service.refresh(token, request.getRemoteAddr(), request.getHeader("User-Agent")));
    }
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@CookieValue(name=RefreshCookieService.COOKIE_NAME, required=false) String token) {
        service.logout(token); return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookies.clear().toString()).build();
    }
    @GetMapping("/me") public AuthDtos.MeResponse me(@AuthenticationPrincipal AuthenticatedUser user) { return service.me(user.id()); }
    private ResponseEntity<AuthDtos.TokenResponse> withCookie(AuthService.AuthenticatedSession session) {
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookies.create(session.refreshToken()).toString()).body(session.response());
    }
}

