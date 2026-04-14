package jymusic.jym_payment_service.service;

import jymusic.jym_payment_service.client.OrderClient;
import jymusic.jym_payment_service.client.TossPaymentsClient;
import jymusic.jym_payment_service.common.GlobalErrorHandler.GlobalException;
import jymusic.jym_payment_service.domain.entity.*;
import jymusic.jym_payment_service.domain.repository.PaymentPrepareRepository;
import jymusic.jym_payment_service.domain.repository.PaymentRepository;
import jymusic.jym_payment_service.dto.request.PaymentCancelRequest;
import jymusic.jym_payment_service.dto.request.PaymentConfirmRequest;
import jymusic.jym_payment_service.dto.request.PaymentPrepareRequest;
import jymusic.jym_payment_service.dto.response.*;
import jymusic.jym_payment_service.event.common.EventTypes;
import jymusic.jym_payment_service.event.common.KafkaTopics;
import jymusic.jym_payment_service.event.payload.PaymentCancelledPayload;
import jymusic.jym_payment_service.event.payload.PaymentCompletedPayload;
import jymusic.jym_payment_service.event.payload.PaymentFailedPayload;
import jymusic.jym_payment_service.event.publisher.EventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentPrepareRepository paymentPrepareRepository;
    private final OrderClient orderClient;
    private final TossPaymentsClient tossClient;
    private final EventPublisher eventPublisher;

    @Transactional
    public PaymentPrepareResponse prepare(Long memberId, PaymentPrepareRequest request) {
        OrderClient.OrderInfo orderInfo = orderClient.getOrderInfo(request.getOrderId(), String.valueOf(memberId));

        // Saga 도입: PENDING 또는 STOCK_RESERVED 상태 모두 결제 허용
        if (!"PENDING".equals(orderInfo.status()) && !"STOCK_RESERVED".equals(orderInfo.status())) {
            throw new GlobalException("결제 대기 상태의 주문만 결제할 수 있습니다.", "ERR_INVALID_ORDER_STATUS");
        }

        if (orderInfo.totalAmount().compareTo(request.getAmount()) != 0) {
            throw new GlobalException("결제 금액이 주문 금액과 다릅니다.", "ERR_AMOUNT_MISMATCH");
        }

        PaymentPrepare prepare = paymentPrepareRepository.findByOrderId(request.getOrderId())
                .map(existing -> {
                    existing.updateAmount(request.getAmount());
                    existing.updateExpiresAt(LocalDateTime.now().plusMinutes(30));
                    return existing;
                })
                .orElseGet(() -> PaymentPrepare.builder()
                        .orderId(request.getOrderId())
                        .amount(request.getAmount())
                        .expiresAt(LocalDateTime.now().plusMinutes(30))
                        .build());
        paymentPrepareRepository.save(prepare);

        return PaymentPrepareResponse.builder()
                .clientKey(tossClient.getClientKey())
                .orderId(request.getOrderId())
                .amount(request.getAmount())
                .build();
    }

    @Transactional
    public PaymentConfirmResponse confirm(Long memberId, PaymentConfirmRequest request) {
        PaymentPrepare prepare = paymentPrepareRepository.findByOrderId(request.getOrderId())
                .orElseThrow(() -> new GlobalException("결제 준비 정보를 찾을 수 없습니다.", "ERR_PREPARE_NOT_FOUND", HttpStatus.NOT_FOUND));

        if (prepare.isExpired()) {
            throw new GlobalException("결제 준비 정보가 만료되었습니다.", "ERR_PREPARE_EXPIRED");
        }

        if (prepare.getAmount().compareTo(request.getAmount()) != 0) {
            throw new GlobalException("결제 금액이 일치하지 않습니다.", "ERR_AMOUNT_MISMATCH");
        }

        try {
            TossPaymentsClient.TossConfirmResult tossResult =
                    tossClient.confirmPayment(request.getPaymentKey(), request.getOrderId(), request.getAmount());

            PaymentMethod method = PaymentMethod.valueOf(tossResult.method());

            Payment payment = Payment.builder()
                    .orderId(request.getOrderId())
                    .memberId(memberId)
                    .paymentKey(request.getPaymentKey())
                    .method(method)
                    .amount(request.getAmount())
                    .status(PaymentStatus.PENDING)
                    .build();
            payment.markSuccess(tossResult.pgTransactionId(), LocalDateTime.now());

            Payment savedPayment = paymentRepository.save(payment);
            paymentPrepareRepository.deleteByOrderId(request.getOrderId());

            // [CHANGED] REST 호출 → Kafka 이벤트 발행
            eventPublisher.publish(
                    KafkaTopics.PAYMENT_EVENTS,
                    request.getOrderId().toString(),
                    EventTypes.PAYMENT_COMPLETED,
                    PaymentCompletedPayload.builder()
                            .orderId(request.getOrderId())
                            .memberId(memberId)
                            .paymentKey(request.getPaymentKey())
                            .amount(request.getAmount())
                            .method(tossResult.method())
                            .build()
            );

            return PaymentConfirmResponse.from(savedPayment);

        } catch (GlobalException e) {
            // Toss API 거절 (카드 한도 초과, 유효하지 않은 카드 등)
            eventPublisher.publish(
                    KafkaTopics.PAYMENT_EVENTS,
                    request.getOrderId().toString(),
                    EventTypes.PAYMENT_FAILED,
                    PaymentFailedPayload.builder()
                            .orderId(request.getOrderId())
                            .memberId(memberId)
                            .reason(e.getMessage())
                            .build()
            );
            throw e;  // 프론트에 에러 응답
        }
    }

    @Transactional
    public PaymentCancelResponse cancel(Long memberId, PaymentCancelRequest request) {
        Payment payment = paymentRepository.findByPaymentKey(request.getPaymentKey())
                .orElseThrow(() -> new GlobalException("결제 정보를 찾을 수 없습니다.", "ERR_PAYMENT_NOT_FOUND", HttpStatus.NOT_FOUND));

        if (!payment.getMemberId().equals(memberId)) {
            throw new GlobalException("접근 권한이 없습니다.", "ERR_FORBIDDEN", HttpStatus.FORBIDDEN);
        }

        if (payment.getStatus() != PaymentStatus.SUCCESS) {
            throw new GlobalException("취소 가능한 결제 상태가 아닙니다.", "ERR_INVALID_PAYMENT_STATUS");
        }

        tossClient.cancelPayment(request.getPaymentKey(), request.getCancelReason(), request.getCancelAmount());
        payment.markCancelled(LocalDateTime.now());

        // [CHANGED] REST 호출 → Kafka 이벤트 발행
        eventPublisher.publish(
                KafkaTopics.PAYMENT_EVENTS,
                payment.getOrderId().toString(),
                EventTypes.PAYMENT_CANCELLED,
                PaymentCancelledPayload.builder()
                        .orderId(payment.getOrderId())
                        .memberId(memberId)
                        .paymentKey(payment.getPaymentKey())
                        .cancelReason(request.getCancelReason())
                        .build()
        );

        return PaymentCancelResponse.from(payment);
    }

    public PaymentDetailResponse getPaymentDetail(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new GlobalException("결제 정보를 찾을 수 없습니다.", "ERR_PAYMENT_NOT_FOUND", HttpStatus.NOT_FOUND));
        return PaymentDetailResponse.from(payment);
    }

    public PaymentDetailResponse getPaymentByOrderId(Long orderId) {
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new GlobalException("해당 주문의 결제 정보를 찾을 수 없습니다.", "ERR_PAYMENT_NOT_FOUND", HttpStatus.NOT_FOUND));
        return PaymentDetailResponse.from(payment);
    }
}
