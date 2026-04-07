package jymusic.jym_payment_service.client;

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

    // ✅ 유지 — 결제 준비 시 주문 정보 조회 (동기 REST)
    @SuppressWarnings("unchecked")
    public OrderInfo getOrderInfo(Long orderId, String memberId) {
        try {
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
        } catch (GlobalException e) {
            throw e;
        } catch (Exception e) {
            log.error("order-service 호출 실패: orderId={}", orderId, e);
            throw new GlobalException("주문 서비스 연결 오류가 발생했습니다.", "ERR_ORDER_SERVICE_UNAVAILABLE", HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    // ❌ 제거 — Kafka 이벤트(PAYMENT_COMPLETED / PAYMENT_CANCELLED)로 대체됨
    // @Deprecated
    // public void updateOrderStatus(Long orderId, String newStatus) { ... }

    public record OrderInfo(Long orderId, String status, BigDecimal totalAmount) {}
}
