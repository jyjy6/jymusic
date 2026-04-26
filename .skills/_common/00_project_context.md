# 프로젝트 컨텍스트 & 아키텍처 헌법

> **이 스킬은 모든 다른 스킬의 기반 컨텍스트입니다.**
> 코드를 생성하거나 수정할 때 항상 이 문서의 원칙과 패턴을 준수하세요.

---

## 1. 프로젝트 개요

**Jymusic** — 음악 앨범 판매 이커머스 플랫폼 (MSA 기반)

### 기술 스택

| 구분 | 기술 |
|------|------|
| **프론트엔드** | Nuxt 3, 4 (Vue 3, TypeScript, Tailwind CSS) |
| **백엔드** | Spring Boot 3.x / 4.x (Java 21), Spring Cloud |
| **API 게이트웨이** | Spring Cloud Gateway (WebFlux / Reactor Netty) |
| **데이터베이스** | MySQL (서비스별 독립 DB) |
| **메시징** | Apache Kafka (Choreography Saga, DLT, EventEnvelope) |
| **캐시/세션** | Redis |
| **인증** | JWT (Stateless) + OAuth2 (Google, Kakao) |
| **복원력** | Resilience4j (Circuit Breaker, Retry) |
| **알림** | SSE (Server-Sent Events) |
| **인프라** | Docker / Docker Compose |
| **빌드** | Gradle (Groovy DSL) |
| **테스트** | JUnit 5 + Mockito (커버리지 70%+) |

---

## 2. 서비스 구조 & 포트 매핑

```
jymusic/
├── jym-front/               # Nuxt 프론트엔드           → localhost:3000
├── jym-api-gateway/          # API Gateway (단일 진입점) → localhost:8080
├── jym-member-auth-service/  # 회원/인증/JWT/OAuth2      → localhost:8081
├── jym-catalog-service/      # 상품 카탈로그/재고         → localhost:8082
├── jym-order-service/        # 주문/장바구니/알림         → localhost:8083
├── jym-payment-service/      # 결제 처리                 → localhost:8084
├── sdd-spec-docs/            # OpenAPI Spec 문서 (SDD)
└── docker/                   # Docker 환경 설정
```

### 서비스별 역할

| 서비스 | 역할 | DB |
|--------|------|----|
| `jym-api-gateway` | 모든 요청의 단일 진입점. JWT 검증 → 헤더 주입(X-User-Id, X-User-Role) → 라우팅 | — |
| `jym-member-auth-service` | 회원 가입, 로그인, JWT 발급/갱신, OAuth2 | jym_member_db |
| `jym-catalog-service` | 상품(앨범) CRUD, 카테고리, 재고, S3 미디어, MyBatis 검색 | jym_catalog_db |
| `jym-order-service` | 주문 생성/조회, 장바구니, 관리자 주문관리, SSE 알림 | jym_order_db |
| `jym-payment-service` | Toss 결제 연동, 결제 승인/취소 | jym_payment_db |

---

## 3. 백엔드 패키지 구조 (표준)

각 서비스는 아래 패키지 구조를 따릅니다:

```
src/main/java/jymusic/jym_{서비스명}/
├── config/              # @Configuration 클래스 (Security, Kafka, JPA, QueryDSL 등)
├── controller/          # @RestController (API 엔드포인트)
│   └── admin/           # 관리자 전용 컨트롤러 (있는 경우)
├── service/             # @Service 비즈니스 로직
│   └── admin/           # 관리자 전용 서비스
├── domain/
│   ├── entity/          # JPA @Entity, @Embeddable, Enum
│   ├── repository/      # JpaRepository, Custom(QueryDSL) 인터페이스 & 구현체
│   ├── common/          # BaseTimeEntity 등 공통 엔티티
│   └── event/           # 도메인 이벤트 (Spring ApplicationEvent)
├── dto/
│   ├── request/         # 요청 DTO (@Valid 검증)
│   └── response/        # 응답 DTO (Builder 패턴 + static from() 팩토리)
├── event/
│   ├── common/          # EventEnvelope, EventTypes, KafkaTopics
│   ├── consumer/        # @KafkaListener 소비자
│   ├── payload/         # 이벤트 페이로드 DTO
│   └── publisher/       # EventPublisher (KafkaTemplate 래퍼)
├── filter/              # GatewayAuthenticationFilter (X-User-Id/Role 파싱)
├── common/
│   └── GlobalErrorHandler/  # GlobalException + GlobalExceptionHandler
├── client/              # 타 서비스 REST 호출 (RestClient + @CircuitBreaker)
├── mapper/              # MyBatis Mapper 인터페이스 (있는 경우)
├── listener/            # Spring @EventListener (도메인 이벤트 수신)
├── notification/        # SSE 알림 관련 (있는 경우)
└── scheduler/           # @Scheduled 스케줄러 (있는 경우)
```

