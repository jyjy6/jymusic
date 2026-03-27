package jymusic.jym_catalog_service.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductSearchRequest {

    private String keyword;
    private Long categoryId;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;

    @Builder.Default
    private int page = 0;

    @Builder.Default
    private int size = 12;

    @Builder.Default
    private String sort = "createdAt,desc";

    /** MyBatis XML에서 OFFSET 계산 시 {@code #{req.offset}} 로 사용 */
    public int getOffset() {
        return page * size;
    }
}

