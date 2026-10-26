package ma.facture.invoicing.invoice.dto;

import org.springframework.data.domain.Page;

import java.util.List;

public record InvoicePageResponse(List<InvoiceSummaryResponse> content, int page, int size,
                                  long totalElements, int totalPages, boolean first, boolean last) {
    public static InvoicePageResponse from(Page<InvoiceSummaryResponse> result) {
        return new InvoicePageResponse(result.getContent(), result.getNumber(), result.getSize(), result.getTotalElements(),
                result.getTotalPages(), result.isFirst(), result.isLast());
    }
}
