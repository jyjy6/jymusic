package jymusic.jym_order_service.service;

import jymusic.jym_order_service.client.CatalogClient;
import jymusic.jym_order_service.common.GlobalErrorHandler.GlobalException;
import jymusic.jym_order_service.domain.entity.Order;
import jymusic.jym_order_service.domain.entity.OrderItem;
import jymusic.jym_order_service.domain.entity.OrderStatus;
import jymusic.jym_order_service.domain.repository.CartRepository;
import jymusic.jym_order_service.domain.repository.OrderRepository;
import jymusic.jym_order_service.dto.request.OrderCreateRequest;
import jymusic.jym_order_service.dto.request.OrderItemRequest;
import jymusic.jym_order_service.dto.response.OrderDetailResponse;
import jymusic.jym_order_service.dto.response.OrderResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock OrderRepository orderRepository;
    @Mock CartRepository cartRepository;
    @Mock CatalogClient catalogClient;

    @InjectMocks OrderService orderService;

    private static final Long MEMBER_ID = 1L;
    private static final Long PRODUCT_ID = 10L;

    @Test
    @DisplayName("정상 주문 생성 시 PENDING 상태의 주문이 반환된다")
    void createOrder_success() {
        given(catalogClient.getProductInfo(PRODUCT_ID)).willReturn(
                new CatalogClient.ProductInfo(PRODUCT_ID, "Abbey Road", "The Beatles",
                        null, new BigDecimal("29000"), 10)
        );

        Order savedOrder = Order.builder()
                .memberId(MEMBER_ID)
                .totalAmount(new BigDecimal("29000"))
                .status(OrderStatus.PENDING)
                .build();

        given(orderRepository.save(any())).willReturn(savedOrder);
        given(cartRepository.findByMemberId(MEMBER_ID)).willReturn(Optional.empty());

        OrderCreateRequest req = new OrderCreateRequest();
        OrderItemRequest itemReq = new OrderItemRequest();
        setField(itemReq, "productId", PRODUCT_ID);
        setField(itemReq, "quantity", 1);
        setField(req, "items", List.of(itemReq));

        OrderResponse response = orderService.createOrder(MEMBER_ID, req);

        assertThat(response.getStatus()).isEqualTo("PENDING");
        assertThat(response.getTotalAmount()).isEqualByComparingTo(new BigDecimal("29000"));
    }

    @Test
    @DisplayName("재고 부족 시 GlobalException이 발생한다")
    void createOrder_throwsWhenStockInsufficient() {
        given(catalogClient.getProductInfo(PRODUCT_ID)).willReturn(
                new CatalogClient.ProductInfo(PRODUCT_ID, "Abbey Road", "The Beatles",
                        null, new BigDecimal("29000"), 0)
        );

        OrderCreateRequest req = new OrderCreateRequest();
        OrderItemRequest itemReq = new OrderItemRequest();
        setField(itemReq, "productId", PRODUCT_ID);
        setField(itemReq, "quantity", 1);
        setField(req, "items", List.of(itemReq));

        assertThatThrownBy(() -> orderService.createOrder(MEMBER_ID, req))
                .isInstanceOf(GlobalException.class)
                .hasMessageContaining("재고가 부족합니다");
    }

    @Test
    @DisplayName("타인의 주문 조회 시 GlobalException이 발생한다")
    void getOrderDetail_throwsWhenNotOwner() {
        Order order = Order.builder()
                .memberId(999L)
                .totalAmount(BigDecimal.TEN)
                .status(OrderStatus.PENDING)
                .build();

        given(orderRepository.findById(1L)).willReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.getOrderDetail(MEMBER_ID, 1L))
                .isInstanceOf(GlobalException.class)
                .hasMessageContaining("접근 권한이 없습니다");
    }

    @Test
    @DisplayName("주문을 찾을 수 없으면 GlobalException이 발생한다")
    void getOrderDetail_throwsWhenNotFound() {
        given(orderRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getOrderDetail(MEMBER_ID, 999L))
                .isInstanceOf(GlobalException.class)
                .hasMessageContaining("주문을 찾을 수 없습니다");
    }

    @Test
    @DisplayName("주문 목록 조회 시 회원의 주문이 반환된다")
    void getMyOrders_returnsOrders() {
        Order order = Order.builder()
                .memberId(MEMBER_ID)
                .totalAmount(new BigDecimal("58000"))
                .status(OrderStatus.PAID)
                .build();

        given(orderRepository.findAllByMemberIdOrderByCreatedAtDesc(MEMBER_ID))
                .willReturn(List.of(order));

        List<OrderResponse> result = orderService.getMyOrders(MEMBER_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo("PAID");
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
