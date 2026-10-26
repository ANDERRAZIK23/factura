package ma.facture.invoicing.auth;

import ma.facture.invoicing.common.AuthenticationException;
import ma.facture.invoicing.security.*;
import ma.facture.invoicing.user.*;
import ma.facture.invoicing.company.CompanyRepository;
import org.slf4j.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
public class AuthService {
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private final UserRepository users; private final PasswordService passwords; private final JwtService jwt; private final RefreshTokenService refreshTokens; private final CompanyRepository companies;
    public record AuthenticatedSession(AuthDtos.TokenResponse response, String refreshToken) {}
    public AuthService(UserRepository users, PasswordService passwords, JwtService jwt, RefreshTokenService refreshTokens, CompanyRepository companies) {
        this.users = users; this.passwords = passwords; this.jwt = jwt; this.refreshTokens = refreshTokens; this.companies = companies;
    }
    @Transactional
    public AuthenticatedSession login(String email, String password, String ip, String agent) {
        var user = users.findByEmailIgnoreCase(UserService.normalizeEmail(email)).orElse(null);
        if (user == null || !user.isEnabled() || !companies.findById(user.getCompanyId()).filter(company -> company.isEnabled()).isPresent()
                || !passwords.matches(password, user.getPasswordHash())) {
            log.warn("Authentication failure from ip={}", ip); throw AuthenticationException.invalidCredentials();
        }
        var refresh = refreshTokens.issue(user, UUID.randomUUID(), ip, agent); return session(user, refresh.rawToken());
    }
    @Transactional(noRollbackFor = AuthenticationException.class)
    public AuthenticatedSession refresh(String raw, String ip, String agent) {
        if (raw == null || raw.isBlank()) throw AuthenticationException.invalidSession();
        var replacement = refreshTokens.rotate(raw, ip, agent); return session(replacement.entity().getUser(), replacement.rawToken());
    }
    @Transactional public void logout(String raw) { if (raw != null && !raw.isBlank()) refreshTokens.revoke(raw); }
    @Transactional(readOnly=true)
    public AuthDtos.MeResponse me(UUID id) {
        var user = users.findById(id).filter(UserEntity::isEnabled).orElseThrow(AuthenticationException::invalidSession);
        return AuthDtos.MeResponse.from(user);
    }
    private AuthenticatedSession session(UserEntity user, String rawRefresh) {
        return new AuthenticatedSession(new AuthDtos.TokenResponse(jwt.createAccessToken(user), "Bearer", jwt.expiresInSeconds(), AuthDtos.SessionUser.from(user)), rawRefresh);
    }
}
