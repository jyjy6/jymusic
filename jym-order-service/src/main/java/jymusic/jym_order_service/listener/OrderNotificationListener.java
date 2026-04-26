package jymusic.jym_order_service.listener;

import jymusic.jym_order_service.domain.event.OrderStatusChangedDomainEvent;
import jymusic.jym_order_service.event.payload.OrderStatusChangedNotiPayload;
import jymusic.jym_order_service.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderNotificationListener {

    private final NotificationService notificationService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
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
