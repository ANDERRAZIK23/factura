package ma.facture.invoicing.contract.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import ma.facture.invoicing.contract.entity.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Schema(description = "Complete contract aggregate including lifecycle audit; ACTIVE commercial terms are immutable")
public record ContractResponse(UUID id, @Schema(example = "CTR-2026-000001", pattern = "^CTR-[0-9]{4}-[0-9]{6,}$") String contractCode, ContractClientResponse client, LocalDate startDate,
                               LocalDate endDate, PaymentMethod paymentMethod, BigDecimal defaultDiscountRate,
                               ContractStatus status, boolean usable, String notes, List<ContractLineResponse> lines,
                               Instant createdAt, Instant updatedAt, UUID createdBy, UUID updatedBy,
                               Instant activatedAt, UUID activatedBy, Instant cancelledAt, UUID cancelledBy,
                               String cancellationReason, long version) {}
