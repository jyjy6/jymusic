package jymusic.jym_payment_service.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import jymusic.jym_payment_service.common.GlobalErrorHandler.GlobalException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderClient {

    private final RestClient orderRestClient;

    /**
     * 결제 준비 시 주문 정보를 조회하는 동기 REST 메서드.
     * 적용 순서 주의: @Retry 가 먼저 실행되고 실패를 소진한 후 @CircuitBreaker 가 카운트를 시작합니다.
     */
    @CircuitBreaker(name = "orderService", fallbackMethod = "getOrderInfoFallback")
    @Retry(name = "orderService")
    @SuppressWarnings("unchecked")
    public OrderInfo getOrderInfo(Long orderId, String memberId) {
        Map<String, Object> response = orderRestClient.get()
                .uri("/api/v1/orders/{orderId}", orderId)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                    throw new GlobalException("주문을 찾을 수 없습니다.", "ERR_ORDER_NOT_FOUND", HttpStatus.NOT_FOUND);
                })
                .body(Map.class);

        if (response == null) {
            throw new GlobalException("주문을 찾을 수 없습니다.", "ERR_ORDER_NOT_FOUND", HttpStatus.NOT_FOUND);
        }

        String status = (String) response.get("status");
        BigDecimal totalAmount = new BigDecimal(response.get("totalAmount").toString());
        return new OrderInfo(orderId, status, totalAmount);
    }

    /**
     * Fallback 메서드.
     * 서킷 브레이커가 OPEN 상태이거나 재시도 후에도 최종 실패한 경우 실행됩니다.
     * Toss Payments 승인 비교 대상 금액이 없으면 금액 변조 취약점에 노출되므로 즉시 예외를 발생시킵니다.
     *
     * @param orderId  요청했던 주문 ID
     * @param memberId 요청 회원 ID
     * @param t        발생한 예외
     */
    public OrderInfo getOrderInfoFallback(Long orderId, String memberId, Throwable t) {
        log.warn("Order API 호출(결제 금액 검증) 실패. Circuit Breaker Fallback 실행: orderId={}", orderId, t);
        throw new GlobalException(
                "현재 주문 정보를 확인할 수 없어 결제를 진행할 수 없습니다. 잠시 후 시도해주세요.",
                "ERR_ORDER_API_UNAVAILABLE",
                HttpStatus.SERVICE_UNAVAILABLE
        );
    }

    // ❌ 제거 — Kafka 이벤트(PAYMENT_COMPLETED / PAYMENT_CANCELLED)로 대체됨
    // @Deprecated
    // public void updateOrderStatus(Long orderId, String newStatus) { ... }

    public record OrderInfo(Long orderId, String status, BigDecimal totalAmount) {}
}
