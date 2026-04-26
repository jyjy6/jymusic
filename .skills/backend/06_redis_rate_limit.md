# Redis 기반 @RateLimit 어노테이션 구현

> **참조**: `.skills/_common/00_project_context.md`

---

## 개요

Redis를 활용한 API Rate Limiting 시스템을 커스텀 어노테이션 기반으로 구현하는 표준 절차입니다.
컨트롤러 메서드에 `@RateLimit` 어노테이션만 붙이면 자동으로 요청 제한이 적용됩니다.

**지원하는 Rate Limiting 알고리즘:**
- **Fixed Window** — 고정 시간 창 기반 카운터
- **Sliding Window** — Redis Sorted Set 기반 정밀 슬라이딩 윈도우
- **Token Bucket** — Lua 스크립트 기반 원자적 토큰 버킷

**식별자 타입:**
- `IP` — 클라이언트 IP 주소 (프록시 헤더 지원)
- `USER_ID` — Spring Security 인증 사용자 ID
- `IP_AND_USER_ID` — IP + 사용자 ID 조합

---

## 입력 (AI에게 제공할 정보)

- 대상 서비스명 (예: `jym-order-service`)
- Rate Limit 대상 API (예: `POST /api/v1/orders`)
- 시간 창 크기 (초 단위, 기본: 86400)
- 최대 요청 수 (기본: 10)
- Rate Limiting 알고리즘 (FIXED_WINDOW / SLIDING_WINDOW / TOKEN_BUCKET)
- 식별자 타입 (IP / USER_ID / IP_AND_USER_ID)
- 에러 메시지 (선택)
- Token Bucket 사용 시: capacity, refillRate

---

## 전제 조건

- **Redis** 서버 실행 중
- **Spring Boot** + `spring-boot-starter-data-redis` 의존성
- **RedisTemplate<String, Object>** Bean 등록 완료
- **GlobalException** 클래스 구현 완료 (에러 코드 기반 예외 처리)
- **Spring Security** 설정 완료 (USER_ID 식별자 사용 시)

---

## 절차

### Step 1: `@RateLimit` 커스텀 어노테이션 생성

`RateLimit/` 패키지에 생성합니다.

```java
package {base_package}.redis.ratelimit;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimit {

    /**
     * 시간 창 크기 (초 단위)
     */
    long windowSeconds() default 86400;

    /**
     * 최대 요청 수
     */
    int maxRequests() default 10;

    /**
     * Rate Limiting 방식
     */
    RateLimitType type() default RateLimitType.SLIDING_WINDOW;

    /**
     * 식별자 타입
     */
    IdentifierType identifierType() default IdentifierType.IP;

    /**
     * 사용자 정의 키 접두사
     */
    String keyPrefix() default "";

    /**
     * 에러 메시지
     */
    String message() default "너무 많은 요청입니다. 잠시 후 다시 시도해주세요.";

    /**
     * Token Bucket 설정 (type이 TOKEN_BUCKET일 때만 사용)
     */
    int capacity() default 10;

    /**
     * Token Bucket 리필 속도 (초당 토큰 수)
     */
    double refillRate() default 1.0;

    enum RateLimitType {
        FIXED_WINDOW,
        SLIDING_WINDOW,
        TOKEN_BUCKET
    }

    enum IdentifierType {
        IP,              // IP 주소
        USER_ID,         // 회원 ID
        IP_AND_USER_ID   // IP + 회원 ID 조합
    }
}
```

### Step 2: `RateLimitInterceptor` 구현

`HandlerInterceptor`를 구현하여 어노테이션 기반 Rate Limit 체크를 수행합니다.

