package ma.facture.invoicing.contract.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import ma.facture.invoicing.contract.entity.PaymentMethod;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Schema(description = "Complete editable DRAFT aggregate; code, status and audit data cannot be supplied")
public record ContractUpdateRequest(
        @NotNull(message = "Le client est requis") UUID clientId,
        @NotNull(message = "La date de début est requise") LocalDate startDate,
        LocalDate endDate,
        @NotNull(message = "Le mode de paiement est requis") PaymentMethod paymentMethod,
        @DecimalMin(value = "0", message = "Le taux de remise par défaut doit être compris entre 0 et 100")
        @DecimalMax(value = "100", message = "Le taux de remise par défaut doit être compris entre 0 et 100") BigDecimal defaultDiscountRate,
        @Size(max = 2000, message = "Les notes du contrat ne doivent pas dépasser 2 000 caractères") String notes,
        @Valid List<ContractLineRequest> lines,
        @Min(value = 0, message = "La version ne doit pas être négative") @Schema(example = "0", description = "Dernière version lue par l’appelant") long version) {}
