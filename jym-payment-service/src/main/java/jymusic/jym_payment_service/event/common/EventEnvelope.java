package jymusic.jym_payment_service.event.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 모든 Kafka 이벤트의 공통 래퍼.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventEnvelope<T> {
    private String eventId;
    private String eventType;
    private int version;
    private LocalDateTime timestamp;
    private String source;
    private T payload;
}
