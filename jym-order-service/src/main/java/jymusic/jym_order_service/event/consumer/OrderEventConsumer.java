package jymusic.jym_order_service.event.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import jymusic.jym_order_service.domain.entity.Order;
import jymusic.jym_order_service.domain.entity.OrderStatus;
import jymusic.jym_order_service.domain.event.OrderStatusChangedDomainEvent;
import jymusic.jym_order_service.domain.repository.OrderRepository;
import jymusic.jym_order_service.event.common.EventEnvelope;
import jymusic.jym_order_service.event.common.EventTypes;
import jymusic.jym_order_service.event.common.KafkaTopics;
import jymusic.jym_order_service.event.inbox.InboxIdempotencyGuard;
import jymusic.jym_order_service.event.payload.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 다른 서비스의 이벤트를 소비하여 주문 상태를 업데이트합니다.
 *
 * 소비 이벤트:
 *   - STOCK_RESERVED       (from catalog-service) → PENDING → STOCK_RESERVED
 *   - STOCK_RESERVATION_FAILED (from catalog-service) → PENDING → CANCELLED
 *   - PAYMENT_COMPLETED    (from payment-service) → STOCK_RESERVED → PAID
 *   - PAYMENT_FAILED       (from payment-service) → STOCK_RESERVED → CANCELLED
 *   - PAYMENT_CANCELLED    (from payment-service) → PAID → CANCELLED
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventConsumer {

    private static final String CONSUMER_GROUP = "jym-order-service-group";

    private final OrderRepository orderRepository;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final InboxIdempotencyGuard inboxIdempotencyGuard;
    private final ObjectMapper objectMapper;

    // ──────────────────────────────────────────
    // Stock Events 소비
    // ──────────────────────────────────────────

    /**
     * @Transactional 을 최상위 메서드에 배치하는 이유:
     * Spring AOP Proxy는 외부로부터의 호출만 인터셉트하므로,
     * 동일 클래스 내의 내부 호출(self-invocation)에서는 @Transactional 이 무시됩니다.
     * 따라서 Kafka로부터 호출되는 이 메서드에 트랜잭션을 설정합니다.
     */
    @KafkaListener(
        topics = KafkaTopics.STOCK_EVENTS,
        groupId = CONSUMER_GROUP,
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void handleStockEvent(ConsumerRecord<String, EventEnvelope<?>> record) {
        EventEnvelope<?> envelope = record.value();
        log.info("Stock 이벤트 수신: type={}, orderId={}",
                envelope.getEventType(), record.key());

        if (!inboxIdempotencyGuard.tryMarkProcessed(
                envelope.getEventId(), CONSUMER_GROUP, envelope.getEventType(), record)) {
            return;
        }

        switch (envelope.getEventType()) {
            case EventTypes.STOCK_RESERVED -> handleStockReserved(envelope);
            case EventTypes.STOCK_RESERVATION_FAILED -> handleStockReservationFailed(envelope);
            default -> log.warn("처리하지 않는 Stock 이벤트 타입: {}", envelope.getEventType());
        }
    }

    private void handleStockReserved(EventEnvelope<?> envelope) {
        StockReservedPayload payload = convertPayload(envelope, StockReservedPayload.class);

        orderRepository.findById(payload.getOrderId()).ifPresentOrElse(
            order -> {
                // 멱등성 체크 — 이미 STOCK_RESERVED 이상이면 무시
                if (order.getStatus() != OrderStatus.PENDING) {
                    log.info("이미 처리된 재고 예약 이벤트, skip: orderId={}, currentStatus={}",
                            payload.getOrderId(), order.getStatus());
                    return;
                }
                OrderStatus previous = order.getStatus();
                order.transitionTo(OrderStatus.STOCK_RESERVED);
                orderRepository.save(order);
                publishStatusChanged(order, previous, OrderStatus.STOCK_RESERVED);
                log.info("주문 상태 갱신: orderId={} → STOCK_RESERVED", payload.getOrderId());
            },
            () -> log.error("주문을 찾을 수 없음: orderId={}", payload.getOrderId())
        );
    }

    private void handleStockReservationFailed(EventEnvelope<?> envelope) {
        StockReservationFailedPayload payload =
                convertPayload(envelope, StockReservationFailedPayload.class);

        orderRepository.findById(payload.getOrderId()).ifPresentOrElse(
            order -> {
                if (order.getStatus() == OrderStatus.CANCELLED) {
                    log.info("이미 취소된 주문, skip: orderId={}", payload.getOrderId());
                    return;
                }
                OrderStatus previous = order.getStatus();
                order.transitionTo(OrderStatus.CANCELLED);
                orderRepository.save(order);
                publishStatusChanged(order, previous, OrderStatus.CANCELLED);
                log.info("재고 예약 실패로 주문 취소: orderId={}, 사유=상품 '{}' 재고 부족",
                        payload.getOrderId(), payload.getFailedProductTitle());
            },
            () -> log.error("주문을 찾을 수 없음: orderId={}", payload.getOrderId())
        );
    }

    // ──────────────────────────────────────────
    // Payment Events 소비
    // ──────────────────────────────────────────

    @KafkaListener(
        topics = KafkaTopics.PAYMENT_EVENTS,
        groupId = CONSUMER_GROUP,
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void handlePaymentEvent(ConsumerRecord<String, EventEnvelope<?>> record) {
        EventEnvelope<?> envelope = record.value();
        log.info("Payment 이벤트 수신: type={}, orderId={}",
                envelope.getEventType(), record.key());

        if (!inboxIdempotencyGuard.tryMarkProcessed(
                envelope.getEventId(), CONSUMER_GROUP, envelope.getEventType(), record)) {
            return;
        }

        switch (envelope.getEventType()) {
            case EventTypes.PAYMENT_COMPLETED -> handlePaymentCompleted(envelope);
            case EventTypes.PAYMENT_FAILED -> handlePaymentFailed(envelope);
            case EventTypes.PAYMENT_CANCELLED -> handlePaymentCancelled(envelope);
            default -> log.warn("처리하지 않는 Payment 이벤트 타입: {}", envelope.getEventType());
        }
    }

    private void handlePaymentCompleted(EventEnvelope<?> envelope) {
        PaymentCompletedPayload payload = convertPayload(envelope, PaymentCompletedPayload.class);

        orderRepository.findById(payload.getOrderId()).ifPresentOrElse(
            order -> {
                if (order.getStatus() == OrderStatus.PAID) {
                    log.info("이미 PAID 상태, skip: orderId={}", payload.getOrderId());
                    return;
                }
                OrderStatus previous = order.getStatus();
                order.transitionTo(OrderStatus.PAID);
                orderRepository.save(order);
                publishStatusChanged(order, previous, OrderStatus.PAID);
                log.info("결제 완료로 주문 상태 갱신: orderId={} → PAID", payload.getOrderId());
            },
            () -> log.error("주문을 찾을 수 없음: orderId={}", payload.getOrderId())
        );
    }

    private void handlePaymentFailed(EventEnvelope<?> envelope) {
        PaymentFailedPayload payload = convertPayload(envelope, PaymentFailedPayload.class);

        orderRepository.findById(payload.getOrderId()).ifPresentOrElse(
            order -> {
                if (order.getStatus() == OrderStatus.CANCELLED) {
                    return;
                }
                OrderStatus previous = order.getStatus();
                order.transitionTo(OrderStatus.CANCELLED);
                orderRepository.save(order);
                publishStatusChanged(order, previous, OrderStatus.CANCELLED);
                log.info("결제 실패로 주문 취소: orderId={}", payload.getOrderId());
            },
            () -> log.error("주문을 찾을 수 없음: orderId={}", payload.getOrderId())
        );
    }

    private void handlePaymentCancelled(EventEnvelope<?> envelope) {
        PaymentCancelledPayload payload = convertPayload(envelope, PaymentCancelledPayload.class);

        orderRepository.findById(payload.getOrderId()).ifPresentOrElse(
            order -> {
                if (order.getStatus() == OrderStatus.CANCELLED) {
                    return;
                }
                OrderStatus previous = order.getStatus();
                order.transitionTo(OrderStatus.CANCELLED);
                orderRepository.save(order);
                publishStatusChanged(order, previous, OrderStatus.CANCELLED);
                log.info("결제 취소로 주문 취소: orderId={}", payload.getOrderId());
            },
            () -> log.error("주문을 찾을 수 없음: orderId={}", payload.getOrderId())
        );
    }

    // ──────────────────────────────────────────
    // 헬퍼
    // ──────────────────────────────────────────

    private <T> T convertPayload(EventEnvelope<?> envelope, Class<T> type) {
        return objectMapper.convertValue(envelope.getPayload(), type);
    }

    private void publishStatusChanged(Order order, OrderStatus previous, OrderStatus current) {
        applicationEventPublisher.publishEvent(OrderStatusChangedDomainEvent.of(
                order.getId(),
                order.getMemberId(),
                previous,
                current,
                order.getTotalAmount(),
                order.getItems().isEmpty() ? "" : order.getItems().get(0).getProductTitle(),
                order.getItems().size()
        ));
    }
}
