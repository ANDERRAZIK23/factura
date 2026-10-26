package ma.facture.invoicing.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import ma.facture.invoicing.user.*;
import org.junit.jupiter.api.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class JwtServiceTest {
    private static final String SECRET = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private final Instant now = Instant.parse("2026-08-03T00:00:00Z");
    private final java.time.Clock clock = java.time.Clock.fixed(now, ZoneOffset.UTC);
    private final SecurityProperties properties = properties("issuer", "audience");
    private final UserEntity user = new UserEntity("Admin", "admin@example.com", "hash", UserRole.ADMIN);

    @Test void createsAndValidatesAccessToken() {
        var service = new JwtService(properties, clock); var principal = service.validate(service.createAccessToken(user));
        assertThat(principal.email()).isEqualTo("admin@example.com"); assertThat(principal.role()).isEqualTo(UserRole.ADMIN);
        assertThat(service.expiresInSeconds()).isEqualTo(900);
    }
    @Test void rejectsExpiredToken() { assertThatThrownBy(() -> service().validate(token("issuer","audience",now.minusSeconds(1), SECRET))).isInstanceOf(ExpiredJwtException.class); }
    @Test void rejectsInvalidSignature() { assertThatThrownBy(() -> service().validate(token("issuer","audience",now.plusSeconds(60), "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789"))).isInstanceOf(JwtException.class); }
    @Test void rejectsInvalidIssuer() { assertThatThrownBy(() -> service().validate(token("other","audience",now.plusSeconds(60), SECRET))).isInstanceOf(IncorrectClaimException.class); }
    @Test void rejectsInvalidAudience() { assertThatThrownBy(() -> service().validate(token("issuer","other",now.plusSeconds(60), SECRET))).isInstanceOf(IncorrectClaimException.class); }

    private JwtService service() { return new JwtService(properties, clock); }
    private String token(String issuer, String audience, Instant expiry, String secret) {
        return Jwts.builder().subject(UUID.randomUUID().toString()).issuer(issuer).audience().add(audience).and()
                .expiration(Date.from(expiry)).claim("email","a@b.com").claim("role","ADMIN")
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8))).compact();
    }
    private static SecurityProperties properties(String issuer, String audience) {
        return new SecurityProperties(SECRET, issuer, audience, Duration.ofMinutes(15), Duration.ofDays(30), List.of("http://localhost:5173"), false, "", "", "");
    }
}
