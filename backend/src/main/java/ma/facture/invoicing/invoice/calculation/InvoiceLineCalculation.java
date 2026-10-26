package ma.facture.invoicing.invoice.calculation;

import java.math.BigDecimal;

public record InvoiceLineCalculation(
        BigDecimal quantity,
        BigDecimal unitPriceExcludingTax,
        BigDecimal vatRate,
        BigDecimal discountRate,
        BigDecimal grossAmountExcludingTax,
        BigDecimal discountAmount,
        BigDecimal netAmountExcludingTax,
        BigDecimal vatAmount,
        BigDecimal totalIncludingTax) {}
