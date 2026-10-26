package ma.facture.invoicing.contract.service;

import ma.facture.invoicing.contract.exception.ContractValidationException;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;

class ContractPricingServiceTest {
    private final ContractPricingService pricing = new ContractPricingService();

    @Test void normalizesEveryFinancialValueHalfUp() {
        assertThat(pricing.normalizePrice(new BigDecimal("13.005"))).isEqualByComparingTo("13.01");
        assertThat(pricing.normalizeRate(new BigDecimal("19.995"), "VAT rate")).isEqualByComparingTo("20.00");
        assertThat(pricing.normalizeQuantity(new BigDecimal("1.2345"))).isEqualByComparingTo("1.235");
        assertThat(pricing.normalizeQuantity(null)).isNull();
    }

    @Test void calculatesTtcAtZeroOneHundredAndRoundingEdge() {
        assertThat(pricing.includingTax(new BigDecimal("13.01"), BigDecimal.ZERO.setScale(2))).isEqualByComparingTo("13.01");
        assertThat(pricing.includingTax(new BigDecimal("13.01"), new BigDecimal("100.00"))).isEqualByComparingTo("26.02");
        assertThat(pricing.includingTax(new BigDecimal("0.03"), new BigDecimal("20.00"))).isEqualByComparingTo("0.04");
    }

    @Test void rejectsMissingNegativeOutOfRangeAndPrecisionOverflow() {
        assertThatThrownBy(() -> pricing.normalizePrice(null)).isInstanceOf(ContractValidationException.class);
        assertThatThrownBy(() -> pricing.normalizePrice(new BigDecimal("-0.01"))).isInstanceOf(ContractValidationException.class);
        assertThatThrownBy(() -> pricing.normalizePrice(new BigDecimal("100000000000000000"))).isInstanceOf(ContractValidationException.class);
        assertThatThrownBy(() -> pricing.normalizeRate(null, "VAT rate")).isInstanceOf(ContractValidationException.class);
        assertThatThrownBy(() -> pricing.normalizeRate(new BigDecimal("-1"), "VAT rate")).isInstanceOf(ContractValidationException.class);
        assertThatThrownBy(() -> pricing.normalizeRate(new BigDecimal("101"), "VAT rate")).isInstanceOf(ContractValidationException.class);
        assertThatThrownBy(() -> pricing.normalizeQuantity(BigDecimal.ZERO)).isInstanceOf(ContractValidationException.class);
        assertThatThrownBy(() -> pricing.normalizeQuantity(new BigDecimal("10000000000000000"))).isInstanceOf(ContractValidationException.class);
    }
}
