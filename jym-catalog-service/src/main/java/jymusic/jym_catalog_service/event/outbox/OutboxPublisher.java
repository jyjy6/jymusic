package jymusic.jym_catalog_service.event.outbox;

import jymusic.jym_catalog_service.event.common.EventEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * outbox_event 테이블의 PENDING 레코드를 폴링하여 Kafka 에 발행합니다.
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

    private final AtomicBoolean running = new AtomicBoolean(false);

    @Scheduled(fixedDelayString = "${app.outbox.poll-interval-ms:500}")
    public void pollAndPublish() {
        publishPending();
    }

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