```java
package {base_package}.redis.ratelimit;

import {base_package}.exception.GlobalException;
import {base_package}.redis.RedisService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
@Slf4j
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RedisService redisService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }

        // HandlerMethod: 실행될 컨트롤러 메서드에 대한 모든 정보가 담긴 객체
        HandlerMethod handlerMethod = (HandlerMethod) handler;
        RateLimit rateLimit = handlerMethod.getMethodAnnotation(RateLimit.class);

        if (rateLimit == null) {
            return true;
        }

        // 토큰 갱신 후 재시도 요청인 경우, Rate Limit 체크를 건너뜀 (중복 카운트 방지)
        String retryHeader = request.getHeader("X-Retry-Request");
        if ("true".equals(retryHeader)) {
            log.info("=== 재시도 요청 감지: Rate Limit 체크 건너뜀 ===");
            return true;
        }

        String identifier = getIdentifier(request, rateLimit.identifierType());
        String key = buildKey(rateLimit.keyPrefix(), identifier, handlerMethod);

        log.info("=== Rate Limit 체크 === Key: {}, Type: {}, Max: {}/{}s",
                key, rateLimit.type(), rateLimit.maxRequests(), rateLimit.windowSeconds());

        boolean allowed = checkRateLimit(key, rateLimit);

        // Rate Limit 응답 헤더 설정
        long currentCount = redisService.getCurrentRequestCount(
                key, rateLimit.windowSeconds(), rateLimit.type());
        response.setHeader("X-RateLimit-Limit", String.valueOf(rateLimit.maxRequests()));
        response.setHeader("X-RateLimit-Remaining",
                String.valueOf(Math.max(0, rateLimit.maxRequests() - currentCount)));
        response.setHeader("X-RateLimit-Reset",
                String.valueOf(System.currentTimeMillis() + rateLimit.windowSeconds() * 1000));

        if (!allowed) {
            log.warn("Rate limit exceeded for key: {}", key);
            throw new GlobalException(rateLimit.message(), "RATE_LIMIT_EXCEEDED");
        }

        return true;
    }

    /**
     * 식별자 추출 — IP / USER_ID / IP_AND_USER_ID
     */
    private String getIdentifier(HttpServletRequest request, RateLimit.IdentifierType identifierType) {
        String ip = getClientIp(request);
        String userId = getCurrentUserId();

        switch (identifierType) {
            case IP:
                return "ip:" + ip;
            case USER_ID:
                return userId != null ? "user:" + userId : "ip:" + ip;
            case IP_AND_USER_ID:
                return userId != null ? "user:" + userId + ":ip:" + ip : "ip:" + ip;
            default:
                return "ip:" + ip;
        }
    }

    /**
     * Spring Security에서 현재 인증된 사용자 ID 추출
     */
    private String getCurrentUserId() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated() &&
                    !"anonymousUser".equals(authentication.getPrincipal())) {
                if (authentication.getPrincipal() instanceof
                        org.springframework.security.core.userdetails.UserDetails) {
                    return ((org.springframework.security.core.userdetails.UserDetails)
                            authentication.getPrincipal()).getUsername();
                }
                if (authentication.getPrincipal() instanceof String) {
                    return (String) authentication.getPrincipal();
                }
            }
        } catch (Exception e) {
            log.debug("사용자 ID 조회 중 오류 발생: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 프록시 환경 대응 클라이언트 IP 추출
     */
    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        return request.getRemoteAddr();
    }

    /**
     * Redis 키 생성: {keyPrefix}:{ClassName}:{methodName}:{identifier}
     */
    private String buildKey(String keyPrefix, String identifier, HandlerMethod handlerMethod) {
        String methodName = handlerMethod.getMethod().getName();
        String className = handlerMethod.getBeanType().getSimpleName();

        if (keyPrefix.isEmpty()) {
            return String.format("%s:%s:%s", className, methodName, identifier);
        } else {
            return String.format("%s:%s:%s:%s", keyPrefix, className, methodName, identifier);
        }
    }

    /**
     * 알고리즘별 Rate Limit 체크 위임
     */
    private boolean checkRateLimit(String key, RateLimit rateLimit) {
        switch (rateLimit.type()) {
            case FIXED_WINDOW:
                return redisService.isAllowedFixedWindow(
                        key, rateLimit.windowSeconds(), rateLimit.maxRequests());
            case SLIDING_WINDOW:
                return redisService.isAllowedSlidingWindow(
                        key, rateLimit.windowSeconds(), rateLimit.maxRequests());
            case TOKEN_BUCKET:
                return redisService.isAllowedTokenBucket(
                        key, rateLimit.capacity(), rateLimit.refillRate());
            default:
                return redisService.isAllowedFixedWindow(
                        key, rateLimit.windowSeconds(), rateLimit.maxRequests());
        }
    }
}
```

