# 03_WEBFLUX_MIGRATION (API Gateway WebFlux Migration)

## 1. Objective

Migrate `jym-api-gateway` from **Spring Cloud Gateway WebMvc (Servlet/Tomcat)** to
**Spring Cloud Gateway Reactive (WebFlux/Netty)**.

The reactive stack enables non-blocking I/O-based proxy processing. Since the gateway is predominantly
I/O-bound (downstream proxying), this migration is expected to improve throughput.

---

## 2. Migration Scope Summary

| Area | Current (WebMvc) | Target (WebFlux) |
| :--- | :--- | :--- |
| **Runtime** | Tomcat (Servlet) | Netty (Reactive) |
| **Gateway Dependency** | `spring-cloud-starter-gateway-server-webmvc` | `spring-cloud-starter-gateway` |
| **Web Dependency** | `spring-boot-starter-webmvc` | `spring-boot-starter-webflux` |
| **Routing** | `RouterFunction<ServerResponse>` (WebMvc.fn) | `RouteLocator` (Spring Cloud Gateway DSL) |
| **JWT Filter** | `OncePerRequestFilter` (Servlet Filter) | `GlobalFilter` (Reactive) |
| **Request/Response** | `HttpServletRequest` / `HttpServletResponse` | `ServerWebExchange` (`ServerHttpRequest` / `ServerHttpResponse`) |
| **Header Injection** | `HttpServletRequestWrapper` | `ServerHttpRequest.mutate().header(...)` |
| **Security** | `SecurityFilterChain` + `HttpSecurity` | `SecurityWebFilterChain` + `ServerHttpSecurity` |
| **CORS** | `CorsConfigurationSource` + `UrlBasedCorsConfigurationSource` | `CorsConfigurationSource` + `UrlBasedCorsConfigurationSource` (same interface, reactive context) |
| **Error Handling** | `@RestControllerAdvice` + `ResponseEntity` | `ErrorWebExceptionHandler` or direct handling in `GlobalFilter` |
| **Testing** | `MockHttpServletRequest` / `MockMvc` | `WebTestClient` / `StepVerifier` |

---

## 3. Dependency Changes (`build.gradle`)

### 3.1 Remove

```groovy
implementation 'org.springframework.boot:spring-boot-starter-webmvc'
implementation 'org.springframework.cloud:spring-cloud-starter-gateway-server-webmvc'
```

### 3.2 Add

```groovy
implementation 'org.springframework.boot:spring-boot-starter-webflux'
implementation 'org.springframework.cloud:spring-cloud-starter-gateway'
```

### 3.3 Unchanged

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

### 3.4 Modified

```groovy
// Remove
testImplementation 'org.springframework.boot:spring-boot-starter-security-test'

// Add (Reactive Security Testing)
testImplementation 'org.springframework.security:spring-security-test'
```

---

## 4. Per-Component Migration Details

### 4.1 `GatewayRoutingConfig` → `GatewayRouteConfig`

**Current**: `RouterFunction<ServerResponse>` (WebMvc.fn) + `BeforeFilterFunctions.uri()` + `HandlerFunctions.http()`

**Target**: `RouteLocatorBuilder`-based `RouteLocator` Bean

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

**Key Changes:**
- `org.springframework.web.servlet.function.*` → removed
- `org.springframework.cloud.gateway.server.mvc.*` → removed
- Routes defined via `RouteLocatorBuilder` `.path()` predicate + `.uri()`
- Multiple `@Bean RouterFunction` methods consolidated into a single `RouteLocator`

---

### 4.2 `JwtVerificationFilter` → `JwtVerificationFilter` (GlobalFilter)

**Current**: `OncePerRequestFilter` + `HttpServletRequestWrapper` for header injection

