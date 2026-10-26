package ma.facture.invoicing.contract.dto;

import ma.facture.invoicing.product.entity.ProductUnit;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ContractLineResponse(UUID id, UUID productId, String productCode, String productName, ProductUnit unit,
                                   boolean productActive, BigDecimal negotiatedUnitPriceExcludingTax, BigDecimal vatRate,
                                   BigDecimal discountRate, BigDecimal minimumQuantity, BigDecimal unitPriceIncludingTaxPreview,
                                   String notes, Instant createdAt, Instant updatedAt, long version) {}
