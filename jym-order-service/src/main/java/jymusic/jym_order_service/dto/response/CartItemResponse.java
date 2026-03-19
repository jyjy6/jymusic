package jymusic.jym_order_service.dto.response;

import jymusic.jym_order_service.domain.entity.CartItem;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class CartItemResponse {
    private Long cartItemId;
    private Long productId;
    private String title;
    private String artist;
    private String thumbnailUrl;
    private BigDecimal price;
    private int quantity;
    private int stockQuantity;

    public static CartItemResponse of(CartItem item, String title, String artist,
                                       String thumbnailUrl, BigDecimal price, int stockQuantity) {
        return CartItemResponse.builder()
                .cartItemId(item.getId())
                .productId(item.getProductId())
                .title(title)
                .artist(artist)
                .thumbnailUrl(thumbnailUrl)
                .price(price)
                .quantity(item.getQuantity())
                .stockQuantity(stockQuantity)
                .build();
    }
}
