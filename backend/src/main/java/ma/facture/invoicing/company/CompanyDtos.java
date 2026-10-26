package ma.facture.invoicing.company;

import jakarta.validation.constraints.*;
import ma.facture.invoicing.user.UserDtos;
import java.time.Instant;
import java.util.UUID;

public final class CompanyDtos {
    private CompanyDtos() {}

    public record CreateRequest(
            @NotBlank(message = "Le nom de l’entreprise est requis") @Size(max = 200, message = "Le nom de l’entreprise ne doit pas dépasser 200 caractères") String companyName,
            @Size(max = 500, message = "L’adresse ne doit pas dépasser 500 caractères") String address,
            @Size(max = 40, message = "Le téléphone ne doit pas dépasser 40 caractères") String phone,
            @Email(message = "L’adresse e-mail professionnelle doit être valide") @Size(max = 254, message = "L’adresse e-mail professionnelle ne doit pas dépasser 254 caractères") String email,
            @Pattern(regexp = "^$|[0-9]{15}", message = "L’ICE doit contenir exactement 15 chiffres") String ice,
            @NotBlank(message = "Le nom de l’administrateur est requis") @Size(max = 200, message = "Le nom de l’administrateur ne doit pas dépasser 200 caractères") String adminFullName,
            @NotBlank(message = "L’adresse e-mail de l’administrateur est requise") @Email(message = "L’adresse e-mail de l’administrateur doit être valide") @Size(max = 320, message = "L’adresse e-mail de l’administrateur ne doit pas dépasser 320 caractères") String adminEmail,
            @NotBlank(message = "Le mot de passe de l’administrateur est requis") @Pattern(regexp = UserDtos.STRONG_PASSWORD, message = "Le mot de passe doit contenir au moins 12 caractères, avec une majuscule, une minuscule, un chiffre et un symbole") String adminPassword) {}

    public record UpdateRequest(
            @NotBlank(message = "Le nom de l’entreprise est requis") @Size(max = 200, message = "Le nom de l’entreprise ne doit pas dépasser 200 caractères") String companyName,
            @Size(max = 500, message = "L’adresse ne doit pas dépasser 500 caractères") String address,
            @Size(max = 40, message = "Le téléphone ne doit pas dépasser 40 caractères") String phone,
            @Email(message = "L’adresse e-mail professionnelle doit être valide") @Size(max = 254, message = "L’adresse e-mail professionnelle ne doit pas dépasser 254 caractères") String email,
            @Pattern(regexp = "^$|[0-9]{15}", message = "L’ICE doit contenir exactement 15 chiffres") String ice,
            @PositiveOrZero(message = "La version ne doit pas être négative") long version) {}

    public record StatusRequest(boolean enabled, @PositiveOrZero long version) {}

    public record Summary(UUID id, String companyCode, String companyName, boolean enabled) {
        public static Summary from(Company company) {
            return new Summary(company.getId(), company.getCompanyCode(), company.getCompanyName(), company.isEnabled());
        }
    }

    public record Response(UUID id, String companyCode, String companyName, String address, String phone, String email,
                           String ice, boolean enabled, long version, Instant createdAt, Instant updatedAt) {
        public static Response from(Company company) {
            return new Response(company.getId(), company.getCompanyCode(), company.getCompanyName(), company.getAddress(),
                    company.getPhone(), company.getEmail(), company.getIce(), company.isEnabled(), company.getVersion(),
                    company.getCreatedAt(), company.getUpdatedAt());
        }
    }
}
