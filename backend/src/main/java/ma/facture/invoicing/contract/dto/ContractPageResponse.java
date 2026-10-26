package ma.facture.invoicing.contract.dto;

import org.springframework.data.domain.Page;
import java.util.List;

public record ContractPageResponse(List<ContractSummaryResponse> content, int page, int size, long totalElements,
                                   int totalPages, boolean first, boolean last) {
    public static ContractPageResponse from(Page<ContractSummaryResponse> page) {
        return new ContractPageResponse(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(),
                page.getTotalPages(), page.isFirst(), page.isLast());
    }
}
