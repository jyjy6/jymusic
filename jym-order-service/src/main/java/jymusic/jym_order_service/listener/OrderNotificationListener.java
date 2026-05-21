package jymusic.jym_order_service.listener;

import jymusic.jym_order_service.domain.event.OrderStatusChangedDomainEvent;
import jymusic.jym_order_service.event.payload.OrderStatusChangedNotiPayload;
import jymusic.jym_order_service.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 주문 상태 변경 시 알림 발행을 처리하는 리스너.
 *
 * 이전 구현은 {@code @TransactionalEventListener(AFTER_COMMIT)} 으로 트랜잭션 commit 이후
 * Kafka 발행을 직접 수행했으나, 이는 "DB commit 성공 → Kafka 발행 실패" 케이스에서
 * 알림 이벤트가 유실되는 dual-write 문제가 있었습니다.
 *
 * 현재 구현은 동기 {@link EventListener} 로 호출되어 호출자(예: OrderEventConsumer)의
 * 트랜잭션 안에서 outbox 에 INSERT 됩니다. 호출자 트랜잭션이 rollback 되면 outbox INSERT 도
 * 함께 rollback 되어 정합성이 보장됩니다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderNotificationListener {

    private final NotificationService notificationService;

    @EventListener
    public void onStatusChanged(OrderStatusChangedDomainEvent event) {
        notificationService.publishOrderStatusChanged(OrderStatusChangedNotiPayload.builder()
                .orderId(event.getOrderId())
                .memberId(event.getMemberId())
                .previousStatus(event.getPreviousStatus())
                .currentStatus(event.getCurrentStatus())
                .totalAmount(event.getTotalAmount())
                .firstItemTitle(event.getFirstItemTitle())
                .itemCount(event.getItemCount())
                .changedAt(LocalDateTime.now())
                .build());
    }
}
