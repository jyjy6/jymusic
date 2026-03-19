package jymusic.jym_order_service.dto.response;

import jymusic.jym_order_service.domain.entity.OrderItem;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class OrderItemDetailResponse {
    private Long productId;
    private String productTitle;
    private BigDecimal price;
    private int quantity;

    public static OrderItemDetailResponse from(OrderItem item) {
        return OrderItemDetailResponse.builder()
                .productId(item.getProductId())
                .productTitle(item.getProductTitle())
                .price(item.getUnitPrice())
                .quantity(item.getQuantity())
                .build();
    }
}