---

## 4. 핵심 코드 패턴

### 4.1 에러 처리 — `GlobalException` + `GlobalExceptionHandler`

모든 비즈니스 예외는 `GlobalException`을 사용합니다:

```java
// 발생
throw new GlobalException("주문을 찾을 수 없습니다.", "ERR_ORDER_NOT_FOUND", HttpStatus.NOT_FOUND);

// HttpStatus 생략 시 기본값 BAD_REQUEST
throw new GlobalException("유효하지 않은 요청입니다.", "ERR_INVALID_REQUEST");
```

`GlobalExceptionHandler`는 일관된 에러 응답 포맷을 제공합니다:
```json
{
  "code": "ERR_ORDER_NOT_FOUND",
  "message": "주문을 찾을 수 없습니다.",
  "status": 404,
  "timestamp": "2026-04-24T17:00:00"
}
```

### 4.2 인증 필터 — `GatewayAuthenticationFilter`

Gateway가 JWT 검증 후 주입한 헤더를 파싱하여 SecurityContext에 등록:

```java
@Component
public class GatewayAuthenticationFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String userId = request.getHeader("X-User-Id");
        String userRole = request.getHeader("X-User-Role");
        if (userId != null && userRole != null) {
            var authorities = AuthorityUtils.createAuthorityList(userRole);
            var authentication = new UsernamePasswordAuthenticationToken(userId, null, authorities);
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
        filterChain.doFilter(request, response);
    }
}
```

컨트롤러에서 사용자 ID 접근: `@AuthenticationPrincipal String memberId`

### 4.3 DTO 패턴

**Request DTO**: `@Getter` + `@NoArgsConstructor` + Bean Validation
```java
@Getter
@NoArgsConstructor
public class OrderCreateRequest {
    @NotEmpty(message = "주문 상품은 최소 1개 이상이어야 합니다.")
    @Valid
    private List<OrderItemRequest> items;
}
```

**Response DTO**: `@Getter` + `@Builder` + `static from()` 팩토리
```java
@Getter
@Builder
public class OrderResponse {
    private Long id;
    private BigDecimal totalAmount;
    private String status;
    private LocalDateTime createdAt;

    public static OrderResponse from(Order order) {
        return OrderResponse.builder()
                .id(order.getId())
                .totalAmount(order.getTotalAmount())
                .status(order.getStatus().name())
                .createdAt(order.getCreatedAt())
                .build();
    }
}
```

### 4.4 엔티티 패턴

```java
@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class Order extends BaseTimeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    // ...
}
```

**BaseTimeEntity**: `@MappedSuperclass` + `@EntityListeners(AuditingEntityListener.class)` + `createdAt`, `updatedAt`

### 4.5 Kafka 이벤트 패턴

**EventEnvelope** — 모든 Kafka 메시지의 공통 래퍼:
```java
@Getter @Builder @NoArgsConstructor @AllArgsConstructor
public class EventEnvelope<T> {
    private String eventId;        // UUID (멱등성 체크용)
    private String eventType;      // "ORDER_CREATED" 등
    private int version;           // 스키마 버전
    private LocalDateTime timestamp;
    private String source;         // "jym-order-service" 등
    private T payload;
}
```

**EventPublisher** — 발행 유틸리티:
```java
eventPublisher.publish(KafkaTopics.ORDER_EVENTS, orderId.toString(), EventTypes.ORDER_CREATED, payload);
```

