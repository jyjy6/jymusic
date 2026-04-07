package jymusic.jym_order_service.event.payload;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** 결제 승인 완료 — payment-service가 발행 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentCompletedPayload {
    private Long orderId;
    private Long memberId;
    private String paymentKey;
    private BigDecimal amount;
    private String method;
}
