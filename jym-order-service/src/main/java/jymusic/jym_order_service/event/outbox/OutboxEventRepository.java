package jymusic.jym_order_service.event.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    /**
     * MySQL 8+ 의 SKIP LOCKED 를 이용하여, 멀티 인스턴스 환경에서도
     * 같은 row 를 동시에 처리하지 않도록 합니다.
     *
     * <p><b>next_retry_at 필터</b>: NULL(첫 INSERT) 이거나 이미 지난 시각만 폴링 대상.
     * 발행 실패로 backoff 가 세팅된 row 는 nextRetryAt 이 도래할 때까지 자동 제외됩니다.
     * Exponential Backoff 알고리즘은 {@link OutboxEvent#markFailed(String)} 및 SPEC §4.6 참조.</p>
     *
     * <p>본 메서드는 반드시 트랜잭션 안에서 호출되어야 하며,
     * 호출자가 같은 트랜잭션 안에서 status 를 갱신하고 commit 해야 락이 해제됩니다.</p>
     */
    @Query(
            value = "SELECT * FROM outbox_event " +
                    "WHERE status = 'PENDING' " +
                    "  AND (next_retry_at IS NULL OR next_retry_at <= NOW()) " +
                    "ORDER BY id ASC " +
                    "LIMIT :limit " +
                    "FOR UPDATE SKIP LOCKED",
            nativeQuery = true
    )
    List<OutboxEvent> findPendingForUpdate(@Param("limit") int limit);
}
