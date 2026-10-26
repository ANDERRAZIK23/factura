package ma.facture.invoicing.invoice.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.UUID;

public record InvoiceLineRequest(
        @NotNull(message = "Le produit est requis") UUID productId,
        @NotNull(message = "La quantité est requise")
        @DecimalMin(value = "0", inclusive = false, message = "La quantité doit être supérieure à zéro") BigDecimal quantity,
        @DecimalMin(value = "0", message = "Le prix unitaire doit être positif ou nul") BigDecimal unitPriceExcludingTax,
        @DecimalMin(value = "0", message = "Le taux de TVA doit être compris entre 0 et 100")
        @DecimalMax(value = "100", message = "Le taux de TVA doit être compris entre 0 et 100") BigDecimal vatRate,
        @DecimalMin(value = "0", message = "Le taux de remise doit être compris entre 0 et 100")
        @DecimalMax(value = "100", message = "Le taux de remise doit être compris entre 0 et 100") BigDecimal discountRate) {}
