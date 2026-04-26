package jymusic.jym_order_service.event.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import jymusic.jym_order_service.event.common.EventEnvelope;
import jymusic.jym_order_service.event.common.EventTypes;
import jymusic.jym_order_service.event.common.KafkaTopics;
import jymusic.jym_order_service.event.payload.OrderStatusChangedNotiPayload;
import jymusic.jym_order_service.notification.dto.NotificationMessage;
import jymusic.jym_order_service.notification.service.SseEmitterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.notification.broadcast-via-kafka", havingValue = "true", matchIfMissing = true)
public class NotificationEventConsumer {

    private final ObjectMapper objectMapper;
    private final SseEmitterRegistry registry;

    @KafkaListener(
            topics = KafkaTopics.NOTIFICATION_EVENTS,
            groupId = "jym-order-service-sse-${app.instance-id:${random.uuid}}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handle(ConsumerRecord<String, EventEnvelope<?>> record) {
        EventEnvelope<?> envelope = record.value();
        if (envelope == null) {
            return;
        }

        if (EventTypes.NOTI_ORDER_STATUS_CHANGED.equals(envelope.getEventType())) {
            OrderStatusChangedNotiPayload payload = objectMapper.convertValue(
                    envelope.getPayload(),
                    OrderStatusChangedNotiPayload.class
            );
            NotificationMessage message = NotificationMessage.from(payload);
            registry.sendToMember(payload.getMemberId(), envelope.getEventType(), message);
            return;
        }

        log.debug("무시되는 알림 이벤트 타입: {}", envelope.getEventType());
    }
}
