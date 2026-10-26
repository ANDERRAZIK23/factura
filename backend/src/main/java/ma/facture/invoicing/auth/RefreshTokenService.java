package ma.facture.invoicing.auth;

import ma.facture.invoicing.common.AuthenticationException;
import ma.facture.invoicing.security.SecurityProperties;
import ma.facture.invoicing.user.UserEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class RefreshTokenService {
    private final RefreshTokenRepository repository;
    private final SecurityProperties properties;
    private final SecureRandom random;
    private final Clock clock;
    public record IssuedToken(String rawToken, RefreshTokenEntity entity) {}

    @Autowired
    public RefreshTokenService(RefreshTokenRepository repository, SecurityProperties properties) {
        this(repository, properties, new SecureRandom(), Clock.systemUTC());
    }
    RefreshTokenService(RefreshTokenRepository repository, SecurityProperties properties, SecureRandom random, Clock clock) {
        this.repository = repository; this.properties = properties; this.random = random; this.clock = clock;
    }

    @Transactional
    public IssuedToken issue(UserEntity user, UUID family, String ip, String agent) {
        byte[] bytes = new byte[48]; random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        var entity = new RefreshTokenEntity(user, hash(raw), family, clock.instant().plus(properties.refreshExpiration()), ip, truncate(agent));
        return new IssuedToken(raw, repository.save(entity));
    }

    @Transactional(noRollbackFor = AuthenticationException.class)
    public IssuedToken rotate(String raw, String ip, String agent) {
        var now = clock.instant();
        var existing = repository.findByHashForUpdate(hash(raw)).orElseThrow(AuthenticationException::invalidSession);
        if (existing.isRevoked()) {
            repository.revokeFamily(existing.getTokenFamily(), now);
            throw AuthenticationException.invalidSession();
        }
        if (existing.isExpired(now) || !existing.getUser().isEnabled()) {
            repository.revokeFamily(existing.getTokenFamily(), now);
            throw AuthenticationException.invalidSession();
        }
        var replacement = issue(existing.getUser(), existing.getTokenFamily(), ip, agent);
        existing.revoke(now, replacement.entity());
        repository.save(existing);
        return replacement;
    }

    @Transactional
    public void revoke(String raw) {
        repository.findByHashForUpdate(hash(raw)).ifPresent(token -> {
            if (!token.isRevoked()) { token.revoke(clock.instant(), null); repository.save(token); }
        });
    }

    public String hash(String raw) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 is unavailable", exception); }
    }
    private String truncate(String value) { return value == null ? null : value.substring(0, Math.min(value.length(), 512)); }
}
