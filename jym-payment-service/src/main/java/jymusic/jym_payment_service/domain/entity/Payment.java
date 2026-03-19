package jymusic.jym_payment_service.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class Payment extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long orderId;

    @Column(nullable = false)
    private Long memberId;

    @Column(nullable = false, unique = true, length = 200)
    private String paymentKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentMethod method;

    @Column(nullable = false, precision = 12, scale = 0)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Column(nullable = false, length = 30)
    @Builder.Default
    private String pgProvider = "TOSS";

    @Column(length = 200)
    private String pgTransactionId;

    @Column(length = 500)
    private String failReason;

    private LocalDateTime paidAt;
    private LocalDateTime cancelledAt;

    public void markSuccess(String pgTransactionId, LocalDateTime paidAt) {
        this.status = PaymentStatus.SUCCESS;
        this.pgTransactionId = pgTransactionId;
        this.paidAt = paidAt;
    }

    public void markFailed(String failReason) {
        this.status = PaymentStatus.FAILED;
        this.failReason = failReason;
    }

    public void markCancelled(LocalDateTime cancelledAt) {
        this.status = PaymentStatus.CANCELLED;
        this.cancelledAt = cancelledAt;
    }
}
