package jymusic.jym_order_service.event.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 비즈니스 트랜잭션 안에서 호출되어 outbox_event 테이블에 INSERT 만 수행합니다.
 * 실제 Kafka 발행은 {@link OutboxPublisher} 가 폴링하면서 비동기로 수행합니다.
 *
 * 이렇게 함으로써 도메인 데이터와 발행할 이벤트가 같은 DB 트랜잭션에 묶여
 * dual-write 정합성 문제(시나리오: DB commit 후 Kafka 실패 / Kafka 발행 후 DB rollback)
 * 가 모두 해결됩니다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxEventRecorder {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher applicationEventPublisher;

    /**
     * 호출자의 트랜잭션에 합류하여 outbox row 를 INSERT 합니다.
     * 트랜잭션 없이 호출되면 즉시 예외를 던집니다 — 트랜잭션 보장이 필수이기 때문입니다.
     */
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
        } catch (JsonProcessingException e) {
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

        // 트랜잭션 commit 직후 OutboxPublisher 를 한 번 깨워주기 위한 트리거.
        // 폴링 주기 대비 발행 지연을 줄이는 보너스 — 실패해도 다음 폴링에서 복구됩니다.
        applicationEventPublisher.publishEvent(new OutboxEventRecorded());
        log.debug("Outbox 이벤트 기록: eventId={}, topic={}, type={}",
                event.getEventId(), topic, eventType);
    }

    /**
     * 트랜잭션 commit 후 즉시 발행 트리거를 위한 Spring ApplicationEvent.
     */
    public record OutboxEventRecorded() {}
}
