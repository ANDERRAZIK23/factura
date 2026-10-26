package ma.facture.invoicing.product.dto;

import ma.facture.invoicing.product.entity.ProductUnit;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ProductSummaryResponse(UUID id, String productCode, String productName,
                                     BigDecimal unitPriceExcludingTax, BigDecimal defaultVatRate,
                                     BigDecimal unitPriceIncludingTax, ProductUnit unit, boolean active,
                                     Instant createdAt, Instant updatedAt, long version) {}
