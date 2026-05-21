package jymusic.jym_order_service.notification.service;

import jymusic.jym_order_service.event.common.EventTypes;
import jymusic.jym_order_service.event.common.KafkaTopics;
import jymusic.jym_order_service.event.outbox.OutboxEventRecorder;
import jymusic.jym_order_service.event.payload.OrderStatusChangedNotiPayload;
import jymusic.jym_order_service.notification.dto.NotificationMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final OutboxEventRecorder outboxEventRecorder;
    private final SseEmitterRegistry registry;

    @Value("${app.notification.broadcast-via-kafka:true}")
    private boolean broadcastViaKafka;

    /**
     * 주문 상태 변경 알림.
     *
     * Kafka 브로드캐스트 모드: 같은 트랜잭션 안에서 Outbox 에 기록 → OutboxPublisher 가 발행 →
     *                       NotificationEventConsumer 가 수신하여 SSE 푸시.
     *                       (도메인 변경과 알림 이벤트가 같은 DB 트랜잭션에 묶여 dual-write 가 해결됨)
     *
     * 단일 인스턴스 모드(브로드캐스트 비활성): SSE 레지스트리에 직접 푸시.
     */
    public void publishOrderStatusChanged(OrderStatusChangedNotiPayload payload) {
        if (broadcastViaKafka) {
            outboxEventRecorder.record(
                    KafkaTopics.NOTIFICATION_EVENTS,
                    "ORDER",
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
