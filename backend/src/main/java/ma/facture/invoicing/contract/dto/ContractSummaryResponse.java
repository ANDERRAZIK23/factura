package ma.facture.invoicing.contract.dto;

import ma.facture.invoicing.contract.entity.*;
import java.time.*;
import java.util.UUID;

public record ContractSummaryResponse(UUID id, String contractCode, ContractClientResponse client,
                                      LocalDate startDate, LocalDate endDate, PaymentMethod paymentMethod,
                                      ContractStatus status, boolean usable, int productCount,
                                      Instant createdAt, Instant updatedAt, long version) {}