### Step 3: `RedisService`에 Rate Limiting 메서드 추가

기존 `RedisService`에 아래 메서드들을 추가합니다.

```java
// === Rate Limiting ===

/**
 * Fixed Window Rate Limiting
 * 고정 시간 창 기반 카운터 방식 — 구현이 단순하고 메모리 효율적
 */
public boolean isAllowedFixedWindow(String key, long windowSizeInSeconds, int maxRequests) {
    long currentWindow = System.currentTimeMillis() / 1000 / windowSizeInSeconds;
    String windowKey = "rate_limit:fixed:" + key + ":" + currentWindow;

    Long currentCount = redisTemplate.opsForValue().increment(windowKey);

    if (currentCount == 1) {
        redisTemplate.expire(windowKey, windowSizeInSeconds, TimeUnit.SECONDS);
    }

    return currentCount <= maxRequests;
}

/**
 * Sliding Window Rate Limiting (정밀한 방식)
 * Redis Sorted Set 사용 — 경계 시점 burst 문제 해결
 */
public boolean isAllowedSlidingWindow(String key, long windowSizeInSeconds, int maxRequests) {
    long now = System.currentTimeMillis();
    long windowStart = now - (windowSizeInSeconds * 1000);
    String slidingKey = "rate_limit:sliding:" + key;

    // 현재 시간을 score로 하여 ZSet에 추가
    redisTemplate.opsForZSet().add(slidingKey, now, now);

    // 시간 창 밖의 오래된 요청들 제거
    redisTemplate.opsForZSet().removeRangeByScore(slidingKey, 0, windowStart);

    // 현재 시간 창 내의 요청 수 조회
    Long currentCount = redisTemplate.opsForZSet().count(slidingKey, windowStart, now);

    // TTL 설정
    redisTemplate.expire(slidingKey, windowSizeInSeconds, TimeUnit.SECONDS);

    return currentCount <= maxRequests;
}

/**
 * Token Bucket Rate Limiting
 * Lua 스크립트로 원자적 처리 — 버스트 트래픽 허용 + 점진적 리필
 */
public boolean isAllowedTokenBucket(String key, int capacity, double refillRate) {
    String bucketKey = "rate_limit:bucket:" + key;
    long now = System.currentTimeMillis();

    String script =
            "local bucket_key = KEYS[1]\n" +
            "local capacity = tonumber(ARGV[1])\n" +
            "local refill_rate = tonumber(ARGV[2])\n" +
            "local now = tonumber(ARGV[3])\n" +
            "local bucket = redis.call('hmget', bucket_key, 'tokens', 'last_refill')\n" +
            "local tokens = tonumber(bucket[1]) or capacity\n" +
            "local last_refill = tonumber(bucket[2]) or now\n" +
            "local time_passed = (now - last_refill) / 1000\n" +
            "tokens = math.min(capacity, tokens + (time_passed * refill_rate))\n" +
            "if tokens >= 1 then\n" +
            "    tokens = tokens - 1\n" +
            "    redis.call('hmset', bucket_key, 'tokens', tokens, 'last_refill', now)\n" +
            "    redis.call('expire', bucket_key, 3600)\n" +
            "    return 1\n" +
            "else\n" +
            "    redis.call('hmset', bucket_key, 'tokens', tokens, 'last_refill', now)\n" +
            "    redis.call('expire', bucket_key, 3600)\n" +
            "    return 0\n" +
            "end";

    Long result = redisTemplate.execute(
            (RedisCallback<Long>) connection ->
                    connection.eval(script.getBytes(), ReturnType.INTEGER, 1,
                            bucketKey.getBytes(),
                            String.valueOf(capacity).getBytes(),
                            String.valueOf(refillRate).getBytes(),
                            String.valueOf(now).getBytes())
    );

    return result != null && result == 1L;
}

/**
 * 현재 Rate Limit 상태 조회
 */
public long getCurrentRequestCount(String key, long windowSizeInSeconds,
                                   RateLimit.RateLimitType type) {
    switch (type) {
        case FIXED_WINDOW:
            long currentWindow = System.currentTimeMillis() / 1000 / windowSizeInSeconds;
            String fixedKey = "rate_limit:fixed:" + key + ":" + currentWindow;
            Object value = redisTemplate.opsForValue().get(fixedKey);
            if (value instanceof Number) return ((Number) value).longValue();
            if (value instanceof String) {
                try { return Long.parseLong((String) value); }
                catch (NumberFormatException e) { return 0L; }
            }
            return 0L;

        case SLIDING_WINDOW:
            long now = System.currentTimeMillis();
            long windowStart = now - (windowSizeInSeconds * 1000);
            String slidingKey = "rate_limit:sliding:" + key;
            Long slidingCount = redisTemplate.opsForZSet().count(slidingKey, windowStart, now);
            return slidingCount != null ? slidingCount : 0;

        case TOKEN_BUCKET:
            String bucketKey = "rate_limit:bucket:" + key;
            Object tokens = redisTemplate.opsForHash().get(bucketKey, "tokens");
            if (tokens instanceof Number) return ((Number) tokens).longValue();
            return 0L;

        default:
            return 0;
    }
}

/**
 * Rate Limit 초기화 (관리자용)
 */
public void resetRateLimit(String key) {
    Set<String> keys = redisTemplate.keys("rate_limit:*:" + key + "*");
    if (keys != null && !keys.isEmpty()) {
        redisTemplate.delete(keys);
    }
}
```

