package jymusic.jym_order_service.event.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import jymusic.jym_order_service.event.common.EventEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * outbox_event 테이블의 PENDING 레코드를 폴링하여 Kafka 에 발행합니다.
 *
 *  - 정기 폴링: {@code app.outbox.poll-interval-ms} (기본 500ms)
 *  - 즉시 트리거: {@link OutboxEventRecorder.OutboxEventRecorded} 이벤트의 AFTER_COMMIT
 *
 * 멀티 인스턴스 환경에서는 SELECT ... FOR UPDATE SKIP LOCKED 로 락을 잡아
 * 같은 row 가 동시에 발행되지 않도록 합니다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${spring.application.name}")
    private String serviceName;

    @Value("${app.outbox.batch-size:100}")
    private int batchSize;

    @Value("${app.outbox.kafka-send-timeout-ms:5000}")
    private long kafkaSendTimeoutMs;

    /**
     * 동시에 여러 스레드가 publishPending 을 호출하지 않도록 단순 가드.
     * 한 인스턴스 안에서 폴링과 AFTER_COMMIT 트리거가 동시에 실행되는 것을 방지합니다.
     */
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Scheduled(fixedDelayString = "${app.outbox.poll-interval-ms:500}")
    public void pollAndPublish() {
        publishPending();
    }

    /**
     * 비즈니스 트랜잭션 commit 직후 한 번 깨워서 발행 지연을 줄입니다.
     * 폴링과는 독립적으로 동작 — 실패해도 다음 폴 사이클이 복구합니다.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOutboxEventRecorded(OutboxEventRecorder.OutboxEventRecorded ignored) {
        publishPending();
    }

    @Transactional
    public void publishPending() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        try {
            List<OutboxEvent> batch = outboxEventRepository.findPendingForUpdate(batchSize);
            if (batch.isEmpty()) {
                return;
            }
            for (OutboxEvent event : batch) {
                publishOne(event);
            }
        } finally {
            running.set(false);
        }
    }

    private void publishOne(OutboxEvent event) {
        try {
            Object payloadObject = objectMapper.readValue(event.getPayload(), Object.class);

            EventEnvelope<Object> envelope = EventEnvelope.<Object>builder()
                    .eventId(event.getEventId())
                    .eventType(event.getEventType())
                    .version(1)
                    .timestamp(event.getCreatedAt())
                    .source(serviceName)
                    .payload(payloadObject)
                    .build();

            kafkaTemplate.send(event.getTopic(), event.getAggregateId(), envelope)
                    .get(kafkaSendTimeoutMs, TimeUnit.MILLISECONDS);

            event.markPublished();
            log.info("Outbox 발행 성공: eventId={}, topic={}, type={}",
                    event.getEventId(), event.getTopic(), event.getEventType());
        } catch (Exception e) {
            event.markFailed(e.getMessage());
            log.error("Outbox 발행 실패: eventId={}, retry={}, type={}",
                    event.getEventId(), event.getRetryCount(), event.getEventType(), e);
        }
    }
}
