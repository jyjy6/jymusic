package jymusic.jym_payment_service.service;

import jymusic.jym_payment_service.client.OrderClient;
import jymusic.jym_payment_service.client.TossPaymentsClient;
import jymusic.jym_payment_service.common.GlobalErrorHandler.GlobalException;
import jymusic.jym_payment_service.domain.entity.*;
import jymusic.jym_payment_service.domain.repository.PaymentPrepareRepository;
import jymusic.jym_payment_service.domain.repository.PaymentRepository;
import jymusic.jym_payment_service.dto.request.PaymentConfirmRequest;
import jymusic.jym_payment_service.dto.request.PaymentPrepareRequest;
import jymusic.jym_payment_service.dto.response.PaymentConfirmResponse;
import jymusic.jym_payment_service.dto.response.PaymentPrepareResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock PaymentRepository paymentRepository;
    @Mock PaymentPrepareRepository paymentPrepareRepository;
    @Mock OrderClient orderClient;
    @Mock TossPaymentsClient tossClient;

    @InjectMocks PaymentService paymentService;

    private static final Long MEMBER_ID = 1L;
    private static final Long ORDER_ID = 100L;
    private static final BigDecimal AMOUNT = new BigDecimal("29000");

    @Test
    @DisplayName("결제 준비 - PENDING 주문에 대해 정상 준비 응답을 반환한다")
    void prepare_success() {
        given(orderClient.getOrderInfo(ORDER_ID, String.valueOf(MEMBER_ID)))
                .willReturn(new OrderClient.OrderInfo(ORDER_ID, "PENDING", AMOUNT));
        given(paymentPrepareRepository.findByOrderId(ORDER_ID)).willReturn(Optional.empty());
        given(paymentPrepareRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(tossClient.getClientKey()).willReturn("test_ck_xxx");

        PaymentPrepareRequest req = new PaymentPrepareRequest();
        setField(req, "orderId", ORDER_ID);
        setField(req, "amount", AMOUNT);

        PaymentPrepareResponse response = paymentService.prepare(MEMBER_ID, req);

        assertThat(response.getClientKey()).isEqualTo("test_ck_xxx");
        assertThat(response.getOrderId()).isEqualTo(ORDER_ID);
        assertThat(response.getAmount()).isEqualByComparingTo(AMOUNT);
    }

    @Test
    @DisplayName("결제 준비 - PAID 주문에 결제 시도 시 GlobalException 발생")
    void prepare_throwsWhenOrderNotPending() {
        given(orderClient.getOrderInfo(ORDER_ID, String.valueOf(MEMBER_ID)))
                .willReturn(new OrderClient.OrderInfo(ORDER_ID, "PAID", AMOUNT));

        PaymentPrepareRequest req = new PaymentPrepareRequest();
        setField(req, "orderId", ORDER_ID);
        setField(req, "amount", AMOUNT);

        assertThatThrownBy(() -> paymentService.prepare(MEMBER_ID, req))
                .isInstanceOf(GlobalException.class)
                .hasMessageContaining("결제 대기 상태의 주문만");
    }

    @Test
    @DisplayName("결제 준비 - 금액 불일치 시 GlobalException 발생")
    void prepare_throwsWhenAmountMismatch() {
        given(orderClient.getOrderInfo(ORDER_ID, String.valueOf(MEMBER_ID)))
                .willReturn(new OrderClient.OrderInfo(ORDER_ID, "PENDING", new BigDecimal("50000")));

        PaymentPrepareRequest req = new PaymentPrepareRequest();
        setField(req, "orderId", ORDER_ID);
        setField(req, "amount", AMOUNT);

        assertThatThrownBy(() -> paymentService.prepare(MEMBER_ID, req))
                .isInstanceOf(GlobalException.class)
                .hasMessageContaining("결제 금액이 주문 금액과 다릅니다");
    }

    @Test
    @DisplayName("결제 승인 - 만료된 prepare 레코드 사용 시 GlobalException 발생")
    void confirm_throwsWhenPrepareExpired() {
        PaymentPrepare expiredPrepare = PaymentPrepare.builder()
                .orderId(ORDER_ID)
                .amount(AMOUNT)
                .expiresAt(LocalDateTime.now().minusMinutes(1))
                .build();

        given(paymentPrepareRepository.findByOrderId(ORDER_ID)).willReturn(Optional.of(expiredPrepare));

        PaymentConfirmRequest req = new PaymentConfirmRequest();
        setField(req, "paymentKey", "test_pk_xxx");
        setField(req, "orderId", ORDER_ID);
        setField(req, "amount", AMOUNT);

        assertThatThrownBy(() -> paymentService.confirm(MEMBER_ID, req))
                .isInstanceOf(GlobalException.class)
                .hasMessageContaining("만료되었습니다");
    }

    @Test
    @DisplayName("결제 승인 - prepare 없으면 GlobalException 발생")
    void confirm_throwsWhenPrepareNotFound() {
        given(paymentPrepareRepository.findByOrderId(ORDER_ID)).willReturn(Optional.empty());

        PaymentConfirmRequest req = new PaymentConfirmRequest();
        setField(req, "paymentKey", "test_pk_xxx");
        setField(req, "orderId", ORDER_ID);
        setField(req, "amount", AMOUNT);

        assertThatThrownBy(() -> paymentService.confirm(MEMBER_ID, req))
                .isInstanceOf(GlobalException.class)
                .hasMessageContaining("결제 준비 정보를 찾을 수 없습니다");
    }

    @Test
    @DisplayName("결제 취소 - SUCCESS 상태 결제의 취소가 정상 처리된다")
    void cancel_success() {
        Payment payment = Payment.builder()
                .orderId(ORDER_ID)
                .memberId(MEMBER_ID)
                .paymentKey("test_pk_xxx")
                .method(PaymentMethod.CARD)
                .amount(AMOUNT)
                .status(PaymentStatus.SUCCESS)
                .build();

        given(paymentRepository.findByPaymentKey("test_pk_xxx")).willReturn(Optional.of(payment));

        jymusic.jym_payment_service.dto.request.PaymentCancelRequest req =
                new jymusic.jym_payment_service.dto.request.PaymentCancelRequest();
        setField(req, "paymentKey", "test_pk_xxx");
        setField(req, "cancelReason", "단순 변심");
        setField(req, "cancelAmount", null);

        var response = paymentService.cancel(MEMBER_ID, req);

        assertThat(response.getStatus()).isEqualTo("CANCELLED");
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
