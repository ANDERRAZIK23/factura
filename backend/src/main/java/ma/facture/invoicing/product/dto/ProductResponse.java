package ma.facture.invoicing.product.dto;

import ma.facture.invoicing.product.entity.ProductUnit;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ProductResponse(UUID id, String productCode, String productName, String description,
                              BigDecimal unitPriceExcludingTax, BigDecimal defaultVatRate,
                              BigDecimal unitPriceIncludingTax, ProductUnit unit, boolean active,
                              Instant createdAt, Instant updatedAt, UUID createdBy, UUID updatedBy, long version) {}
