package jymusic.jym_payment_service.event.payload;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentFailedPayload {
    private Long orderId;
    private Long memberId;
    private String reason;
}
