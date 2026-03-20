# 03_WEBFLUX_MIGRATION (API 게이트웨이 WebFlux 마이그레이션)

## 1. 목적

`jym-api-gateway`를 **Spring Cloud Gateway WebMvc (Servlet/Tomcat)** 기반에서
**Spring Cloud Gateway Reactive (WebFlux/Netty)** 기반으로 마이그레이션합니다.

리액티브 스택 전환으로 비차단(non-blocking) I/O 기반의 프록시 처리가 가능해지며,
게이트웨이 특성상 I/O 바운드 작업(다운스트림 프록시)이 대부분이므로 처리량(throughput) 개선을 기대합니다.

---

## 2. 마이그레이션 범위 요약

| 영역 | 현재 (WebMvc) | 변경 후 (WebFlux) |
| :--- | :--- | :--- |
| **런타임** | Tomcat (Servlet) | Netty (Reactive) |
| **Gateway 의존성** | `spring-cloud-starter-gateway-server-webmvc` | `spring-cloud-starter-gateway` |
| **Web 의존성** | `spring-boot-starter-webmvc` | `spring-boot-starter-webflux` |
| **라우팅** | `RouterFunction<ServerResponse>` (WebMvc.fn) | `RouteLocator` (Spring Cloud Gateway DSL) |
| **JWT 필터** | `OncePerRequestFilter` (Servlet Filter) | `GlobalFilter` (Reactive) |
| **요청/응답 객체** | `HttpServletRequest` / `HttpServletResponse` | `ServerWebExchange` (`ServerHttpRequest` / `ServerHttpResponse`) |
| **헤더 주입** | `HttpServletRequestWrapper` | `ServerHttpRequest.mutate().header(...)` |
| **Security** | `SecurityFilterChain` + `HttpSecurity` | `SecurityWebFilterChain` + `ServerHttpSecurity` |
| **CORS** | `CorsConfigurationSource` + `UrlBasedCorsConfigurationSource` | `CorsConfigurationSource` + `UrlBasedCorsConfigurationSource` (동일 인터페이스, 리액티브 컨텍스트) |
| **에러 처리** | `@RestControllerAdvice` + `ResponseEntity` | `AbstractErrorWebExceptionHandler` 또는 `GlobalFilter`에서 직접 처리 |
| **테스트** | `MockHttpServletRequest` / `MockMvc` | `WebTestClient` / `StepVerifier` |

---

## 3. 의존성 변경 (`build.gradle`)

### 3.1 제거 대상

```groovy
implementation 'org.springframework.boot:spring-boot-starter-webmvc'
implementation 'org.springframework.cloud:spring-cloud-starter-gateway-server-webmvc'
```

### 3.2 추가 대상

```groovy
implementation 'org.springframework.boot:spring-boot-starter-webflux'
implementation 'org.springframework.cloud:spring-cloud-starter-gateway'
```

### 3.3 변경 없는 의존성

```groovy
implementation 'org.springframework.boot:spring-boot-starter-security'
implementation 'io.jsonwebtoken:jjwt-api:0.12.6'
runtimeOnly 'io.jsonwebtoken:jjwt-impl:0.12.6'
runtimeOnly 'io.jsonwebtoken:jjwt-jackson:0.12.6'
compileOnly 'org.projectlombok:lombok'
annotationProcessor 'org.projectlombok:lombok'
developmentOnly 'org.springframework.boot:spring-boot-devtools'
testImplementation 'org.springframework.boot:spring-boot-starter-test'
testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
```

### 3.4 변경 대상

```groovy
// 제거
testImplementation 'org.springframework.boot:spring-boot-starter-security-test'

// 추가 (Reactive Security 테스트)
testImplementation 'org.springframework.security:spring-security-test'
```

---

## 4. 컴포넌트별 마이그레이션 상세

### 4.1 `GatewayRoutingConfig` → `GatewayRouteConfig`

**현재**: `RouterFunction<ServerResponse>` (WebMvc.fn) + `BeforeFilterFunctions.uri()` + `HandlerFunctions.http()`

**변경**: `RouteLocatorBuilder` 기반 `RouteLocator` Bean