### Step 4: `WebMvcConfigurer`에 인터셉터 등록

`WebConfig` (또는 기존 MVC 설정 클래스)에 인터셉터를 등록합니다.

```java
package {base_package}.config;

import {base_package}.redis.ratelimit.RateLimitInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@RequiredArgsConstructor
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final RateLimitInterceptor rateLimitInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/error",
                        "/swagger-ui/**",
                        "/v3/api-docs/**",
                        "/swagger-resources/**",
                        "/webjars/**"
                );
    }
}
```

### Step 5: 컨트롤러 메서드에 `@RateLimit` 적용

어노테이션만 붙이면 Rate Limit이 자동 적용됩니다.

```java
// === 기본 사용 (Sliding Window, IP 기반, 하루 10회 제한) ===
@RateLimit
@PostMapping("/submit")
public ResponseEntity<?> submitForm(@RequestBody FormRequest request) { ... }

// === Sliding Window — 10초간 3회 제한 ===
@RateLimit(
        windowSeconds = 10,
        maxRequests = 3,
        identifierType = RateLimit.IdentifierType.IP,
        type = RateLimit.RateLimitType.SLIDING_WINDOW,
        message = "요청 제한: 10초간 3회까지 가능합니다."
)
@GetMapping("/search")
public ResponseEntity<?> search() { ... }

// === Token Bucket — 최대 5개 토큰, 초당 0.5개 리필 ===
@RateLimit(
        type = RateLimit.RateLimitType.TOKEN_BUCKET,
        capacity = 5,
        refillRate = 0.5,
        identifierType = RateLimit.IdentifierType.IP,
        message = "API 호출 한도를 초과했습니다."
)
@PostMapping("/api/ai/generate")
public ResponseEntity<?> generateAi() { ... }

// === Fixed Window — 사용자 ID 기반, 1시간 100회 ===
@RateLimit(
        windowSeconds = 3600,
        maxRequests = 100,
        type = RateLimit.RateLimitType.FIXED_WINDOW,
        identifierType = RateLimit.IdentifierType.USER_ID,
        keyPrefix = "premium_api"
)
@GetMapping("/premium/data")
public ResponseEntity<?> getPremiumData() { ... }
```

### Step 6: (선택) 관리자 Rate Limit 모니터링 API

Rate Limit 상태 조회, 초기화, 키 목록 등 관리 기능을 제공합니다.

