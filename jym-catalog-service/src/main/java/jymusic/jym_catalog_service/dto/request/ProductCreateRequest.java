package jymusic.jym_catalog_service.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@NoArgsConstructor
public class ProductCreateRequest {

    @NotBlank(message = "앨범명은 필수입니다.")
    @Size(max = 100, message = "앨범명은 100자 이하이어야 합니다.")
    private String title;

    @NotBlank(message = "아티스트명은 필수입니다.")
    @Size(max = 100, message = "아티스트명은 100자 이하이어야 합니다.")
    private String artist;

    @Size(max = 2000, message = "상세 설명은 2000자 이하이어야 합니다.")
    private String description;

    @NotNull(message = "가격은 필수입니다.")
    private BigDecimal price;

    @NotNull(message = "재고 수량은 필수입니다.")
    private Integer stockQuantity;

    @NotNull(message = "카테고리는 필수입니다.")
    private Long categoryId;

    private String imageKey;
}