```java
@Configuration
public class GatewayRouteConfig {

    @Value("${services.member-auth.url}")
    private String memberAuthUrl;

    @Value("${services.catalog.url}")
    private String catalogUrl;

    @Value("${services.order.url}")
    private String orderUrl;

    @Value("${services.payment.url}")
    private String paymentUrl;

    @Bean
    public RouteLocator gatewayRoutes(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("member_auth_service", r -> r
                        .path("/api/v1/auth/**", "/api/v1/members/**")
                        .uri(memberAuthUrl))
                .route("catalog_service", r -> r
                        .path("/api/v1/products/**", "/api/v1/categories/**", "/api/v1/media/**")
                        .uri(catalogUrl))
                .route("order_service", r -> r
                        .path("/api/v1/cart/**", "/api/v1/orders/**")
                        .uri(orderUrl))
                .route("payment_service", r -> r
                        .path("/api/v1/payments/**")
                        .uri(paymentUrl))
                .build();
    }
}
```

**핵심 변경점:**
- `org.springframework.web.servlet.function.*` → 사용하지 않음
- `org.springframework.cloud.gateway.server.mvc.*` → 사용하지 않음
- `RouteLocatorBuilder`의 `.path()` predicate + `.uri()` 조합으로 라우트 정의
- 개별 `@Bean RouterFunction`을 하나의 `RouteLocator`로 통합

---

### 4.2 `JwtVerificationFilter` → `JwtVerificationFilter` (GlobalFilter)

**현재**: `OncePerRequestFilter` + `HttpServletRequestWrapper`로 헤더 주입

