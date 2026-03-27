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
public class ProductSummaryResponse {

    private Long id;
    private String title;
    private String artist;
    private BigDecimal price;

    @JsonIgnore
    private String imageKey;

    private String thumbnailUrl;

    public static ProductSummaryResponse from(Product product, String s3BaseUrl) {
        return ProductSummaryResponse.builder()
                .id(product.getId())
                .title(product.getTitle())
                .artist(product.getArtist())
                .price(product.getPrice())
                .imageKey(product.getImageKey())
                .thumbnailUrl(product.getImageKey() != null
                        ? s3BaseUrl + "/" + product.getImageKey() : null)
                .build();
    }

    public void applyS3BaseUrl(String s3BaseUrl) {
        this.thumbnailUrl = this.imageKey != null
                ? s3BaseUrl + "/" + this.imageKey
                : null;
    }
}
