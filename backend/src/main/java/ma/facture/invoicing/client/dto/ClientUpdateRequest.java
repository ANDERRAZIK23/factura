package ma.facture.invoicing.client.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

@Schema(description = "Editable client data with the version last observed by the caller")
public record ClientUpdateRequest(
        @NotBlank(message = "Le nom du client est requis")
        @Size(min = 2, max = 200, message = "Le nom du client doit contenir entre 2 et 200 caractères")
        @Pattern(regexp = "^\\s*\\S(?:.*\\S)\\s*$", message = "Le nom du client doit contenir au moins 2 caractères non blancs") @Schema(example = "Nom du client mis à jour") String clientName,
        @Size(max = 30, message = "Le numéro de téléphone ne doit pas dépasser 30 caractères")
        @Pattern(regexp = "^[+0-9() \\-]*$", message = "Le numéro de téléphone contient des caractères invalides") String phoneNumber,
        @Size(max = 500, message = "L’adresse ne doit pas dépasser 500 caractères") String address,
        @Pattern(regexp = "^\\s*$|^\\s*[^\\s@]+@[^\\s@]+\\.[^\\s@]+\\s*$", message = "L’adresse e-mail doit être valide")
        @Size(max = 254, message = "L’adresse e-mail ne doit pas dépasser 254 caractères") String email,
        @Pattern(regexp = "^\\s*$|^\\s*(?:\\d\\s*){15}$", message = "L’ICE doit contenir exactement 15 chiffres") String ice,
        @Min(value = 0, message = "La version ne doit pas être négative") @Schema(example = "0", description = "Dernière version lue par l’appelant") long version) {}
