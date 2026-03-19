package jymusic.jym_order_service.service;

import jymusic.jym_order_service.client.CatalogClient;
import jymusic.jym_order_service.common.GlobalErrorHandler.GlobalException;
import jymusic.jym_order_service.domain.entity.Cart;
import jymusic.jym_order_service.domain.entity.CartItem;
import jymusic.jym_order_service.domain.repository.CartItemRepository;
import jymusic.jym_order_service.domain.repository.CartRepository;
import jymusic.jym_order_service.dto.request.AddToCartRequest;
import jymusic.jym_order_service.dto.request.UpdateCartItemRequest;
import jymusic.jym_order_service.dto.response.CartResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @Mock CartRepository cartRepository;
    @Mock CartItemRepository cartItemRepository;
    @Mock CatalogClient catalogClient;

    @InjectMocks CartService cartService;

    private static final Long MEMBER_ID = 1L;
    private static final Long PRODUCT_ID = 10L;

    private CatalogClient.ProductInfo sampleProductInfo() {
        return new CatalogClient.ProductInfo(PRODUCT_ID, "Abbey Road", "The Beatles",
                null, new BigDecimal("29000"), 10);
    }

    @BeforeEach
    void setUp() {
        given(catalogClient.getProductInfo(PRODUCT_ID)).willReturn(sampleProductInfo());
    }

    @Test
    @DisplayName("장바구니가 없으면 빈 장바구니를 반환한다")
    void getCart_returnsEmptyWhenNoCart() {
        given(cartRepository.findByMemberId(MEMBER_ID)).willReturn(Optional.empty());

        CartResponse result = cartService.getCart(MEMBER_ID);

        assertThat(result.getItems()).isEmpty();
    }

    @Test
    @DisplayName("신규 상품을 장바구니에 추가하면 장바구니가 생성된다")
    void addItem_createsNewCart() {
        given(cartRepository.findByMemberId(MEMBER_ID)).willReturn(Optional.empty());

        Cart newCart = Cart.builder().memberId(MEMBER_ID).build();
        given(cartRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(cartItemRepository.findByCartIdAndProductId(any(), any())).willReturn(Optional.empty());

        AddToCartRequest req = new AddToCartRequest();
        setField(req, "productId", PRODUCT_ID);
        setField(req, "quantity", 2);

        CartResponse response = cartService.addItem(MEMBER_ID, req);

        assertThat(response).isNotNull();
    }

    @Test
    @DisplayName("재고 초과 수량 담기 시 GlobalException이 발생한다")
    void addItem_throwsWhenStockInsufficient() {
        given(catalogClient.getProductInfo(PRODUCT_ID)).willReturn(
                new CatalogClient.ProductInfo(PRODUCT_ID, "Abbey Road", "The Beatles",
                        null, new BigDecimal("29000"), 1)
        );

        Cart cart = Cart.builder().memberId(MEMBER_ID).build();
        given(cartRepository.findByMemberId(MEMBER_ID)).willReturn(Optional.of(cart));
        given(cartRepository.save(any())).willReturn(cart);
        given(cartItemRepository.findByCartIdAndProductId(any(), any())).willReturn(Optional.empty());

        AddToCartRequest req = new AddToCartRequest();
        setField(req, "productId", PRODUCT_ID);
        setField(req, "quantity", 5);

        assertThatThrownBy(() -> cartService.addItem(MEMBER_ID, req))
                .isInstanceOf(GlobalException.class)
                .hasMessageContaining("재고가 부족합니다");
    }

    @Test
    @DisplayName("존재하지 않는 아이템 삭제 시 GlobalException이 발생한다")
    void removeItem_throwsWhenItemNotFound() {
        Cart cart = Cart.builder().memberId(MEMBER_ID).build();
        given(cartRepository.findByMemberId(MEMBER_ID)).willReturn(Optional.of(cart));
        given(cartItemRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> cartService.removeItem(MEMBER_ID, 99L))
                .isInstanceOf(GlobalException.class)
                .hasMessageContaining("장바구니 아이템을 찾을 수 없습니다");
    }

    @Test
    @DisplayName("수량 0으로 업데이트하면 아이템이 삭제된다")
    void updateItem_deletesWhenQuantityIsZero() {
        Cart cart = Cart.builder().memberId(MEMBER_ID).build();

        CartItem item = CartItem.builder()
                .cart(cart)
                .productId(PRODUCT_ID)
                .quantity(2)
                .build();
        cart.getItems().add(item);

        given(cartRepository.findByMemberId(MEMBER_ID)).willReturn(Optional.of(cart));
        given(cartItemRepository.findById(any())).willReturn(Optional.of(item));
        given(cartRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        UpdateCartItemRequest req = new UpdateCartItemRequest();
        setField(req, "quantity", 0);

        CartResponse response = cartService.updateItem(MEMBER_ID, 1L, req);
        assertThat(response.getItems()).isEmpty();
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
