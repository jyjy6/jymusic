package jymusic.jym_payment_service.client;

import jakarta.annotation.PostConstruct;
import jymusic.jym_payment_service.common.GlobalErrorHandler.GlobalException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

@Component
@Slf4j
public class TossPaymentsClient {

    @Value("${toss.payments.secret-key}")
    private String secretKey;

    @Value("${toss.payments.client-key}")
    private String clientKey;

    @Value("${toss.payments.api-url}")
    private String apiUrl;

    private RestClient tossRestClient;

    @PostConstruct
    void init() {
        String encoded = Base64.getEncoder().encodeToString((secretKey + ":").getBytes(StandardCharsets.UTF_8));
        this.tossRestClient = RestClient.builder()
                .baseUrl(apiUrl)
                .defaultHeader("Authorization", "Basic " + encoded)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    public String getClientKey() {
        return clientKey;
    }

    @SuppressWarnings("unchecked")
    public TossConfirmResult confirmPayment(String paymentKey, Long orderId, BigDecimal amount) {
        Map<String, Object> requestBody = Map.of(
                "paymentKey", paymentKey,
                "orderId", String.valueOf(orderId),
                "amount", amount
        );
        try {
            Map<String, Object> result = tossRestClient.post()
                    .uri("/{paymentKey}", paymentKey)
                    .body(requestBody)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        throw new GlobalException("결제 승인에 실패했습니다.", "ERR_TOSS_CONFIRM_FAILED", HttpStatus.PAYMENT_REQUIRED);
                    })
                    .body(Map.class);

            if (result == null) {
                throw new GlobalException("Toss 결제 승인 응답이 없습니다.", "ERR_TOSS_EMPTY_RESPONSE", HttpStatus.INTERNAL_SERVER_ERROR);
            }

            String pgTransactionId = (String) result.get("transactionKey");
            String method = (String) result.get("method");
            return new TossConfirmResult(pgTransactionId, convertMethod(method));
        } catch (GlobalException e) {
            throw e;
        } catch (Exception e) {
            log.error("Toss 결제 승인 실패: paymentKey={}", paymentKey, e);
            throw new GlobalException("결제 서버 오류가 발생했습니다.", "ERR_TOSS_SERVER_ERROR", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public void cancelPayment(String paymentKey, String cancelReason, BigDecimal cancelAmount) {
        Map<String, Object> requestBody = cancelAmount != null
                ? Map.of("cancelReason", cancelReason, "cancelAmount", cancelAmount)
                : Map.of("cancelReason", cancelReason);
        try {
            tossRestClient.post()
                    .uri("/{paymentKey}/cancel", paymentKey)
                    .body(requestBody)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        throw new GlobalException("결제 취소에 실패했습니다.", "ERR_TOSS_CANCEL_FAILED", HttpStatus.BAD_REQUEST);
                    })
                    .toBodilessEntity();
        } catch (GlobalException e) {
            throw e;
        } catch (Exception e) {
            log.error("Toss 결제 취소 실패: paymentKey={}", paymentKey, e);
            throw new GlobalException("결제 취소 처리 중 오류가 발생했습니다.", "ERR_TOSS_CANCEL_FAILED", HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    private String convertMethod(String tossMethod) {
        if (tossMethod == null) return "CARD";
        return switch (tossMethod) {
            case "카드" -> "CARD";
            case "계좌이체" -> "VIRTUAL_ACCOUNT";
            case "카카오페이" -> "KAKAO_PAY";
            case "네이버페이" -> "NAVER_PAY";
            default -> "CARD";
        };
    }

    public record TossConfirmResult(String pgTransactionId, String method) {}
}
