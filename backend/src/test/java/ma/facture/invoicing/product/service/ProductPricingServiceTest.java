package ma.facture.invoicing.product.service;

import ma.facture.invoicing.product.exception.ProductValidationException;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;

class ProductPricingServiceTest {
    private final ProductPricingService pricing = new ProductPricingService();

    @Test void normalizesFinancialValuesWithHalfUpRounding() {
        assertThat(pricing.normalizePrice(new BigDecimal("12.345"))).isEqualByComparingTo("12.35");
        assertThat(pricing.normalizePrice(BigDecimal.ZERO)).isEqualByComparingTo("0.00");
        assertThat(pricing.normalizeVat(new BigDecimal("19.995"))).isEqualByComparingTo("20.00");
        assertThat(pricing.normalizeVat(new BigDecimal("100"))).isEqualByComparingTo("100.00");
    }

    @Test void calculatesTtcPreviewIncludingRoundingEdges() {
        assertThat(pricing.includingTax(new BigDecimal("13.00"), new BigDecimal("20.00"))).isEqualByComparingTo("15.60");
        assertThat(pricing.includingTax(new BigDecimal("10.01"), new BigDecimal("0.00"))).isEqualByComparingTo("10.01");
        assertThat(pricing.includingTax(new BigDecimal("0.05"), new BigDecimal("100.00"))).isEqualByComparingTo("0.10");
    }

    @Test void rejectsInvalidPriceVatAndPrecision() {
        assertThatThrownBy(() -> pricing.normalizePrice(null)).isInstanceOf(ProductValidationException.class);
        assertThatThrownBy(() -> pricing.normalizePrice(new BigDecimal("-0.01"))).isInstanceOf(ProductValidationException.class);
        assertThatThrownBy(() -> pricing.normalizePrice(new BigDecimal("100000000000000000"))).isInstanceOf(ProductValidationException.class);
        assertThatThrownBy(() -> pricing.normalizeVat(null)).isInstanceOf(ProductValidationException.class);
        assertThatThrownBy(() -> pricing.normalizeVat(new BigDecimal("-0.01"))).isInstanceOf(ProductValidationException.class);
        assertThatThrownBy(() -> pricing.normalizeVat(new BigDecimal("100.01"))).isInstanceOf(ProductValidationException.class);
    }
}
