package jymusic.jym_catalog_service.dto.response;

import jymusic.jym_catalog_service.domain.entity.Product;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class ProductDetailResponse {

    private Long id;
    private String title;
    private String artist;
    private String description;
    private BigDecimal price;
    private Integer stockQuantity;
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
                .imageUrl(product.getImageKey() != null
                        ? s3BaseUrl + "/" + product.getImageKey() : null)
                .categoryId(product.getCategory() != null ? product.getCategory().getId() : null)
                .categoryName(product.getCategory() != null ? product.getCategory().getName() : null)
                .build();
    }
}