**변경**: `GlobalFilter` + `Ordered` 인터페이스 구현

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtVerificationFilter implements GlobalFilter, Ordered {

    private final JwtValidator jwtValidator;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    private static final List<String> EXCLUDE_PATHS = List.of(
            "/api/v1/auth/**",
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/openapi.yaml"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        if (shouldSkip(path)) {
            return chain.filter(exchange);
        }

        String token = extractToken(exchange.getRequest());

        if (token == null) {
            return writeUnauthorizedResponse(exchange, "인증 토큰이 누락되었습니다.");
        }

        if (!jwtValidator.validateToken(token)) {
            return writeUnauthorizedResponse(exchange, "인증 정보가 유효하지 않거나 만료되었습니다.");
        }

        Claims claims = jwtValidator.getClaims(token);
        String userId   = String.valueOf(claims.get("userId"));
        String username = claims.getSubject();
        String role     = String.valueOf(claims.get("role"));

        log.debug("인증된 사용자 → userId: {}, username: {}, role: {}", userId, username, role);

        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .header("X-User-Id", userId)
                .header("X-User-Name", username)
                .header("X-User-Role", role)
                .build();

        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    @Override
    public int getOrder() {
        return -1; // Security 필터 이후, 라우팅 이전에 실행
    }

    private boolean shouldSkip(String path) {
        return EXCLUDE_PATHS.stream()
                .anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    private String extractToken(ServerHttpRequest request) {
        String bearerToken = request.getHeaders().getFirst("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

    private Mono<Void> writeUnauthorizedResponse(ServerWebExchange exchange, String message) {
        log.warn("JWT 검증 실패: {}", message);
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String body = String.format(
                "{\"status\":401,\"code\":\"ERR_UNAUTHORIZED\",\"message\":\"%s\",\"timestamp\":\"%s\"}",
                message, LocalDateTime.now()
        );
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }
}
```

**핵심 변경점:**
- `OncePerRequestFilter` → `GlobalFilter` + `Ordered`
- `doFilterInternal(request, response, chain)` → `filter(ServerWebExchange, GatewayFilterChain)`
- `HttpServletRequestWrapper` 제거 → `ServerHttpRequest.mutate().header(...)` 사용
- `filterChain.doFilter()` → `chain.filter()` (리턴 타입 `Mono<Void>`)
- 에러 응답 작성: `response.getWriter().write()` → `response.writeWith(Mono.just(DataBuffer))`
- 내부 클래스 `UserContextRequestWrapper` 제거 (불필요)

---

### 4.3 `SpringSecurityConfig` → `SecurityConfig` (Reactive Security)

**현재**: `SecurityFilterChain` + `HttpSecurity` (Servlet 기반)

**변경**: `SecurityWebFilterChain` + `ServerHttpSecurity` (Reactive 기반)

```java
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .authorizeExchange(auth -> auth.anyExchange().permitAll())
                .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("http://localhost:3000"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Authorization"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
```

**핵심 변경점:**
- `@EnableWebSecurity` → `@EnableWebFluxSecurity`
- `SecurityFilterChain` → `SecurityWebFilterChain`
- `HttpSecurity` → `ServerHttpSecurity`
- `.authorizeHttpRequests(...)` → `.authorizeExchange(...)`
- `.anyRequest().permitAll()` → `.anyExchange().permitAll()`
- `.sessionManagement(...)` → 제거 (WebFlux는 기본적으로 무상태)
- `CorsConfigurationSource` 인터페이스는 동일하게 사용 가능

---

### 4.4 `GlobalExceptionHandler` → `GatewayErrorWebExceptionHandler`

**현재**: `@RestControllerAdvice` + `@ExceptionHandler` (MVC 어노테이션 기반)

**변경**: `AbstractErrorWebExceptionHandler` 상속 (Reactive Error Handling)

> WebFlux 환경에서는 `@RestControllerAdvice`가 Gateway 프록시 레이어에서 발생하는 예외를 잡지 못합니다.
> 대신 `ErrorWebExceptionHandler`를 구현합니다.

```java
@Slf4j
@Component
@Order(-2) // DefaultErrorWebExceptionHandler(-1)보다 높은 우선순위
public class GatewayErrorWebExceptionHandler implements ErrorWebExceptionHandler {

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        ServerHttpResponse response = exchange.getResponse();
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        HttpStatus status;
        String code;
        String message;

        if (ex instanceof GlobalException ge) {
            status = ge.getHttpStatus();
            code = ge.getErrorCode();
            message = ge.getMessage();
            log.error("Business Exception: {} - {}", code, message);

            if ("RATE_LIMIT_EXCEEDED".equals(code)) {
                status = HttpStatus.TOO_MANY_REQUESTS;
            }
        } else if (ex instanceof AccessDeniedException || ex instanceof AuthorizationDeniedException) {
            status = HttpStatus.UNAUTHORIZED;
            code = "HTTP_UNAUTHORIZED_ERROR";
            message = "접근 권한이 없습니다.";
            log.error("권한 없는 접근 시도: {}", ex.getMessage());
        } else {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
            code = "INTERNAL_SERVER_ERROR";
            message = "시스템 오류가 발생했습니다";
            log.error("Unexpected Exception: ", ex);
        }

        response.setStatusCode(status);
        String body = String.format(
                "{\"code\":\"%s\",\"message\":\"%s\",\"status\":%d,\"timestamp\":\"%s\"}",
                code, message, status.value(), LocalDateTime.now()
        );
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }
}
```

**핵심 변경점:**
- `@RestControllerAdvice` 제거 → `ErrorWebExceptionHandler` 구현
- `ResponseEntity<Map<...>>` → `Mono<Void>` + `DataBuffer` 기반 직접 응답 쓰기
- `@Order(-2)` 로 기본 핸들러보다 우선 실행 보장

---

### 4.5 `JwtValidator` → 변경 없음

`JwtValidator`는 순수 Java 유틸리티 클래스(동기 로직)이므로 변경이 필요하지 않습니다.
`jjwt` 라이브러리 기반의 서명 검증 로직은 Servlet/Reactive에 무관하게 동작합니다.

---

### 4.6 `GlobalException` → 변경 없음

커스텀 예외 클래스는 웹 프레임워크에 의존하지 않으므로 그대로 유지합니다.

---

### 4.7 `JymApiGatewayApplication` → 변경 없음

`@SpringBootApplication` 메인 클래스는 변경 불필요. Spring Boot가 classpath의 `spring-boot-starter-webflux`를 감지하여 자동으로 Netty 기반으로 기동합니다.

---

## 5. 삭제 대상

| 대상 | 이유 |
| :--- | :--- |
| `UserContextRequestWrapper` (내부 클래스) | `ServerHttpRequest.mutate()`로 대체 |

---

## 6. 파일 변경 매핑

| 현재 파일 | 작업 | 비고 |
| :--- | :--- | :--- |
| `build.gradle` | 수정 | 의존성 교체 (§3) |
| `GatewayRoutingConfig.java` | **재작성** → `GatewayRouteConfig.java` | RouteLocator 기반 (§4.1) |
| `JwtVerificationFilter.java` | **재작성** | GlobalFilter 기반 (§4.2) |
| `SpringSecurityConfig.java` | **재작성** → `SecurityConfig.java` | Reactive Security (§4.3) |
| `GlobalExceptionHandler.java` | **재작성** → `GatewayErrorWebExceptionHandler.java` | ErrorWebExceptionHandler (§4.4) |
| `JwtValidator.java` | 변경 없음 | §4.5 |
| `GlobalException.java` | 변경 없음 | §4.6 |
| `JymApiGatewayApplication.java` | 변경 없음 | §4.7 |

---

## 7. Import 변경 요약

### 제거 (Servlet 관련)

```
jakarta.servlet.*
org.springframework.web.filter.OncePerRequestFilter
org.springframework.web.servlet.function.*
org.springframework.cloud.gateway.server.mvc.*
org.springframework.security.config.annotation.web.builders.HttpSecurity
org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer
org.springframework.security.config.http.SessionCreationPolicy
org.springframework.security.web.SecurityFilterChain
```

### 추가 (Reactive 관련)

```
org.springframework.cloud.gateway.filter.GlobalFilter
org.springframework.cloud.gateway.filter.GatewayFilterChain
org.springframework.cloud.gateway.route.RouteLocator
org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder
org.springframework.http.server.reactive.ServerHttpRequest
org.springframework.http.server.reactive.ServerHttpResponse
org.springframework.web.server.ServerWebExchange
org.springframework.core.io.buffer.DataBuffer
org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity
org.springframework.security.config.web.server.ServerHttpSecurity
org.springframework.security.web.server.SecurityWebFilterChain
org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler
reactor.core.publisher.Mono
```

---

## 8. 마이그레이션 체크리스트

- [ ] `build.gradle` 의존성 교체
- [ ] `GatewayRoutingConfig` → `GatewayRouteConfig` 재작성
- [ ] `JwtVerificationFilter` → `GlobalFilter` 재작성
- [ ] `SpringSecurityConfig` → `SecurityConfig` (Reactive) 재작성
- [ ] `GlobalExceptionHandler` → `GatewayErrorWebExceptionHandler` 재작성
- [ ] 테스트 코드 마이그레이션 (§ `02_TEST_SPEC` 참조)
- [ ] `application.properties` / `application.yml` 설정 확인 (포트, 서비스 URL 등)
- [ ] 빌드 및 기동 확인 (Netty 정상 기동)
- [ ] 전체 라우팅 동작 확인
- [ ] JWT 검증 동작 확인
- [ ] CORS Preflight 동작 확인
- [ ] 에러 응답 형식 확인

---

## 9. 주의 사항

1. **WebMvc와 WebFlux 공존 불가**: `spring-boot-starter-webmvc`와 `spring-boot-starter-webflux`가 동시에 존재하면 WebMvc가 우선됩니다. 반드시 `webmvc` 의존성을 완전히 제거해야 합니다.
2. **블로킹 호출 금지**: Netty 이벤트 루프에서 블로킹 호출(`Thread.sleep`, 동기 I/O 등)은 금지됩니다. `JwtValidator`의 공개키 로딩은 생성자에서 1회만 수행하므로 문제 없습니다.
3. **`@RestControllerAdvice` 비호환**: Spring Cloud Gateway(Reactive)는 프록시 체인에서 발생하는 예외를 MVC 예외 핸들러로 전달하지 않습니다. 반드시 `ErrorWebExceptionHandler`를 사용해야 합니다.
4. **필터 순서**: `GlobalFilter`의 `getOrder()` 반환값으로 실행 순서를 제어합니다. Security 필터 체인 이후에 실행되도록 적절한 값을 설정합니다.
5. **Reactor Netty 기본 포트**: 기본 포트는 8080으로 동일하며, `server.port`로 변경 가능합니다.
