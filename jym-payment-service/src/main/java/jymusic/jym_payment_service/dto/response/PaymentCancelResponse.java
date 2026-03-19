package jymusic.jym_payment_service.dto.response;

import jymusic.jym_payment_service.domain.entity.Payment;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
public class PaymentCancelResponse {
    private Long paymentId;
    private String status;
    private BigDecimal cancelledAmount;
    private LocalDateTime cancelledAt;

    public static PaymentCancelResponse from(Payment payment) {
        return PaymentCancelResponse.builder()
                .paymentId(payment.getId())
                .status(payment.getStatus().name())
                .cancelledAmount(payment.getAmount())
                .cancelledAt(payment.getCancelledAt())
                .build();
    }
}
