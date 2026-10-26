package ma.facture.invoicing.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import ma.facture.invoicing.user.UserEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {
    private final SecurityProperties properties;
    private final Clock clock;
    private final SecretKey key;

    @Autowired
    public JwtService(SecurityProperties properties) { this(properties, Clock.systemUTC()); }
    JwtService(SecurityProperties properties, Clock clock) {
        this.properties = properties; this.clock = clock;
        key = Keys.hmacShaKeyFor(properties.jwtSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String createAccessToken(UserEntity user) {
        var now = clock.instant();
        return Jwts.builder().subject(user.getId().toString()).issuer(properties.issuer())
                .audience().add(properties.audience()).and().issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(properties.accessExpiration())))
                .claim("email", user.getEmail()).claim("role", user.getRole().name()).claim("companyId", user.getCompanyId().toString())
                .signWith(key).compact();
    }

    public AuthenticatedUser validate(String token) {
        Claims claims = Jwts.parser().verifyWith(key).requireIssuer(properties.issuer())
                .requireAudience(properties.audience()).clock(() -> Date.from(clock.instant())).build()
                .parseSignedClaims(token).getPayload();
        return new AuthenticatedUser(UUID.fromString(claims.getSubject()), claims.get("email", String.class),
                ma.facture.invoicing.user.UserRole.valueOf(claims.get("role", String.class)),
                UUID.fromString(claims.get("companyId", String.class)));
    }

    public long expiresInSeconds() { return properties.accessExpiration().toSeconds(); }
}
