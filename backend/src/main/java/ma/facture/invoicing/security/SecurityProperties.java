package ma.facture.invoicing.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.time.Duration;
import java.util.List;

@ConfigurationProperties("app.security")
public record SecurityProperties(
        String jwtSecret, String issuer, String audience, Duration accessExpiration,
        Duration refreshExpiration, List<String> allowedOrigins, boolean cookieSecure,
        String initialAdminName, String initialAdminEmail, String initialAdminPassword) {
    public SecurityProperties {
        if (jwtSecret == null || jwtSecret.length() < 64) throw new IllegalArgumentException("JWT_SECRET must contain at least 64 characters");
        if (issuer == null || issuer.isBlank()) throw new IllegalArgumentException("JWT issuer is required");
        if (audience == null || audience.isBlank()) throw new IllegalArgumentException("JWT audience is required");
        if (accessExpiration == null || accessExpiration.isNegative() || accessExpiration.isZero()) throw new IllegalArgumentException("Access expiration must be positive");
        if (refreshExpiration == null || refreshExpiration.isNegative() || refreshExpiration.isZero()) throw new IllegalArgumentException("Refresh expiration must be positive");
        allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
    }
}

