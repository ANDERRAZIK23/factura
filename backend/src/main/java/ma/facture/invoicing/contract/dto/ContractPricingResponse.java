package ma.facture.invoicing.contract.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import ma.facture.invoicing.contract.entity.*;
import ma.facture.invoicing.product.entity.ProductUnit;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Schema(description = "Read-only negotiated terms returned only for a currently usable ACTIVE contract")
public record ContractPricingResponse(UUID contractId, @Schema(example = "CTR-2026-000001") String contractCode, ContractClientResponse client,
                                      PaymentMethod paymentMethod, LocalDate startDate, LocalDate endDate,
                                      ContractStatus status, boolean usable, List<ProductPricing> products) {
    public record ProductPricing(UUID productId, String productCode, String productName, ProductUnit unit,
                                 BigDecimal negotiatedUnitPriceExcludingTax, BigDecimal vatRate,
                                 BigDecimal discountRate, BigDecimal minimumQuantity,
                                 BigDecimal unitPriceIncludingTaxPreview) {}
}
