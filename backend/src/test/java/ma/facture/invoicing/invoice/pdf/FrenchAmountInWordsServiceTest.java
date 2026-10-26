package ma.facture.invoicing.invoice.pdf;

import org.junit.jupiter.api.Test;

import java.math.*;

import static org.assertj.core.api.Assertions.*;

class FrenchAmountInWordsServiceTest {
    private final FrenchAmountInWordsService service = new FrenchAmountInWordsService();

    @Test void convertsMoroccanInvoiceExamplesAndCents() {
        assertThat(service.convert(new BigDecimal("31200.00"))).isEqualTo("Trente et un mille deux cents dirhams");
        assertThat(service.convert(new BigDecimal("1.01"))).isEqualTo("Un dirham et un centime");
        assertThat(service.convert(new BigDecimal("0.50"))).isEqualTo("Zéro dirhams et cinquante centimes");
        assertThat(service.convert(new BigDecimal("80.995"))).isEqualTo("Quatre-vingt-un dirhams");
    }

    @Test void appliesFrenchPluralAndTerminalRulesAcrossLargeGroups() {
        assertThat(service.convert(new BigDecimal("200"))).isEqualTo("Deux cents dirhams");
        assertThat(service.convert(new BigDecimal("200000"))).isEqualTo("Deux cent mille dirhams");
        assertThat(service.convert(new BigDecimal("80000"))).isEqualTo("Quatre-vingt mille dirhams");
        assertThat(service.convert(new BigDecimal("2000001"))).isEqualTo("Deux millions un dirhams");
        assertThat(service.convert(new BigDecimal("2000000000000000"))).isEqualTo("Deux billiards dirhams");
        assertThat(service.convert(new BigDecimal("71"))).isEqualTo("Soixante et onze dirhams");
        assertThat(service.convert(new BigDecimal("91"))).isEqualTo("Quatre-vingt-onze dirhams");
    }

    @Test void rejectsMissingNegativeAndUnsupportedAmounts() {
        assertThatThrownBy(() -> service.convert(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.convert(new BigDecimal("-0.01"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.convert(new BigDecimal("1000000000000000000")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("range");
    }
}
