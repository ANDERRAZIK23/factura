package ma.facture.invoicing.invoice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import ma.facture.invoicing.contract.entity.PaymentMethod;

import java.time.LocalDate;
import java.util.*;

public record InvoiceCreateRequest(
        @NotNull(message = "La date de facture est requise") LocalDate invoiceDate,
        @NotNull(message = "Le client est requis") UUID clientId,
        UUID contractId,
        PaymentMethod paymentMethod,
        @Size(max = 100, message = "La référence de commande client ne doit pas dépasser 100 caractères") String customerOrderReference,
        List<@NotNull(message = "La ligne de facture est requise") @Valid InvoiceLineRequest> lines) {}
