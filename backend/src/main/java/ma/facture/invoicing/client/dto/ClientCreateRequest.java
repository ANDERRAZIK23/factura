package ma.facture.invoicing.client.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

@Schema(description = "Editable data for a new client; the client code and status are server managed")
public record ClientCreateRequest(
        @NotBlank(message = "Le nom du client est requis")
        @Size(min = 2, max = 200, message = "Le nom du client doit contenir entre 2 et 200 caractères")
        @Pattern(regexp = "^\\s*\\S(?:.*\\S)\\s*$", message = "Le nom du client doit contenir au moins 2 caractères non blancs")
        @Schema(example = "BENELHOU FRERES SARL") String clientName,
        @Size(max = 30, message = "Le numéro de téléphone ne doit pas dépasser 30 caractères")
        @Pattern(regexp = "^[+0-9() \\-]*$", message = "Le numéro de téléphone contient des caractères invalides")
        @Schema(example = "+212 5 00 00 00 00") String phoneNumber,
        @Size(max = 500, message = "L’adresse ne doit pas dépasser 500 caractères") @Schema(example = "Casablanca, Maroc") String address,
        @Pattern(regexp = "^\\s*$|^\\s*[^\\s@]+@[^\\s@]+\\.[^\\s@]+\\s*$", message = "L’adresse e-mail doit être valide")
        @Size(max = 254, message = "L’adresse e-mail ne doit pas dépasser 254 caractères") @Schema(example = "contact@example.com") String email,
        @Pattern(regexp = "^\\s*$|^\\s*(?:\\d\\s*){15}$", message = "L’ICE doit contenir exactement 15 chiffres") @Schema(example = "001527304000014") String ice) {}
