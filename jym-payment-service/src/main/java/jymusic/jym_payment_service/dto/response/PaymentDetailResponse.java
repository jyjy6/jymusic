package jymusic.jym_payment_service.dto.response;

import jymusic.jym_payment_service.domain.entity.Payment;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
public class PaymentDetailResponse {
    private Long paymentId;
    private Long orderId;
    private String paymentKey;
    private String status;
    private String method;
    private BigDecimal amount;
    private String pgProvider;
    private LocalDateTime paidAt;
    private LocalDateTime cancelledAt;
    private LocalDateTime createdAt;

    public static PaymentDetailResponse from(Payment payment) {
        return PaymentDetailResponse.builder()
                .paymentId(payment.getId())
                .orderId(payment.getOrderId())
                .paymentKey(payment.getPaymentKey())
                .status(payment.getStatus().name())
                .method(payment.getMethod().name())
                .amount(payment.getAmount())
                .pgProvider(payment.getPgProvider())
                .paidAt(payment.getPaidAt())
                .cancelledAt(payment.getCancelledAt())
                .createdAt(payment.getCreatedAt())
                .build();
    }
}
