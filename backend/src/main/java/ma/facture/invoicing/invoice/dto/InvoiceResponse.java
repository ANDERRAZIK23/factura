package ma.facture.invoicing.invoice.dto;

import ma.facture.invoicing.contract.entity.PaymentMethod;
import ma.facture.invoicing.invoice.entity.InvoiceStatus;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public record InvoiceResponse(
        UUID id, String invoiceNumber, LocalDate invoiceDate, InvoiceClientResponse client,
        InvoiceContractResponse contract, PaymentMethod paymentMethod, String customerOrderReference,
        BigDecimal subtotalExcludingTax, BigDecimal totalDiscount, BigDecimal taxableAmount,
        BigDecimal totalVat, BigDecimal totalIncludingTax, String amountInWords, InvoiceStatus status,
        List<InvoiceLineResponse> lines, UUID createdBy, UUID updatedBy, UUID validatorUserId,
        Instant createdAt, Instant updatedAt, Instant validatedAt, Instant cancelledAt,
        boolean pdfAvailable, String pdfDownloadUrl, long version,
        String createdByName, String updatedByName, String validatorName,
        BigDecimal paidAmount, BigDecimal outstandingAmount, LocalDate dueDate) {}
