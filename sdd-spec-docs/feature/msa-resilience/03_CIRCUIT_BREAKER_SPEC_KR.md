# 03_CIRCUIT_BREAKER — Circuit Breaker 패턴 구현 스펙

> **목적**: 서비스 간 동기적 REST API 호출 시 발생하는 장애 전파를 차단하기 위한 Circuit Breaker 패턴 도입 스펙
> **선행 문서**: `00_MSA_RESILIENCE_GUIDE_KR.md`
> **영향 범위**: `jym-order-service`, `jym-payment-service`

---

## 1. 개요 및 적용 대상

기존의 동기 REST 호출인 `OrderClient.updateOrderStatus()`는 비동기 처리(Kafka)로 이전되었지만, 일부 동기 호출은 아키텍처 상 여전히 남아있습니다. 이러한 호출은 타겟 서비스 장애 시 스레드를 고갈시키며 호출자 서비스까지 장애를 유발(Cascading Failure)할 수 있으므로, 해당 지점에 **Resilience4j Circuit Breaker 및 Retry**를 적용합니다.

### 1.1 보호 대상 (Target Clients)

| 서비스 (호출자) | 호출 대상 객체 | 역할 | 적용할 정책 |
|---|---|---|---|
| `jym-order-service` | `CatalogClient.getProductInfo` | 주문 시 상품 단가/유효성 조회 | Circuit Breaker + Retry |
| `jym-payment-service` | `OrderClient.getOrderAmount` | 결제 시작 전 주문 금액 조회 | Circuit Breaker + Retry |

---

## 2. 의존성 추가 (Dependencies)

Spring Boot 3.x에서 권장하는 `Resilience4j` 스타터 및 모니터링을 위한 Actuator, AOP 의존성을 추가합니다.

`build.gradle` (order-service, payment-service 공통 추가 사항):

```groovy
dependencies {
    // Resilience4j
    implementation 'org.springframework.cloud:spring-cloud-starter-circuitbreaker-resilience4j'
    implementation 'org.springframework.boot:spring-boot-starter-aop'
    
    // Actuator (Circuit Breaker 상태 모니터링 노출용)
    implementation 'org.springframework.boot:spring-boot-starter-actuator'
}
```

---

## 3. 환경 설정 (application-dev.properties)

Resilience4j의 Circuit Breaker와 Retry 속성을 설정합니다. 
`order-service`와 `payment-service`의 구성 파일에 맞게 아래 내용을 추가합니다.

```properties
# ──────────────────────────────────────────
# Actuator Configuration
# ──────────────────────────────────────────
management.endpoints.web.exposure.include=health,info,metrics,circuitbreakers
management.health.circuitbreakers.enabled=true

# ──────────────────────────────────────────
# Resilience4j Circuit Breaker
# ──────────────────────────────────────────
# 상태 기록을 위한 윈도우 크기 (최근 호출 10개 기준)
resilience4j.circuitbreaker.configs.default.sliding-window-size=10
# 실패율 임계치 (50% 이상 실패 시 OPEN)
resilience4j.circuitbreaker.configs.default.failure-rate-threshold=50
# 서킷이 OPEN 되고 나서 HALF_OPEN 으로 전환되기까지의 대기 시간
resilience4j.circuitbreaker.configs.default.wait-duration-in-open-state=30s
# 최소 측정 요청 수 (이만큼의 요청이 있어야 실패율 계산)
resilience4j.circuitbreaker.configs.default.minimum-number-of-calls=5
# HALF_OPEN 상태에서 허용할 요청 수
resilience4j.circuitbreaker.configs.default.permitted-number-of-calls-in-half-open-state=3

resilience4j.circuitbreaker.instances.catalogService.base-config=default
resilience4j.circuitbreaker.instances.orderService.base-config=default

# ──────────────────────────────────────────
# Resilience4j Retry
# ──────────────────────────────────────────
# 재시도 횟수 (최초 호출 포함 3회)
resilience4j.retry.configs.default.max-attempts=3
# 재시도 간 대기 시간 (1초)
resilience4j.retry.configs.default.wait-duration=1s

resilience4j.retry.instances.catalogService.base-config=default
resilience4j.retry.instances.orderService.base-config=default
```

---

## 4. 코드 구현 스펙

### 4.1 Order Service -> CatalogClient 적용

`jym-order-service`에서 `catalog-service`를 호출하는 Feign Client 혹은 RestClient 클래스에 애노테이션 기반 정책과 Fallback을 적용합니다.

