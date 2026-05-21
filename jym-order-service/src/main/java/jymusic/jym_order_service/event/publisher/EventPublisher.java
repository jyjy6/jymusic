package jymusic.jym_order_service.event.publisher;

import jymusic.jym_order_service.event.common.EventEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Kafka 이벤트 발행 유틸리티.
 * EventEnvelope로 감싸서 일관된 포맷으로 발행합니다.
 *
 * <p><b>중요</b>: 비즈니스 코드에서 직접 호출하지 마세요.
 * 도메인 데이터와 발행 이벤트의 정합성(dual-write 방지)을 위해
 * 반드시 {@code OutboxEventRecorder.record(...)} 를 사용하세요.
 * 이 클래스는 {@code OutboxPublisher} 가 outbox row 를 발행할 때만 사용합니다.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${spring.application.name}")
    private String serviceName;

    /**
     * 이벤트를 Kafka 토픽에 발행합니다.
     *
     * @param topic     대상 토픽명
     * @param key       파티션 분배용 키 (orderId 등)
     * @param eventType 이벤트 타입 (EventTypes 상수)
     * @param payload   이벤트 데이터
     */
    public <T> void publish(String topic, String key, String eventType, T payload) {
        EventEnvelope<T> envelope = EventEnvelope.<T>builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(eventType)
                .version(1)
                .timestamp(LocalDateTime.now())
                .source(serviceName)
                .payload(payload)
                .build();

        kafkaTemplate.send(topic, key, envelope)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("이벤트 발행 실패: topic={}, key={}, type={}",
                                topic, key, eventType, ex);
                    } else {
                        log.info("이벤트 발행 성공: topic={}, key={}, type={}, offset={}",
                                topic, key, eventType,
                                result.getRecordMetadata().offset());
                    }
                });
    }
}
