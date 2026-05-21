package jymusic.jym_payment_service.event.outbox;

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

    /**
     * 호출자의 트랜잭션이 롤백되어야 하는 경로(예: 외부 API 호출은 성공했지만
     * 로컬 DB 처리에서 예외 발생)에서, outbox 만 별도 트랜잭션으로 기록할 때 사용합니다.
     * PaymentService.confirm 의 Toss 승인 후 실패 처리 같은 경로에서 사용됩니다.
     */
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

    /**
     * 트랜잭션 commit 후 즉시 발행 트리거를 위한 Spring ApplicationEvent.
     */
    public record OutboxEventRecorded() {}
}
