package ma.facture.invoicing.contract.mapper;

import ma.facture.invoicing.contract.dto.*;
import ma.facture.invoicing.contract.entity.*;
import ma.facture.invoicing.contract.service.*;
import org.springframework.stereotype.Component;
import java.time.*;

@Component
public class ContractMapper {
    private final ContractPricingService pricing;
    private final ContractValidationService validation;
    private final Clock clock;
    public ContractMapper(ContractPricingService pricing, ContractValidationService validation, Clock clock) {
        this.pricing = pricing; this.validation = validation; this.clock = clock;
    }
    public ContractResponse toResponse(Contract contract) {
        return new ContractResponse(contract.getId(), contract.getContractCode(), client(contract), contract.getStartDate(),
                contract.getEndDate(), contract.getPaymentMethod(), contract.getDefaultDiscountRate(), contract.getStatus(),
                validation.usable(contract, LocalDate.now(clock)), contract.getNotes(), contract.getLines().stream().map(this::line).toList(),
                contract.getCreatedAt(), contract.getUpdatedAt(), contract.getCreatedBy(), contract.getUpdatedBy(),
                contract.getActivatedAt(), contract.getActivatedBy(), contract.getCancelledAt(), contract.getCancelledBy(),
                contract.getCancellationReason(), contract.getVersion());
    }
    public ContractSummaryResponse toSummary(Contract contract) {
        return new ContractSummaryResponse(contract.getId(), contract.getContractCode(), client(contract), contract.getStartDate(),
                contract.getEndDate(), contract.getPaymentMethod(), contract.getStatus(),
                validation.usable(contract, LocalDate.now(clock)), contract.getLines().size(), contract.getCreatedAt(),
                contract.getUpdatedAt(), contract.getVersion());
    }
    public ContractPricingResponse toPricing(Contract contract) {
        var products = contract.getLines().stream().map(line -> new ContractPricingResponse.ProductPricing(
                line.getProduct().getId(), line.getProduct().getProductCode(), line.getProduct().getProductName(), line.getProduct().getUnit(),
                line.getNegotiatedUnitPriceExcludingTax(), line.getVatRate(), line.getDiscountRate(), line.getMinimumQuantity(),
                pricing.includingTax(line.getNegotiatedUnitPriceExcludingTax(), line.getVatRate()))).toList();
        return new ContractPricingResponse(contract.getId(), contract.getContractCode(), client(contract), contract.getPaymentMethod(),
                contract.getStartDate(), contract.getEndDate(), contract.getStatus(), true, products);
    }
    private ContractClientResponse client(Contract contract) {
        var client = contract.getClient();
        return new ContractClientResponse(client.getId(), client.getClientCode(), client.getClientName(), client.isActive());
    }
    private ContractLineResponse line(ContractLine line) {
        var product = line.getProduct();
        return new ContractLineResponse(line.getId(), product.getId(), product.getProductCode(), product.getProductName(), product.getUnit(),
                product.isActive(), line.getNegotiatedUnitPriceExcludingTax(), line.getVatRate(), line.getDiscountRate(), line.getMinimumQuantity(),
                pricing.includingTax(line.getNegotiatedUnitPriceExcludingTax(), line.getVatRate()), line.getNotes(), line.getCreatedAt(),
                line.getUpdatedAt(), line.getVersion());
    }
}
