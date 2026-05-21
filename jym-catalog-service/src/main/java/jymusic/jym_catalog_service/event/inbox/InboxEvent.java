package jymusic.jym_catalog_service.event.inbox;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Inbox 패턴 — 컨슈머가 처리 완료한 이벤트의 id 를 기록하여 재처리(중복) 를 차단합니다.
 *
 * (eventId, consumerGroup) 조합에 unique 제약을 두어
 * 같은 이벤트가 두 번 들어왔을 때 INSERT 가 실패하도록 합니다.
 */
@Entity
@Table(
        name = "inbox_event",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_inbox_event_group",
                columnNames = {"event_id", "consumer_group"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class InboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, length = 36)
    private String eventId;

    @Column(name = "consumer_group", nullable = false, length = 100)
    private String consumerGroup;

    @Column(name = "event_type", length = 50)
    private String eventType;

    @Column(length = 100)
    private String topic;

    @Column(name = "partition_num")
    private Integer partitionNum;

    @Column(name = "offset_num")
    private Long offsetNum;

    @Column(name = "processed_at", nullable = false, updatable = false)
    private LocalDateTime processedAt;

    @PrePersist
    void onCreate() {
        this.processedAt = LocalDateTime.now();
    }
}