**Target**: `GlobalFilter` + `Ordered` interface

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

        log.debug("Authenticated → userId: {}, username: {}, role: {}", userId, username, role);

        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .header("X-User-Id", userId)
                .header("X-User-Name", username)
                .header("X-User-Role", role)
                .build();

        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    @Override
    public int getOrder() {
        return -1; // Execute after Security filters, before routing
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
        log.warn("JWT verification failed: {}", message);
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

**Key Changes:**
- `OncePerRequestFilter` → `GlobalFilter` + `Ordered`
- `doFilterInternal(request, response, chain)` → `filter(ServerWebExchange, GatewayFilterChain)`
- `HttpServletRequestWrapper` removed → `ServerHttpRequest.mutate().header(...)` used
- `filterChain.doFilter()` → `chain.filter()` (returns `Mono<Void>`)
- Error response: `response.getWriter().write()` → `response.writeWith(Mono.just(DataBuffer))`
- Inner class `UserContextRequestWrapper` removed (unnecessary)

---

### 4.3 `SpringSecurityConfig` → `SecurityConfig` (Reactive Security)

**Current**: `SecurityFilterChain` + `HttpSecurity` (Servlet-based)

**Target**: `SecurityWebFilterChain` + `ServerHttpSecurity` (Reactive-based)

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

**Key Changes:**
- `@EnableWebSecurity` → `@EnableWebFluxSecurity`
- `SecurityFilterChain` → `SecurityWebFilterChain`
- `HttpSecurity` → `ServerHttpSecurity`
- `.authorizeHttpRequests(...)` → `.authorizeExchange(...)`
- `.anyRequest().permitAll()` → `.anyExchange().permitAll()`
- `.sessionManagement(...)` → removed (WebFlux is stateless by default)
- `CorsConfigurationSource` interface remains the same

---

### 4.4 `GlobalExceptionHandler` → `GatewayErrorWebExceptionHandler`

**Current**: `@RestControllerAdvice` + `@ExceptionHandler` (MVC annotation-based)

**Target**: `ErrorWebExceptionHandler` implementation (Reactive Error Handling)

> In WebFlux, `@RestControllerAdvice` cannot intercept exceptions from the Gateway proxy layer.
> Use `ErrorWebExceptionHandler` instead.

```java
@Slf4j
@Component
@Order(-2) // Higher priority than DefaultErrorWebExceptionHandler(-1)
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
            log.error("Unauthorized access attempt: {}", ex.getMessage());
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

**Key Changes:**
- `@RestControllerAdvice` removed → `ErrorWebExceptionHandler` implemented
- `ResponseEntity<Map<...>>` → `Mono<Void>` + `DataBuffer`-based direct response writing
- `@Order(-2)` ensures execution before default handler

---

### 4.5 `JwtValidator` → No Change

Pure Java utility class with synchronous logic. The `jjwt` library-based signature
verification works identically in both Servlet and Reactive environments.

---

### 4.6 `GlobalException` → No Change

Custom exception class has no dependency on the web framework.

---

### 4.7 `JymApiGatewayApplication` → No Change

Spring Boot auto-detects `spring-boot-starter-webflux` on the classpath and
bootstraps with Netty automatically.

---

## 5. Deletion Targets

| Target | Reason |
| :--- | :--- |
| `UserContextRequestWrapper` (inner class) | Replaced by `ServerHttpRequest.mutate()` |

---

## 6. File Change Mapping

| Current File | Action | Notes |
| :--- | :--- | :--- |
| `build.gradle` | Modify | Swap dependencies (§3) |
| `GatewayRoutingConfig.java` | **Rewrite** → `GatewayRouteConfig.java` | RouteLocator-based (§4.1) |
| `JwtVerificationFilter.java` | **Rewrite** | GlobalFilter-based (§4.2) |
| `SpringSecurityConfig.java` | **Rewrite** → `SecurityConfig.java` | Reactive Security (§4.3) |
| `GlobalExceptionHandler.java` | **Rewrite** → `GatewayErrorWebExceptionHandler.java` | ErrorWebExceptionHandler (§4.4) |
| `JwtValidator.java` | No change | §4.5 |
| `GlobalException.java` | No change | §4.6 |
| `JymApiGatewayApplication.java` | No change | §4.7 |

---

## 7. Import Changes Summary

### Remove (Servlet-related)

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

### Add (Reactive-related)

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

## 8. Migration Checklist

- [ ] Swap `build.gradle` dependencies
- [ ] Rewrite `GatewayRoutingConfig` → `GatewayRouteConfig`
- [ ] Rewrite `JwtVerificationFilter` → `GlobalFilter`
- [ ] Rewrite `SpringSecurityConfig` → `SecurityConfig` (Reactive)
- [ ] Rewrite `GlobalExceptionHandler` → `GatewayErrorWebExceptionHandler`
- [ ] Migrate test code (see `02_TEST_SPEC`)
- [ ] Verify `application.properties` / `application.yml` configuration (port, service URLs, etc.)
- [ ] Confirm Netty startup
- [ ] Verify all routing paths
- [ ] Verify JWT verification flow
- [ ] Verify CORS preflight behavior
- [ ] Verify error response format

---

## 9. Important Notes

1. **WebMvc and WebFlux cannot coexist**: If both `spring-boot-starter-webmvc` and `spring-boot-starter-webflux` are present, WebMvc takes priority. The `webmvc` dependency MUST be completely removed.
2. **No blocking calls**: Blocking calls (`Thread.sleep`, synchronous I/O, etc.) on the Netty event loop are forbidden. `JwtValidator`'s public key loading occurs once in the constructor, so it is safe.
3. **`@RestControllerAdvice` incompatible**: Spring Cloud Gateway (Reactive) does not route proxy-chain exceptions to MVC exception handlers. Use `ErrorWebExceptionHandler` instead.
4. **Filter ordering**: `GlobalFilter.getOrder()` return value controls execution order. Set an appropriate value to run after the Security filter chain but before routing.
5. **Reactor Netty default port**: Default port remains 8080, configurable via `server.port`.
