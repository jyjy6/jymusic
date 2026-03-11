package jymusic.jym_catalog_service.domain.entity;

import jakarta.persistence.*;
import jymusic.jym_catalog_service.domain.common.BaseTimeEntity;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "products")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class Product extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, length = 100)
    private String artist;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    private Integer stockQuantity;

    // S3 objectKey (예: "products/uuid-abbey-road.jpg")
    // imageUrl / thumbnailUrl 은 서비스 레이어에서 s3BaseUrl + "/" + imageKey 로 조합
    @Column(length = 500)
    private String imageKey;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isAvailable = true;

    public void update(String title, String artist, String description,
                       BigDecimal price, Integer stockQuantity,
                       Category category, String imageKey) {
        this.title = title;
        this.artist = artist;
        this.description = description;
        this.price = price;
        this.stockQuantity = stockQuantity;
        this.category = category;
        this.imageKey = imageKey;
    }

    public void softDelete() {
        this.isAvailable = false;
    }
}
