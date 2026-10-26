package ma.facture.invoicing.invoice.calculation;

import java.math.BigDecimal;
import java.util.List;

public record InvoiceCalculation(
        List<InvoiceLineCalculation> lines,
        BigDecimal subtotalExcludingTax,
        BigDecimal totalDiscount,
        BigDecimal taxableAmount,
        BigDecimal totalVat,
        BigDecimal totalIncludingTax) {
    public InvoiceCalculation {
        lines = List.copyOf(lines);
    }
}
