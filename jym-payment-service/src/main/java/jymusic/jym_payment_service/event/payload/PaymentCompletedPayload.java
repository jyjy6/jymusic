package jymusic.jym_payment_service.event.payload;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

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
