package jymusic.jym_payment_service.dto.response;

import jymusic.jym_payment_service.domain.entity.Payment;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
public class PaymentConfirmResponse {
    private Long paymentId;
    private String transactionId;
    private String status;
    private String method;
    private BigDecimal paidAmount;
    private LocalDateTime paidAt;

    public static PaymentConfirmResponse from(Payment payment) {
        return PaymentConfirmResponse.builder()
                .paymentId(payment.getId())
                .transactionId(payment.getPgTransactionId())
                .status(payment.getStatus().name())
                .method(payment.getMethod().name())
                .paidAmount(payment.getAmount())
                .paidAt(payment.getPaidAt())
                .build();
    }
}
