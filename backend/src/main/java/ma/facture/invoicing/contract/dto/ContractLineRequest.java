package ma.facture.invoicing.contract.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.UUID;

@Schema(description = "Negotiated terms for one unique product in the contract; financial values are normalized with HALF_UP")
public record ContractLineRequest(
        @Schema(description = "Existing line ID for DRAFT reconciliation; omit on creation") UUID id,
        @NotNull(message = "Le produit est requis") @Schema(example = "8cc7d2de-5c3d-4d46-8a9d-94fd831994ef") UUID productId,
        @NotNull(message = "Le prix unitaire négocié est requis")
        @DecimalMin(value = "0", message = "Le prix unitaire négocié doit être positif ou nul")
        @DecimalMax(value = "99999999999999999.99", message = "Le prix unitaire négocié dépasse la précision prise en charge") @Schema(example = "13.00") BigDecimal negotiatedUnitPriceExcludingTax,
        @NotNull(message = "Le taux de TVA est requis") @DecimalMin(value = "0", message = "Le taux de TVA doit être compris entre 0 et 100")
        @DecimalMax(value = "100", message = "Le taux de TVA doit être compris entre 0 et 100") @Schema(example = "20.00") BigDecimal vatRate,
        @DecimalMin(value = "0", message = "Le taux de remise doit être compris entre 0 et 100")
        @DecimalMax(value = "100", message = "Le taux de remise doit être compris entre 0 et 100") @Schema(example = "5.00", description = "Valeur du contrat utilisée par défaut si elle est omise") BigDecimal discountRate,
        @DecimalMin(value = "0.0001", inclusive = true, message = "La quantité minimale doit être supérieure à zéro")
        @DecimalMax(value = "9999999999999999.999", message = "La quantité minimale dépasse la précision prise en charge") @Schema(example = "100.000") BigDecimal minimumQuantity,
        @Size(max = 1000, message = "Les notes de la ligne ne doivent pas dépasser 1 000 caractères") String notes,
        @Min(value = 0, message = "La version de la ligne ne doit pas être négative") @Schema(description = "Requise pour une ligne existante") Long version) {}
