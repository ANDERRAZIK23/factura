package ma.facture.invoicing.invoice.calculation;

import java.math.BigDecimal;

public record InvoiceLineInput(BigDecimal quantity, BigDecimal unitPriceExcludingTax,
                               BigDecimal vatRate, BigDecimal discountRate) {}
