package jymusic.jym_order_service.notification.service;

import jymusic.jym_order_service.event.common.EventTypes;
import jymusic.jym_order_service.event.common.KafkaTopics;
import jymusic.jym_order_service.event.payload.OrderStatusChangedNotiPayload;
import jymusic.jym_order_service.event.publisher.EventPublisher;
import jymusic.jym_order_service.notification.dto.NotificationMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final EventPublisher eventPublisher;
    private final SseEmitterRegistry registry;

    @Value("${app.notification.broadcast-via-kafka:true}")
    private boolean broadcastViaKafka;

    public void publishOrderStatusChanged(OrderStatusChangedNotiPayload payload) {
        if (broadcastViaKafka) {
            eventPublisher.publish(
                    KafkaTopics.NOTIFICATION_EVENTS,
                    String.valueOf(payload.getOrderId()),
                    EventTypes.NOTI_ORDER_STATUS_CHANGED,
                    payload
            );
            return;
        }

        registry.sendToMember(
                payload.getMemberId(),
                EventTypes.NOTI_ORDER_STATUS_CHANGED,
                NotificationMessage.from(payload)
        );
    }
}
