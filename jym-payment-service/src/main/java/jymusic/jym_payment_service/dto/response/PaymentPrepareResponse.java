package jymusic.jym_payment_service.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class PaymentPrepareResponse {
    private String clientKey;
    private Long orderId;
    private BigDecimal amount;
}
