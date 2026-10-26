package ma.facture.invoicing.auth;

import jakarta.validation.constraints.*;
import ma.facture.invoicing.user.*;
import java.util.UUID;

public final class AuthDtos {
    private AuthDtos() {}
    public record LoginRequest(@NotBlank(message = "L’adresse e-mail est requise") String email,
                               @NotBlank(message = "Le mot de passe est requis") String password) {}
    public record SessionUser(UUID id, UUID companyId, String fullName, String email, UserRole role) {
        static SessionUser from(UserEntity user) { return new SessionUser(user.getId(), user.getCompanyId(), user.getFullName(), user.getEmail(), user.getRole()); }
    }
    public record TokenResponse(String accessToken, String tokenType, long expiresIn, SessionUser user) {}
    public record MeResponse(UUID id, UUID companyId, String fullName, String email, UserRole role, boolean enabled) {
        static MeResponse from(UserEntity user) { return new MeResponse(user.getId(), user.getCompanyId(), user.getFullName(), user.getEmail(), user.getRole(), user.isEnabled()); }
    }
}
