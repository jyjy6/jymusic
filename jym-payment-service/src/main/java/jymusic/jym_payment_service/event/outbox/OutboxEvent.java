package jymusic.jym_payment_service.event.outbox;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Transactional Outbox 패턴의 이벤트 저장 엔티티.
 *
 * 비즈니스 트랜잭션 안에서 도메인 데이터와 함께 INSERT 되며,
 * 별도의 Polling Publisher({@link OutboxPublisher})가 PENDING 상태 레코드를 읽어
 * Kafka 로 발행한 뒤 PUBLISHED 로 마킹합니다.
 */
@Entity
@Table(
        name = "outbox_event",
        indexes = {
                @Index(name = "idx_outbox_status_id", columnList = "status, id"),
                @Index(name = "idx_outbox_event_id", columnList = "event_id", unique = true)
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class OutboxEvent {

    private static final int MAX_ERROR_LENGTH = 1000;
    private static final int MAX_RETRY_BEFORE_FAILED = 30;

    /** Exponential backoff 의 1차 실패 시 대기 시간(초). retryCount=1 일 때 적용. */
    private static final long BASE_RETRY_DELAY_SECONDS = 1;
    /** Backoff 지연의 상한(초). 2^N 증가가 무한정 커지지 않도록 cap. */
    private static final long MAX_RETRY_DELAY_SECONDS = 300;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true, length = 36)
    private String eventId;

    @Column(name = "aggregate_type", nullable = false, length = 30)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, length = 100)
    private String aggregateId;

    @Column(nullable = false, length = 100)
    private String topic;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private OutboxStatus status;

    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private int retryCount = 0;

    @Column(name = "last_error", length = MAX_ERROR_LENGTH)
    private String lastError;

    /**
     * 다음 재시도 가능 시각 (Exponential Backoff).
     *  - NULL: 첫 INSERT 또는 PUBLISHED 후. 즉시 폴링 대상.
     *  - 미래 시각: 발행 실패 후 backoff 적용됨. 그 시각 이후에만 폴링됨.
     * 폴링 쿼리에서 {@code (next_retry_at IS NULL OR next_retry_at <= NOW())} 로 필터됩니다.
     */
    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.status == null) {
            this.status = OutboxStatus.PENDING;
        }
    }

    public void markPublished() {
        this.status = OutboxStatus.PUBLISHED;
        this.publishedAt = LocalDateTime.now();
        this.lastError = null;
        this.nextRetryAt = null;
    }

    /**
     * 발행 실패 시 호출. retryCount 를 증가시키고 Exponential Backoff 으로
     * nextRetryAt 을 산정. retryCount 가 {@link #MAX_RETRY_BEFORE_FAILED} 에 도달하면
     * FAILED 로 격리. 자세한 알고리즘은 SPEC §4.6 참조.
     */
    public void markFailed(String error) {
        this.retryCount++;
        if (error != null && error.length() > MAX_ERROR_LENGTH) {
            this.lastError = error.substring(0, MAX_ERROR_LENGTH);
        } else {
            this.lastError = error;
        }

        if (this.retryCount >= MAX_RETRY_BEFORE_FAILED) {
            this.status = OutboxStatus.FAILED;
            this.nextRetryAt = null;
            return;
        }
        long delaySeconds = computeBackoffDelaySeconds(this.retryCount);
        this.nextRetryAt = LocalDateTime.now().plusSeconds(delaySeconds);
    }

    private static long computeBackoffDelaySeconds(int retryCount) {
        int shift = Math.min(retryCount - 1, 30);
        long exponential = BASE_RETRY_DELAY_SECONDS * (1L << shift);
        return Math.min(exponential, MAX_RETRY_DELAY_SECONDS);
    }
}
