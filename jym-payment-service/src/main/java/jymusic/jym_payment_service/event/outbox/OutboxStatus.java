package jymusic.jym_payment_service.event.outbox;

/**
 * Outbox 이벤트의 발행 상태.
 *
 *  - PENDING   : 발행 대기 — Polling Publisher가 다음 사이클에서 발행을 시도합니다.
 *  - PUBLISHED : Kafka 발행 성공 — 더 이상 처리하지 않습니다.
 *  - FAILED    : 누적 재시도 한계를 초과한 상태 — 운영자가 수동으로 확인 후 처리해야 합니다.
 */
public enum OutboxStatus {
    PENDING,
    PUBLISHED,
    FAILED
}
