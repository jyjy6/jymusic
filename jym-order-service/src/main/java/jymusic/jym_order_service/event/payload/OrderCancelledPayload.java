package jymusic.jym_order_service.event.payload;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 주문 취소 — order-service가 발행 (타임아웃 또는 사용자 취소) */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderCancelledPayload {
    private Long orderId;
    private Long memberId;
    private String reason;  // "STOCK_RESERVATION_FAILED", "PAYMENT_TIMEOUT", "USER_CANCELLED"
}
