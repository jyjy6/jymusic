package jymusic.jym_order_service.dto.response;

import jymusic.jym_order_service.domain.entity.Cart;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class CartResponse {
    private Long cartId;
    private List<CartItemResponse> items;

    public static CartResponse of(Cart cart, List<CartItemResponse> items) {
        return CartResponse.builder()
                .cartId(cart.getId())
                .items(items)
                .build();
    }

    public static CartResponse empty(Long cartId) {
        return CartResponse.builder()
                .cartId(cartId)
                .items(List.of())
                .build();
    }
}
