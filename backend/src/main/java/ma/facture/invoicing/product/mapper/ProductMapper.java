package ma.facture.invoicing.product.mapper;

import ma.facture.invoicing.product.dto.*;
import ma.facture.invoicing.product.entity.Product;
import ma.facture.invoicing.product.service.ProductPricingService;
import org.springframework.stereotype.Component;

@Component
public class ProductMapper {
    private final ProductPricingService pricing;
    public ProductMapper(ProductPricingService pricing) { this.pricing = pricing; }
    public ProductResponse toResponse(Product product) {
        return new ProductResponse(product.getId(), product.getProductCode(), product.getProductName(), product.getDescription(),
                product.getUnitPriceExcludingTax(), product.getDefaultVatRate(), pricing.includingTax(product.getUnitPriceExcludingTax(), product.getDefaultVatRate()),
                product.getUnit(), product.isActive(), product.getCreatedAt(), product.getUpdatedAt(), product.getCreatedBy(), product.getUpdatedBy(), product.getVersion());
    }
    public ProductSummaryResponse toSummary(Product product) {
        return new ProductSummaryResponse(product.getId(), product.getProductCode(), product.getProductName(), product.getUnitPriceExcludingTax(),
                product.getDefaultVatRate(), pricing.includingTax(product.getUnitPriceExcludingTax(), product.getDefaultVatRate()), product.getUnit(),
                product.isActive(), product.getCreatedAt(), product.getUpdatedAt(), product.getVersion());
    }
}
