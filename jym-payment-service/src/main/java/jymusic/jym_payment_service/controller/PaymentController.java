package jymusic.jym_payment_service.controller;

import jakarta.validation.Valid;
import jymusic.jym_payment_service.dto.request.PaymentCancelRequest;
import jymusic.jym_payment_service.dto.request.PaymentConfirmRequest;
import jymusic.jym_payment_service.dto.request.PaymentPrepareRequest;
import jymusic.jym_payment_service.dto.response.*;
import jymusic.jym_payment_service.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/prepare")
    public ResponseEntity<PaymentPrepareResponse> prepare(
            @AuthenticationPrincipal String memberId,
            @Valid @RequestBody PaymentPrepareRequest request) {
        return ResponseEntity.ok(paymentService.prepare(Long.parseLong(memberId), request));
    }

    @PostMapping("/confirm")
    public ResponseEntity<PaymentConfirmResponse> confirm(
            @AuthenticationPrincipal String memberId,
            @Valid @RequestBody PaymentConfirmRequest request) {
        return ResponseEntity.ok(paymentService.confirm(Long.parseLong(memberId), request));
    }

    @PostMapping("/cancel")
    public ResponseEntity<PaymentCancelResponse> cancel(
            @AuthenticationPrincipal String memberId,
            @Valid @RequestBody PaymentCancelRequest request) {
        return ResponseEntity.ok(paymentService.cancel(Long.parseLong(memberId), request));
    }

    @GetMapping("/{paymentId}")
    public ResponseEntity<PaymentDetailResponse> getPaymentDetail(
            @PathVariable Long paymentId) {
        return ResponseEntity.ok(paymentService.getPaymentDetail(paymentId));
    }

    @GetMapping("/order/{orderId}")
    public ResponseEntity<PaymentDetailResponse> getPaymentByOrderId(
            @PathVariable Long orderId) {
        return ResponseEntity.ok(paymentService.getPaymentByOrderId(orderId));
    }
}
