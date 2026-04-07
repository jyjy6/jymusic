package jymusic.jym_catalog_service.domain.entity;

public enum StockReservationStatus {
    RESERVED,   // 예약 중 (결제 대기)
    CONFIRMED,  // 확정 (결제 완료)
    RELEASED    // 해제 (결제 실패/취소)
}
