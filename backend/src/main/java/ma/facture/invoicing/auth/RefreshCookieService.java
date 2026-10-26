package ma.facture.invoicing.auth;

import ma.facture.invoicing.security.SecurityProperties;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

@Service
public class RefreshCookieService {
    public static final String COOKIE_NAME = "invoice_refresh";
    private final SecurityProperties properties;
    public RefreshCookieService(SecurityProperties properties) { this.properties = properties; }
    public ResponseCookie create(String token) { return base(token).maxAge(properties.refreshExpiration()).build(); }
    public ResponseCookie clear() { return base("").maxAge(0).build(); }
    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(COOKIE_NAME, value).httpOnly(true).secure(properties.cookieSecure())
                .sameSite("Strict").path("/api/v1/auth");
    }
}

