package ma.facture.invoicing.user;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;

public final class UserDtos {
    private UserDtos() {}
    public static final String STRONG_PASSWORD = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{12,128}$";
    public record CreateRequest(@NotBlank(message="Le nom complet est requis") @Size(max=200, message="Le nom complet ne doit pas dépasser 200 caractères") String fullName, @NotBlank(message="L’adresse e-mail est requise") @Email(message="L’adresse e-mail doit être valide") @Size(max=320, message="L’adresse e-mail ne doit pas dépasser 320 caractères") String email,
                                @NotBlank(message="Le mot de passe est requis") @Pattern(regexp=STRONG_PASSWORD, message="Le mot de passe doit contenir au moins 12 caractères, avec une majuscule, une minuscule, un chiffre et un symbole") String password,
                                @NotNull(message="Le rôle est requis") UserRole role) {}
    public record UpdateRequest(@NotBlank(message="Le nom complet est requis") @Size(max=200, message="Le nom complet ne doit pas dépasser 200 caractères") String fullName, @NotBlank(message="L’adresse e-mail est requise") @Email(message="L’adresse e-mail doit être valide") @Size(max=320, message="L’adresse e-mail ne doit pas dépasser 320 caractères") String email,
                                @Pattern(regexp=STRONG_PASSWORD, message="Le mot de passe doit contenir au moins 12 caractères, avec une majuscule, une minuscule, un chiffre et un symbole") String password,
                                @NotNull(message="Le rôle est requis") UserRole role) {}
    public record StatusRequest(boolean enabled) {}
    public record Response(UUID id, UUID companyId, String fullName, String email, UserRole role, boolean enabled, long version, Instant createdAt, Instant updatedAt) {
        public static Response from(UserEntity user) { return new Response(user.getId(), user.getCompanyId(), user.getFullName(), user.getEmail(), user.getRole(), user.isEnabled(), user.getVersion(), user.getCreatedAt(), user.getUpdatedAt()); }
    }
}
