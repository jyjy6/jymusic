package jymusic.jym_catalog_service.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jymusic.jym_catalog_service.domain.entity.Product;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductDetailResponse {

    private Long id;
    private String title;
    private String artist;
    private String description;
    private BigDecimal price;
    private Integer stockQuantity;

    @JsonIgnore
    private String imageKey;

    private String imageUrl;
    private Long categoryId;
    private String categoryName;

    public static ProductDetailResponse from(Product product, String s3BaseUrl) {
        return ProductDetailResponse.builder()
                .id(product.getId())
                .title(product.getTitle())
                .artist(product.getArtist())
                .description(product.getDescription())
                .price(product.getPrice())
                .stockQuantity(product.getStockQuantity())
                .imageKey(product.getImageKey())
                .imageUrl(product.getImageKey() != null
                        ? s3BaseUrl + "/" + product.getImageKey() : null)
                .categoryId(product.getCategory() != null ? product.getCategory().getId() : null)
                .categoryName(product.getCategory() != null ? product.getCategory().getName() : null)
                .build();
    }

    public void applyS3BaseUrl(String s3BaseUrl) {
        this.imageUrl = this.imageKey != null
                ? s3BaseUrl + "/" + this.imageKey
                : null;
    }
}
