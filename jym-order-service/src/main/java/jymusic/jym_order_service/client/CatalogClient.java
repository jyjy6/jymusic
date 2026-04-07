package jymusic.jym_order_service.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import jymusic.jym_order_service.common.GlobalErrorHandler.GlobalException;
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
public class CatalogClient {

    private final RestClient catalogRestClient;

    /**
     * 상품 정보를 가져오는 동기 REST 메서드.
     * 적용 순서 주의: @Retry 가 먼저 실행되고 실패를 소진한 후 @CircuitBreaker 가 카운트를 시작합니다.
     */
    @CircuitBreaker(name = "catalogService", fallbackMethod = "getProductInfoFallback")
    @Retry(name = "catalogService")
    @SuppressWarnings("unchecked")
    public ProductInfo getProductInfo(Long productId) {
        Map<String, Object> response = catalogRestClient.get()
                .uri("/api/v1/products/{id}", productId)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                    throw new GlobalException("상품을 찾을 수 없습니다.", "ERR_PRODUCT_NOT_FOUND", HttpStatus.NOT_FOUND);
                })
                .body(Map.class);

        if (response == null) {
            throw new GlobalException("상품 정보를 찾을 수 없습니다.", "ERR_PRODUCT_NOT_FOUND", HttpStatus.NOT_FOUND);
        }

        return new ProductInfo(
                productId,
                (String) response.get("title"),
                (String) response.get("artist"),
                (String) response.get("imageUrl"),
                new BigDecimal(response.get("price").toString()),
                (Integer) response.get("stockQuantity")
        );
    }

    /**
     * Fallback 메서드.
     * 서킷 브레이커가 OPEN 상태이거나 재시도 후에도 최종 실패한 경우 실행됩니다.
     * 단가를 알 수 없는 상태에서 주문을 계속 진행하면 매출/정산 이슈를 유발하므로 즉시 예외를 발생시킵니다.
     *
     * @param productId 요청했던 상품 ID
     * @param t         발생한 예외
     */
    public ProductInfo getProductInfoFallback(Long productId, Throwable t) {
        log.warn("Catalog API 호출 실패. Circuit Breaker Fallback 실행: productId={}", productId, t);
        throw new GlobalException(
                "상품 정보를 현재 확인할 수 없습니다. 잠시 후 시도해주세요.",
                "ERR_CATALOG_UNAVAILABLE",
                HttpStatus.SERVICE_UNAVAILABLE
        );
    }

    public record ProductInfo(
            Long productId,
            String title,
            String artist,
            String thumbnailUrl,
            BigDecimal price,
            int stockQuantity
    ) {}
}