```java
@RestController
@RequestMapping("/api/v1/admin/rate-limit")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class RateLimitController {

    private final RedisService redisService;

    // Rate Limit 상태 조회
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getRateLimitStatus(
            @RequestParam String key,
            @RequestParam long windowSeconds,
            @RequestParam(defaultValue = "FIXED_WINDOW") String type) {
        RateLimit.RateLimitType rateLimitType = RateLimit.RateLimitType.valueOf(type.toUpperCase());
        long currentCount = redisService.getCurrentRequestCount(key, windowSeconds, rateLimitType);

        Map<String, Object> status = Map.of(
                "key", key,
                "windowSeconds", windowSeconds,
                "currentCount", currentCount,
                "status", "active"
        );
        return ResponseEntity.ok(status);
    }

    // Rate Limit 초기화
    @PostMapping("/reset")
    public ResponseEntity<Map<String, Object>> resetRateLimit(@RequestParam String key) {
        redisService.deleteSpecificKey(key);
        return ResponseEntity.ok(Map.of(
                "message", "Rate limit 초기화 완료",
                "key", key,
                "status", "success"
        ));
    }

    // 전체 Rate Limit 키 조회
    @GetMapping("/keys")
    public ResponseEntity<Map<String, Object>> getAllRateLimitKeys() {
        Set<String> keys = redisService.getAllKeys();
        Set<String> rateLimitKeys = keys.stream()
                .filter(key -> key.startsWith("rate_limit:"))
                .collect(java.util.stream.Collectors.toSet());

        return ResponseEntity.ok(Map.of(
                "keys", rateLimitKeys,
                "totalCount", rateLimitKeys.size(),
                "status", "success"
        ));
    }
}
```

---

## Redis 키 구조

| 알고리즘 | Redis 키 패턴 | 데이터 타입 |
|---|---|---|
| Fixed Window | `rate_limit:fixed:{Class}:{method}:{identifier}:{windowId}` | String (카운터) |
| Sliding Window | `rate_limit:sliding:{Class}:{method}:{identifier}` | Sorted Set (timestamp) |
| Token Bucket | `rate_limit:bucket:{Class}:{method}:{identifier}` | Hash (`tokens`, `last_refill`) |

---

## 응답 헤더

Rate Limit이 적용된 모든 요청에 아래 헤더가 자동 추가됩니다:

| 헤더 | 설명 |
|---|---|
| `X-RateLimit-Limit` | 최대 허용 요청 수 |
| `X-RateLimit-Remaining` | 남은 요청 수 |
| `X-RateLimit-Reset` | 제한 리셋 시간 (Unix timestamp ms) |

---

## 알고리즘 선택 가이드

| 알고리즘 | 적합한 상황 | 장점 | 단점 |
|---|---|---|---|
| **Fixed Window** | 간단한 API 제한 | 구현 단순, 메모리 절약 | 경계 시점 burst 가능 |
| **Sliding Window** | 정밀한 트래픽 제어 | 경계 burst 방지 | 메모리 사용량 증가 |
| **Token Bucket** | 버스트 허용 + 장기 평균 제어 | 유연한 트래픽 허용 | 구현 복잡도 높음 |

---

## 체크리스트

- [ ] `spring-boot-starter-data-redis` 의존성 추가
- [ ] `RedisTemplate<String, Object>` Bean 설정 완료
- [ ] `@RateLimit` 어노테이션 생성
- [ ] `RateLimitInterceptor` 구현 및 `@Component` 등록
- [ ] `RedisService`에 Rate Limiting 메서드 3종 추가 (Fixed/Sliding/TokenBucket)
- [ ] `WebConfig`에 인터셉터 등록 (`addPathPatterns("/**")`)
- [ ] Swagger, error 등 제외 경로 설정
- [ ] `GlobalException`에 `RATE_LIMIT_EXCEEDED` 에러 코드 처리
- [ ] 프록시 환경 시 `X-Forwarded-For` / `X-Real-IP` 헤더 처리 확인
- [ ] (선택) 관리자용 Rate Limit 모니터링 API 추가
- [ ] (선택) 프론트엔드에서 `X-RateLimit-Remaining` 헤더 파싱하여 UX 반영

---

## 관련 스킬

- `_common/00_project_context.md` — 프로젝트 컨텍스트
- `backend/01_new_api_endpoint.md` — REST API 엔드포인트 추가
- `backend/03_unit_test.md` — 단위 테스트 작성
