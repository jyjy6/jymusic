package jymusic.jym_catalog_service.dto.response;

import jymusic.jym_catalog_service.domain.entity.Product;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class ProductSummaryResponse {

    private Long id;
    private String title;
    private String artist;
    private BigDecimal price;
    private String thumbnailUrl;

    public static ProductSummaryResponse from(Product product, String s3BaseUrl) {
        return ProductSummaryResponse.builder()
                .id(product.getId())
                .title(product.getTitle())
                .artist(product.getArtist())
                .price(product.getPrice())
                .thumbnailUrl(product.getImageKey() != null
                        ? s3BaseUrl + "/" + product.getImageKey() : null)
                .build();
    }
}
