package ma.facture.invoicing.invoice.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record InvoiceLineResponse(
        UUID id, UUID productId, String productCode, String productName, String productDescription, String unit,
        BigDecimal quantity, BigDecimal unitPriceExcludingTax, BigDecimal vatRate, BigDecimal discountRate,
        BigDecimal grossAmountExcludingTax, BigDecimal discountAmount, BigDecimal netAmountExcludingTax,
        BigDecimal vatAmount, BigDecimal totalIncludingTax) {}
