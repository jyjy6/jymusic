package jymusic.jym_order_service.scheduler;

import jymusic.jym_order_service.domain.entity.Order;
import jymusic.jym_order_service.domain.entity.OrderStatus;
import jymusic.jym_order_service.domain.repository.OrderRepository;
import jymusic.jym_order_service.event.common.EventTypes;
import jymusic.jym_order_service.event.common.KafkaTopics;
import jymusic.jym_order_service.event.outbox.OutboxEventRecorder;
import jymusic.jym_order_service.event.payload.OrderCancelledPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 결제 타임아웃 스케줄러.
 * PENDING / STOCK_RESERVED 상태에서 30분이 경과한 주문을 자동 취소합니다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentTimeoutScheduler {

    private final OrderRepository orderRepository;
    private final OutboxEventRecorder outboxEventRecorder;

    @Scheduled(fixedRate = 60000)  // 1분마다 실행
    @Transactional
    public void cancelTimedOutOrders() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(30);

        List<Order> timedOutOrders = orderRepository
                .findByStatusInAndCreatedAtBefore(
                        List.of(OrderStatus.PENDING, OrderStatus.STOCK_RESERVED),
                        threshold
                );

        for (Order order : timedOutOrders) {
            order.transitionTo(OrderStatus.CANCELLED);
            orderRepository.save(order);

            // 재고 복원용 ORDER_CANCELLED 이벤트를 Outbox 에 기록 (같은 트랜잭션)
            outboxEventRecorder.record(
                    KafkaTopics.ORDER_EVENTS,
                    "ORDER",
                    order.getId().toString(),
                    EventTypes.ORDER_CANCELLED,
                    OrderCancelledPayload.builder()
                            .orderId(order.getId())
                            .memberId(order.getMemberId())
                            .reason("PAYMENT_TIMEOUT")
                            .build()
            );

            log.info("결제 타임아웃으로 주문 취소: orderId={}, createdAt={}",
                    order.getId(), order.getCreatedAt());
        }

        if (!timedOutOrders.isEmpty()) {
            log.info("타임아웃 주문 {} 건 취소 완료", timedOutOrders.size());
        }
    }
}
