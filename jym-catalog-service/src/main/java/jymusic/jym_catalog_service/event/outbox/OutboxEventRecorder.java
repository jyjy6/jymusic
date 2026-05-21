package jymusic.jym_catalog_service.event.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

/**
 * 비즈니스 트랜잭션 안에서 호출되어 outbox_event 테이블에 INSERT 만 수행합니다.
 * 실제 Kafka 발행은 {@link OutboxPublisher} 가 폴링하면서 비동기로 수행합니다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxEventRecorder {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher applicationEventPublisher;

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(String topic,
                       String aggregateType,
                       String aggregateId,
                       String eventType,
                       Object payload) {
        recordInternal(topic, aggregateType, aggregateId, eventType, payload);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordInNewTransaction(String topic,
                                        String aggregateType,
                                        String aggregateId,
                                        String eventType,
                                        Object payload) {
        recordInternal(topic, aggregateType, aggregateId, eventType, payload);
    }

    private void recordInternal(String topic,
                                 String aggregateType,
                                 String aggregateId,
                                 String eventType,
                                 Object payload) {
        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(payload);
        } catch (RuntimeException e) {
            throw new IllegalStateException(
                    "Outbox payload 직렬화 실패: topic=" + topic + ", eventType=" + eventType, e);
        }

        OutboxEvent event = OutboxEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .aggregateType(aggregateType)
                .aggregateId(aggregateId)
                .topic(topic)
                .eventType(eventType)
                .payload(payloadJson)
                .status(OutboxStatus.PENDING)
                .build();

        outboxEventRepository.save(event);

        applicationEventPublisher.publishEvent(new OutboxEventRecorded());
        log.debug("Outbox 이벤트 기록: eventId={}, topic={}, type={}",
                event.getEventId(), topic, eventType);
    }

    public record OutboxEventRecorded() {}
}