```java
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
    public ProductInfo getProductInfo(Long productId) {
        return catalogRestClient.get()
                .uri("/api/v1/products/{id}", productId)
                .retrieve()
                .body(ProductInfo.class);
    }

    /**
     * Fallback 메서드.
     * 서킷 브레이커가 OPEN 상태이거나 재시도 후에도 최종 실패한 경우 실행됩니다.
     *
     * @param productId 요청했던 상품 ID
     * @param t         발생한 예외
     * @return ProductInfo 기본값 매핑 혹은 에러 반환
     */
    public ProductInfo getProductInfoFallback(Long productId, Throwable t) {
        log.warn("Catalog API 호출 실패. Circuit Breaker Fallback 실행: productId={}", productId, t);
        
        // 요구되는 비즈니스 스펙에 맞춰 익셉션 처리 (장바구니 로딩이나 결제 시 오류 메시지 일원화)
        throw new GlobalException(
                "상품 정보를 현재 확인할 수 없습니다. 잠시 후 시도해주세요.",
                "ERR_CATALOG_UNAVAILABLE",
                HttpStatus.SERVICE_UNAVAILABLE
        );
    }
}
```

### 4.2 Payment Service -> OrderClient 적용

`jym-payment-service`에서 결제 검증 시 금액 정보를 조회하기 위해 `order-service`를 호출하는 로직에 적용합니다.

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderClient {

    private final RestClient orderRestClient;

    @CircuitBreaker(name = "orderService", fallbackMethod = "getOrderAmountFallback")
    @Retry(name = "orderService")
    public BigDecimal getOrderAmount(Long orderId) {
        return orderRestClient.get()
                .uri("/api/v1/orders/{id}/amount", orderId)
                .retrieve()
                .body(BigDecimal.class);
    }

    /**
     * Fallback 로직
     */
    public BigDecimal getOrderAmountFallback(Long orderId, Throwable t) {
        log.warn("Order API 호출(결제 금액 검증) 실패. Fallback 실행: orderId={}", orderId, t);
        
        throw new GlobalException(
                "현재 주문 정보를 확인할 수 없어 결제를 진행할 수 없습니다. 잠시 후 시도해주세요.",
                "ERR_ORDER_API_UNAVAILABLE",
                HttpStatus.SERVICE_UNAVAILABLE
        );
    }
}
```

---

## 5. Fallback 전략에 대한 비즈니스 판단

Circuit Breaker OPEN 혹은 외부 호출 실패 시 처리 가능한 Fallback 전략 중 Jymusic 아키텍처에 맞게 선택합니다.

| 시나리오 | 적용된 전략 | 사유 |
|---|---|---|
| **상품 조회 실패** | Error 발생 중단 (`Throw Exception`) | 단가를 정확히 알 수 없는 상황에서 더미(Dummy) 값 또는 이전 캐시 데이터로 결제를 넘기면 치명적 매출 및 정산 이슈를 유발할 수 있으므로, 결제 시도를 멈춰야 함 |
| **주문 금액 조회 실패** | Error 발생 중단 (`Throw Exception`) | Toss Payments에 결제를 승인하고 비교할 Target 대상 금액이 없으면 보안 취약점(금액 변조)에 노출되므로 원천 차단해야 함 |

---

## 6. 테스트 체계

### 6.1 `WireMock` 을 활용한 Circuit Open 검증 시나리오

1. **상태 모킹**: `catalog-service` 에 대한 API 응답을 `Http Status 500` 으로 설정합니다.
2. **반복 요청**: `order-service` 에서 상품 조회 메서드를 10회 호출합니다.
3. **서킷 OPEN 검증**: 실패율이 임계치(50%)를 넘었으므로 Actuator(`/actuator/circuitbreakers`) 앤드포인트 확인 시 상태가 `OPEN` 임을 검증합니다.
4. **Fallback 검증**: 11번째 요청 시 `catalog-service` 호출을 아예 시도하지 않고 내부 `GlobalException(ERR_CATALOG_UNAVAILABLE)` 이 바로 발생하는지 응답 시간을 통해 확인합니다 (빠른 에러 리턴).
5. **HALF_OPEN 검증**: `wait-duration-in-open-state` (예: 30초) 시간 이후에 요청을 보내면 `HALF_OPEN` 으로 돌아서고 타겟 서버 복구 시 최종 `CLOSED` 상태로 안착하는지 확인합니다.

---

*이 문서는 Circuit Breaker 장애 조치 설계이며 개발 진행 시 해당 명세에 따라 `@CircuitBreaker`, `@Retry` 셋업과 모니터링 환경을 구성해야 합니다.*
