package jymusic.jym_payment_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@NoArgsConstructor
public class PaymentCancelRequest {

    @NotBlank(message = "paymentKey는 필수입니다.")
    private String paymentKey;

    @NotBlank(message = "취소 사유는 필수입니다.")
    @Size(max = 200, message = "취소 사유는 200자 이내여야 합니다.")
    private String cancelReason;

    private BigDecimal cancelAmount;
}
