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
import jymusic.jym_order_service.event.common.EventTypes;
import jymusic.jym_order_service.event.common.KafkaTopics;
import jymusic.jym_order_service.event.outbox.OutboxEventRecorder;
import jymusic.jym_order_service.event.payload.OrderCreatedPayload;
import jymusic.jym_order_service.event.payload.OrderItemPayload;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderService {

    private final OrderRepository orderRepository;
    private final CartRepository cartRepository;
    private final CatalogClient catalogClient;
    private final OutboxEventRecorder outboxEventRecorder;

    @Transactional
    public OrderResponse createOrder(Long memberId, OrderCreateRequest request) {
        record ItemInfo(CatalogClient.ProductInfo info, int quantity) {}

        // 1. 상품 정보 조회 (REST → catalog-service)
        //    ※ 재고 "검증"만 수행, 차감은 하지 않음 (Kafka 이벤트로 처리)
        List<ItemInfo> infos = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;

        for (OrderItemRequest itemReq : request.getItems()) {
            CatalogClient.ProductInfo info = catalogClient.getProductInfo(itemReq.getProductId());
            // 기본 유효성만 체크 (상품 존재 여부, 판매 가능 여부)
            // ※ 재고 수량 최종 검증은 catalog-service가 이벤트 소비 시 수행
            totalAmount = totalAmount.add(info.price().multiply(BigDecimal.valueOf(itemReq.getQuantity())));
            infos.add(new ItemInfo(info, itemReq.getQuantity()));
        }

        // 2. 주문 생성 (PENDING 상태)
        Order order = Order.builder()
                .memberId(memberId)
                .totalAmount(totalAmount)
                .status(OrderStatus.PENDING)
                .build();

        for (ItemInfo i : infos) {
            OrderItem orderItem = OrderItem.builder()
                    .order(order)
                    .productId(i.info().productId())
                    .productTitle(i.info().title())
                    .unitPrice(i.info().price())
                    .quantity(i.quantity())
                    .build();
            order.getItems().add(orderItem);
        }

        Order savedOrder = orderRepository.save(order);

        // 3. 장바구니 비우기
        cartRepository.findByMemberId(memberId).ifPresent(cart -> {
            cart.getItems().clear();
            cartRepository.save(cart);
        });

        // 4. ORDER_CREATED 이벤트를 Outbox 에 기록 (같은 트랜잭션 — dual write 방지)
        recordOrderCreatedToOutbox(savedOrder);

        return OrderResponse.from(savedOrder);
    }

    /**
     * 주문 생성 이벤트를 Outbox 테이블에 기록합니다.
     * 같은 DB 트랜잭션 안에서 INSERT 되므로 정합성이 보장되며,
     * 실제 Kafka 발행은 OutboxPublisher 가 비동기로 수행합니다.
     */
    private void recordOrderCreatedToOutbox(Order order) {
        OrderCreatedPayload payload = OrderCreatedPayload.builder()
                .orderId(order.getId())
                .memberId(order.getMemberId())
                .totalAmount(order.getTotalAmount())
                .items(order.getItems().stream()
                        .map(item -> OrderItemPayload.builder()
                                .productId(item.getProductId())
                                .productTitle(item.getProductTitle())
                                .unitPrice(item.getUnitPrice())
                                .quantity(item.getQuantity())
                                .build())
                        .toList())
                .build();

        outboxEventRecorder.record(
                KafkaTopics.ORDER_EVENTS,
                "ORDER",
                order.getId().toString(),
                EventTypes.ORDER_CREATED,
                payload
        );
    }

    public List<OrderResponse> getMyOrders(Long memberId) {
        return orderRepository.findAllByMemberIdOrderByCreatedAtDesc(memberId)
                .stream()
                .map(OrderResponse::from)
                .toList();
    }

    public OrderDetailResponse getOrderDetail(Long memberId, Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new GlobalException("주문을 찾을 수 없습니다.", "ERR_ORDER_NOT_FOUND", HttpStatus.NOT_FOUND));

        if (!order.getMemberId().equals(memberId)) {
            throw new GlobalException("접근 권한이 없습니다.", "ERR_FORBIDDEN", HttpStatus.FORBIDDEN);
        }

        return OrderDetailResponse.from(order);
    }

    @Transactional
    public void updateOrderStatus(Long orderId, OrderStatus newStatus) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new GlobalException("주문을 찾을 수 없습니다.", "ERR_ORDER_NOT_FOUND", HttpStatus.NOT_FOUND));
        order.transitionTo(newStatus);
    }

}
