package jymusic.jym_catalog_service.dto.response;

import lombok.Builder;
import lombok.Getter;
import org.springframework.data.domain.Page;

import java.util.List;

@Getter
@Builder
public class ProductListResponse {

    private List<ProductSummaryResponse> content;
    private long totalElements;
    private int totalPages;

    public static ProductListResponse from(Page<ProductSummaryResponse> page) {
        return ProductListResponse.builder()
                .content(page.getContent())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .build();
    }
}
