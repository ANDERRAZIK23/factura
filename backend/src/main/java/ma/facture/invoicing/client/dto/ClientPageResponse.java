package ma.facture.invoicing.client.dto;

import org.springframework.data.domain.Page;
import java.util.List;

public record ClientPageResponse(List<ClientSummaryResponse> content, int page, int size,
                                 long totalElements, int totalPages, boolean first, boolean last) {
    public static ClientPageResponse from(Page<ClientSummaryResponse> result) {
        return new ClientPageResponse(result.getContent(), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages(), result.isFirst(), result.isLast());
    }
}
