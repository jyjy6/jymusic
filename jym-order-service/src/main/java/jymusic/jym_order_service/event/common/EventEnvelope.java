package jymusic.jym_order_service.event.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 모든 Kafka 이벤트의 공통 래퍼.
 * 이벤트 메타데이터(추적, 중복 방지)와 실제 페이로드를 포함.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventEnvelope<T> {
    private String eventId;        // UUID — 이벤트 고유 식별자 (멱등성 체크용)
    private String eventType;      // 이벤트 타입 (예: "ORDER_CREATED")
    private int version;           // 스키마 버전 (하위 호환성 관리)
    private LocalDateTime timestamp; // 이벤트 발생 시각
    private String source;         // 발행 서비스명 (예: "jym-order-service")
    private T payload;             // 실제 이벤트 데이터
}
