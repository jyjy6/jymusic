package jymusic.jym_order_service.event.payload;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 결제 실패 — payment-service가 발행 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentFailedPayload {
    private Long orderId;
    private Long memberId;
    private String reason;
}
