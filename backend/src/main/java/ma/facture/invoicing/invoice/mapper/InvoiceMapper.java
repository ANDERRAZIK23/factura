package ma.facture.invoicing.invoice.mapper;

import ma.facture.invoicing.invoice.dto.*;
import ma.facture.invoicing.invoice.entity.*;
import ma.facture.invoicing.user.UserEntity;
import ma.facture.invoicing.user.UserRepository;
import org.springframework.stereotype.Component;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class InvoiceMapper {
    private final UserRepository users;
    public InvoiceMapper(UserRepository users) { this.users = users; }

    public InvoiceResponse toResponse(Invoice invoice) {
        var actorIds = Stream.of(invoice.getCreatedBy(), invoice.getUpdatedBy(), invoice.getValidatorUserId())
                .filter(Objects::nonNull).collect(Collectors.toSet());
        var names = users.findAllById(actorIds).stream()
                .collect(Collectors.toMap(UserEntity::getId, UserEntity::getFullName));
        return new InvoiceResponse(invoice.getId(), invoice.getInvoiceNumber(), invoice.getInvoiceDate(), client(invoice),
                contract(invoice), invoice.getPaymentMethod(), invoice.getCustomerOrderReference(),
                invoice.getSubtotalExcludingTax(), invoice.getTotalDiscount(), invoice.getTaxableAmount(),
                invoice.getTotalVat(), invoice.getTotalIncludingTax(), invoice.getAmountInWords(), invoice.getStatus(),
                invoice.getLines().stream().map(this::line).toList(), invoice.getCreatedBy(), invoice.getUpdatedBy(),
                invoice.getValidatorUserId(), invoice.getCreatedAt(), invoice.getUpdatedAt(), invoice.getValidatedAt(),
                invoice.getCancelledAt(), invoice.getPdfFilePath() != null,
                invoice.getPdfFilePath() == null ? null : "/api/v1/invoices/" + invoice.getId() + "/pdf", invoice.getVersion(),
                names.get(invoice.getCreatedBy()), names.get(invoice.getUpdatedBy()), names.get(invoice.getValidatorUserId()),
                invoice.getPaidAmount(), invoice.getOutstandingAmount(), invoice.getDueDate());
    }

    public InvoiceSummaryResponse toSummary(Invoice invoice) {
        return new InvoiceSummaryResponse(invoice.getId(), invoice.getInvoiceNumber(), invoice.getInvoiceDate(), client(invoice),
                invoice.getStatus(), invoice.getSubtotalExcludingTax(), invoice.getTotalDiscount(),
                invoice.getTaxableAmount(), invoice.getTotalVat(), invoice.getTotalIncludingTax(),
                invoice.getLines().size(), invoice.getCreatedAt(), invoice.getUpdatedAt(), invoice.getVersion(),
                invoice.getPaidAmount(), invoice.getOutstandingAmount(), invoice.getDueDate());
    }

    private InvoiceClientResponse client(Invoice invoice) {
        var client = invoice.getClient();
        boolean snapshot = invoice.getInvoiceNumber() != null;
        return new InvoiceClientResponse(client.getId(),
                snapshot ? invoice.getClientCodeSnapshot() : client.getClientCode(),
                snapshot ? invoice.getClientNameSnapshot() : client.getClientName(),
                snapshot ? invoice.getClientAddressSnapshot() : client.getAddress(),
                snapshot ? invoice.getClientPhoneSnapshot() : client.getPhoneNumber(),
                snapshot ? invoice.getClientIceSnapshot() : client.getIce(), client.isActive());
    }

    private static InvoiceContractResponse contract(Invoice invoice) {
        return invoice.getContract() == null ? null
                : new InvoiceContractResponse(invoice.getContract().getId(), invoice.getContract().getContractCode());
    }

    private InvoiceLineResponse line(InvoiceLine line) {
        boolean snapshot = line.getInvoice().getInvoiceNumber() != null;
        var product = line.getProduct();
        return new InvoiceLineResponse(line.getId(), product.getId(),
                snapshot ? line.getProductCodeSnapshot() : product.getProductCode(),
                snapshot ? line.getProductNameSnapshot() : product.getProductName(),
                snapshot ? line.getProductDescriptionSnapshot() : product.getDescription(),
                snapshot ? line.getUnitSnapshot() : product.getUnit().name(), line.getQuantity(),
                line.getUnitPriceExcludingTax(), line.getVatRate(), line.getDiscountRate(),
                line.getGrossAmountExcludingTax(), line.getDiscountAmount(), line.getNetAmountExcludingTax(),
                line.getVatAmount(), line.getTotalIncludingTax());
    }
}
