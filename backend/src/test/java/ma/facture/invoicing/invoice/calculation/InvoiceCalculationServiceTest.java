package ma.facture.invoicing.invoice.calculation;

import ma.facture.invoicing.invoice.exception.InvoiceCalculationException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InvoiceCalculationServiceTest {
    private final InvoiceCalculationService service = new InvoiceCalculationService();

    @Test void calculatesAndSumsLinesUsingStoredMonetaryRounding() {
        InvoiceCalculation result = service.calculate(List.of(
                line("2", "100", "20", "10"),
                line("1.005", "10.005", "19.995", "12.345")
        ));

        assertThat(result.lines()).hasSize(2);
        assertThat(result.lines().getFirst()).isEqualTo(new InvoiceLineCalculation(
                decimal("2.000"), decimal("100.00"), decimal("20.00"), decimal("10.00"),
                decimal("200.00"), decimal("20.00"), decimal("180.00"), decimal("36.00"), decimal("216.00")
        ));
        assertThat(result.lines().get(1)).isEqualTo(new InvoiceLineCalculation(
                decimal("1.005"), decimal("10.01"), decimal("20.00"), decimal("12.35"),
                decimal("10.06"), decimal("1.24"), decimal("8.82"), decimal("1.76"), decimal("10.58")
        ));
        assertThat(result.subtotalExcludingTax()).isEqualByComparingTo("210.06");
        assertThat(result.totalDiscount()).isEqualByComparingTo("21.24");
        assertThat(result.taxableAmount()).isEqualByComparingTo("188.82");
        assertThat(result.totalVat()).isEqualByComparingTo("37.76");
        assertThat(result.totalIncludingTax()).isEqualByComparingTo("226.58");
    }

    @Test void supportsZeroTaxFreeLinesAndDefaultsMissingDiscountToZero() {
        InvoiceLineCalculation result = service.calculateLine(line("1", "0", "0", null));

        assertThat(result.quantity()).isEqualByComparingTo("1.000");
        assertThat(result.discountRate()).isEqualByComparingTo("0.00");
        assertThat(result.grossAmountExcludingTax()).isEqualByComparingTo("0.00");
        assertThat(result.discountAmount()).isEqualByComparingTo("0.00");
        assertThat(result.netAmountExcludingTax()).isEqualByComparingTo("0.00");
        assertThat(result.vatAmount()).isEqualByComparingTo("0.00");
        assertThat(result.totalIncludingTax()).isEqualByComparingTo("0.00");
    }

    @Test void supportsFullDiscountWithoutCreatingNegativeAmounts() {
        InvoiceLineCalculation result = service.calculateLine(line("3", "7.50", "100", "100"));

        assertThat(result.grossAmountExcludingTax()).isEqualByComparingTo("22.50");
        assertThat(result.discountAmount()).isEqualByComparingTo("22.50");
        assertThat(result.netAmountExcludingTax()).isEqualByComparingTo("0.00");
        assertThat(result.vatAmount()).isEqualByComparingTo("0.00");
        assertThat(result.totalIncludingTax()).isEqualByComparingTo("0.00");
    }

    @Test void rejectsMissingInvoiceOrLineData() {
        assertThatThrownBy(() -> service.calculate(null)).isInstanceOf(InvoiceCalculationException.class);
        assertThatThrownBy(() -> service.calculate(List.of())).isInstanceOf(InvoiceCalculationException.class);
        assertThatThrownBy(() -> service.calculateLine(null)).isInstanceOf(InvoiceCalculationException.class);
        assertThatThrownBy(() -> service.calculateLine(new InvoiceLineInput(null, decimal("1"), decimal("20"), null)))
                .isInstanceOf(InvoiceCalculationException.class).hasMessageContaining("quantité");
        assertThatThrownBy(() -> service.calculateLine(new InvoiceLineInput(decimal("1"), null, decimal("20"), null)))
                .isInstanceOf(InvoiceCalculationException.class).hasMessageContaining("prix unitaire");
        assertThatThrownBy(() -> service.calculateLine(new InvoiceLineInput(decimal("1"), decimal("1"), null, null)))
                .isInstanceOf(InvoiceCalculationException.class).hasMessageContaining("taux de TVA");
    }

    @Test void rejectsInvalidQuantityPriceAndRateRangesAfterNormalization() {
        assertInvalid(line("0.0004", "1", "20", null));
        assertInvalid(line("-1", "1", "20", null));
        assertInvalid(line("10000000000000000", "1", "20", null));
        assertInvalid(line("1", "-0.01", "20", null));
        assertInvalid(line("1", "100000000000000000", "20", null));
        assertInvalid(line("1", "1", "-0.01", null));
        assertInvalid(line("1", "1", "100.005", null));
        assertInvalid(line("1", "1", "20", "-0.01"));
        assertInvalid(line("1", "1", "20", "100.005"));
    }

    @Test void rejectsLineAndInvoiceTotalPrecisionOverflow() {
        assertThatThrownBy(() -> service.calculateLine(line("2", "99999999999999999.99", "0", null)))
                .isInstanceOf(InvoiceCalculationException.class).hasMessageContaining("montant brut");

        InvoiceLineInput largeLine = line("1", "60000000000000000", "0", null);
        assertThatThrownBy(() -> service.calculate(List.of(largeLine, largeLine)))
                .isInstanceOf(InvoiceCalculationException.class).hasMessageContaining("sous-total de la facture");
    }

    @Test void calculationResultDefensivelyCopiesItsLines() {
        List<InvoiceLineCalculation> mutable = new ArrayList<>();
        mutable.add(service.calculateLine(line("1", "1", "0", null)));

        InvoiceCalculation calculation = new InvoiceCalculation(mutable, decimal("1.00"), decimal("0.00"),
                decimal("1.00"), decimal("0.00"), decimal("1.00"));
        mutable.clear();

        assertThat(calculation.lines()).hasSize(1);
        assertThatThrownBy(() -> calculation.lines().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    private void assertInvalid(InvoiceLineInput input) {
        assertThatThrownBy(() -> service.calculateLine(input)).isInstanceOf(InvoiceCalculationException.class);
    }

    private static InvoiceLineInput line(String quantity, String price, String vat, String discount) {
        return new InvoiceLineInput(decimal(quantity), decimal(price), decimal(vat), decimal(discount));
    }

    private static BigDecimal decimal(String value) {
        return value == null ? null : new BigDecimal(value);
    }
}
