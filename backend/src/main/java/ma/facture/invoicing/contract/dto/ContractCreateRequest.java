package ma.facture.invoicing.contract.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import ma.facture.invoicing.contract.entity.PaymentMethod;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Schema(description = "New DRAFT contract; code, status and audit fields are server managed")
public record ContractCreateRequest(
        @NotNull(message = "Le client est requis") UUID clientId,
        @NotNull(message = "La date de début est requise") LocalDate startDate,
        LocalDate endDate,
        @NotNull(message = "Le mode de paiement est requis") PaymentMethod paymentMethod,
        @DecimalMin(value = "0", message = "Le taux de remise par défaut doit être compris entre 0 et 100")
        @DecimalMax(value = "100", message = "Le taux de remise par défaut doit être compris entre 0 et 100") BigDecimal defaultDiscountRate,
        @Size(max = 2000, message = "Les notes du contrat ne doivent pas dépasser 2 000 caractères") String notes,
        @Valid List<ContractLineRequest> lines) {}
