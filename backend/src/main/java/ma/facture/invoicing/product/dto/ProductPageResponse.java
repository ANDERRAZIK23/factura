package ma.facture.invoicing.product.dto;

import org.springframework.data.domain.Page;
import java.util.List;

public record ProductPageResponse(List<ProductSummaryResponse> content, int page, int size,
                                  long totalElements, int totalPages, boolean first, boolean last) {
    public static ProductPageResponse from(Page<ProductSummaryResponse> result) {
        return new ProductPageResponse(result.getContent(), result.getNumber(), result.getSize(), result.getTotalElements(),
                result.getTotalPages(), result.isFirst(), result.isLast());
    }
}
