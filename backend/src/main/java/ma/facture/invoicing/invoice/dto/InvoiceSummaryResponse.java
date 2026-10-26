package ma.facture.invoicing.invoice.dto;

import ma.facture.invoicing.invoice.entity.InvoiceStatus;

import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;

public record InvoiceSummaryResponse(
        UUID id, String invoiceNumber, LocalDate invoiceDate, InvoiceClientResponse client,
        InvoiceStatus status, BigDecimal subtotalExcludingTax, BigDecimal totalDiscount,
        BigDecimal taxableAmount, BigDecimal totalVat, BigDecimal totalIncludingTax, int lineCount,
        Instant createdAt, Instant updatedAt, long version,
        BigDecimal paidAmount, BigDecimal outstandingAmount, LocalDate dueDate) {}
