package ma.facture.invoicing.product.dto;

import jakarta.validation.constraints.*;
import ma.facture.invoicing.product.entity.ProductUnit;
import java.math.BigDecimal;

public record ProductUpdateRequest(
        @NotBlank(message = "Le nom du produit est requis") @Size(max = 200, message = "Le nom du produit ne doit pas dépasser 200 caractères") String productName,
        @Size(max = 1000, message = "La description ne doit pas dépasser 1 000 caractères") String description,
        @NotNull(message = "Le prix unitaire HT est requis") @DecimalMin(value = "0", message = "Le prix unitaire HT doit être positif ou nul")
        @DecimalMax(value = "99999999999999999.99", message = "Le prix unitaire HT dépasse la précision prise en charge") BigDecimal unitPriceExcludingTax,
        @NotNull(message = "Le taux de TVA par défaut est requis") @DecimalMin(value = "0", message = "Le taux de TVA par défaut doit être compris entre 0 et 100")
        @DecimalMax(value = "100", message = "Le taux de TVA par défaut doit être compris entre 0 et 100") BigDecimal defaultVatRate,
        @NotNull(message = "L’unité est requise") ProductUnit unit,
        @Min(value = 0, message = "La version ne doit pas être négative") long version) {}
