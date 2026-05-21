package jymusic.jym_catalog_service.event.inbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 컨슈머가 이벤트를 처리하기 전 호출하는 멱등성 가드.
 *
 * 트랜잭션 안에서 (eventId, consumerGroup) 으로 inbox row 를 INSERT 시도하며,
 * unique 제약 위반이 발생하면 false 를 반환합니다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class InboxIdempotencyGuard {

    private final InboxEventRepository inboxEventRepository;

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean tryMarkProcessed(String eventId,
                                     String consumerGroup,
                                     String eventType,
                                     ConsumerRecord<?, ?> record) {
        if (eventId == null || eventId.isBlank()) {
            log.warn("eventId 누락 — 멱등성 보장 불가, 처리 진행: type={}, topic={}, offset={}",
                    eventType,
                    record != null ? record.topic() : null,
                    record != null ? record.offset() : null);
            return true;
        }

        InboxEvent inbox = InboxEvent.builder()
                .eventId(eventId)
                .consumerGroup(consumerGroup)
                .eventType(eventType)
                .topic(record != null ? record.topic() : null)
                .partitionNum(record != null ? record.partition() : null)
                .offsetNum(record != null ? record.offset() : null)
                .build();

        try {
            inboxEventRepository.saveAndFlush(inbox);
            return true;
        } catch (DataIntegrityViolationException e) {
            log.info("이미 처리된 이벤트, skip: eventId={}, group={}, type={}",
                    eventId, consumerGroup, eventType);
            return false;
        }
    }
}