### 4.6 SecurityConfig

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    private final GatewayAuthenticationFilter gatewayAuthFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/openapi.yaml", "/actuator/health").permitAll()
                .anyRequest().authenticated()
            )
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .addFilterBefore(gatewayAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
```

---

## 5. 프론트엔드 규칙 (Nuxt 3, 4)

### 필수 규칙
- 반드시 `<script setup lang="ts">` 사용
- API 호출은 반드시 **Axios** 사용 (`useNuxtApp().$axios`)
- 상태 관리는 **Pinia** (`defineStore`, Composition API 스타일)
- 스타일링은 반드시 **Tailwind CSS** 유틸리티 클래스만 사용 (별도 `<style>` 블록 금지)

### 프론트엔드 구조
```
app/
├── pages/          # 파일 기반 라우팅 ([id].vue 동적 라우트)
├── components/     # 도메인별 재사용 컴포넌트 (자동 import)
├── composables/    # API 연동 훅 (use*.ts)
├── stores/         # Pinia 스토어
├── types/          # TypeScript 타입 정의
├── plugins/        # Nuxt 플러그인 (01.axios.ts, 02.auth.client.ts 등)
├── middleware/     # 라우트 미들웨어 (auth.ts, admin.ts)
├── layouts/        # 레이아웃 (default.vue, admin.vue)
├── assets/css/     # Tailwind 메인 CSS
└── app.vue         # 앱 루트
```

### Composable 패턴
```typescript
export const useXxx = () => {
  const { $axios } = useNuxtApp();
  const data = useState<T[]>('xxx-data', () => []);
  const isLoading = useState<boolean>('xxx-loading', () => false);
  const errorMessage = useState<string>('xxx-error', () => '');

  const fetchData = async () => {
    isLoading.value = true;
    errorMessage.value = '';
    try {
      const response = await ($axios as AxiosInstance).get<T[]>('/api/v1/xxx');
      data.value = response.data;
    } catch (err: unknown) {
      const error = err as { response?: { data?: { message?: string } } };
      errorMessage.value = error.response?.data?.message ?? '데이터를 불러오지 못했습니다.';
    } finally {
      isLoading.value = false;
    }
  };

  return { data, isLoading, errorMessage, fetchData };
};
```

---

## 6. 아키텍처 원칙 (헌법)

1. **Database-per-service**: 각 서비스는 자신의 DB만 접근. 직접 크로스 DB 접근 금지
2. **Spec-Driven Development (SDD)**: 모든 API 변경은 OpenAPI Spec (OAS 3.0/3.1) 작성에서 시작
3. **Stateless**: 서버에 세션 저장 없음. JWT 기반 인가
4. **Lombok 필수**: 보일러플레이트 코드 제거
5. **DTO 분리 필수**: 엔티티 직접 노출 금지
6. **Builder 패턴 선호**: DTO와 엔티티 생성 시
7. **`@Transactional(readOnly = true)` 기본**: Service 클래스 레벨에 선언, 변경 메서드에만 `@Transactional` 오버라이드
8. **단위 테스트 커버리지 70% 이상** 유지

---

## 7. Kafka 토픽 & 이벤트 타입

### 토픽
| 상수 | 토픽명 |
|------|--------|
| `ORDER_EVENTS` | `jym.order.events` |
| `PAYMENT_EVENTS` | `jym.payment.events` |
| `STOCK_EVENTS` | `jym.stock.events` |
| `NOTIFICATION_EVENTS` | `jym.notification.events` |

### 이벤트 타입
| 타입 | 발행 서비스 | 설명 |
|------|-------------|------|
| `ORDER_CREATED` | order-service | 주문 생성됨 |
| `ORDER_CANCELLED` | order-service | 주문 취소됨 |
| `PAYMENT_COMPLETED` | payment-service | 결제 완료 |
| `PAYMENT_FAILED` | payment-service | 결제 실패 |
| `PAYMENT_CANCELLED` | payment-service | 결제 취소 |
| `STOCK_RESERVED` | catalog-service | 재고 예약 완료 |
| `STOCK_RESERVATION_FAILED` | catalog-service | 재고 예약 실패 |
| `STOCK_RELEASED` | catalog-service | 재고 복구 |
| `NOTI_ORDER_STATUS_CHANGED` | order-service | 주문 상태 변경 알림 |
| `NOTI_ADMIN_ORDER_CREATED` | order-service | 관리자 신규 주문 알림 |

---

## 8. build.gradle 표준 의존성

```groovy
plugins {
    id 'java'
    id 'org.springframework.boot' version '4.0.3'
    id 'io.spring.dependency-management' version '1.1.7'
}

java {
    toolchain { languageVersion = JavaLanguageVersion.of(21) }
}

dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-actuator'
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'org.springframework.boot:spring-boot-starter-security'
    implementation 'org.springframework.boot:spring-boot-starter-webmvc'
    implementation 'org.springframework.boot:spring-boot-starter-validation'
    implementation 'org.springframework.boot:spring-boot-starter-kafka'

    // QueryDSL
    implementation 'com.querydsl:querydsl-jpa:5.1.0:jakarta'
    annotationProcessor 'com.querydsl:querydsl-apt:5.1.0:jakarta'
    annotationProcessor 'jakarta.annotation:jakarta.annotation-api'
    annotationProcessor 'jakarta.persistence:jakarta.persistence-api'

    // Resilience4j
    implementation 'io.github.resilience4j:resilience4j-spring-boot3:2.3.0'
    implementation 'org.aspectj:aspectjweaver'

    // Swagger
    implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.5'

    compileOnly 'org.projectlombok:lombok'
    annotationProcessor 'org.projectlombok:lombok'
    developmentOnly 'org.springframework.boot:spring-boot-devtools'
    runtimeOnly 'com.mysql:mysql-connector-j'

    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testImplementation 'org.springframework.kafka:spring-kafka-test'
    testImplementation 'org.springframework.security:spring-security-test'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}
```
