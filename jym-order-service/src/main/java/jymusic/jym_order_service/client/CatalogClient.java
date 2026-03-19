package jymusic.jym_order_service.client;

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

    @SuppressWarnings("unchecked")
    public ProductInfo getProductInfo(Long productId) {
        try {
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
        } catch (GlobalException e) {
            throw e;
        } catch (Exception e) {
            log.error("catalog-service 호출 실패: productId={}", productId, e);
            throw new GlobalException("상품 정보를 가져오는 중 오류가 발생했습니다.", "ERR_CATALOG_UNAVAILABLE", HttpStatus.SERVICE_UNAVAILABLE);
        }
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
