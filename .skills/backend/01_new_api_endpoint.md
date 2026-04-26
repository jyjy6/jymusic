# 새 REST API 엔드포인트 추가

> **참조**: `.skills/_common/00_project_context.md`

---

## 개요

기존 마이크로서비스에 새로운 REST API 엔드포인트를 추가하는 표준 절차입니다.
Controller → Service → Repository 계층 구조를 따릅니다.

---

## 입력 (AI에게 제공할 정보)

- 대상 서비스명 (예: `jym-order-service`)
- API 경로 (예: `GET /api/v1/orders/{orderId}/items`)
- 기능 설명
- 인증 필요 여부 (기본: 필요)
- 관리자 전용 여부

---

## 절차

### Step 1: Request DTO 생성 (POST/PUT인 경우)

`dto/request/` 패키지에 생성합니다.

```java
package jymusic.jym_{서비스명}.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class XxxCreateRequest {

    @NotBlank(message = "제목은 필수입니다.")
    private String title;

    @NotNull(message = "가격은 필수입니다.")
    @Positive(message = "가격은 0보다 커야 합니다.")
    private BigDecimal price;
}
```

### Step 2: Response DTO 생성

`dto/response/` 패키지에 생성합니다.

```java
package jymusic.jym_{서비스명}.dto.response;

import jymusic.jym_{서비스명}.domain.entity.XxxEntity;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class XxxResponse {
    private Long id;
    private String title;
    private BigDecimal price;
    private LocalDateTime createdAt;

    // 정적 팩토리 메서드 (엔티티 → DTO 변환)
    public static XxxResponse from(XxxEntity entity) {
        return XxxResponse.builder()
                .id(entity.getId())
                .title(entity.getTitle())
                .price(entity.getPrice())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
```

### Step 3: Service 로직 작성

`service/` 패키지에 구현합니다.

```java
package jymusic.jym_{서비스명}.service;

import jymusic.jym_{서비스명}.common.GlobalErrorHandler.GlobalException;
import jymusic.jym_{서비스명}.domain.repository.XxxRepository;
import jymusic.jym_{서비스명}.dto.request.XxxCreateRequest;
import jymusic.jym_{서비스명}.dto.response.XxxResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)  // ← 클래스 레벨 기본값
public class XxxService {

    private final XxxRepository xxxRepository;

    // 조회 — readOnly 트랜잭션 사용
    public XxxResponse getXxx(Long id) {
        XxxEntity entity = xxxRepository.findById(id)
                .orElseThrow(() -> new GlobalException(
                        "XXX을 찾을 수 없습니다.",
                        "ERR_XXX_NOT_FOUND",
                        HttpStatus.NOT_FOUND
                ));
        return XxxResponse.from(entity);
    }

    // 변경 — @Transactional 오버라이드
    @Transactional
    public XxxResponse createXxx(Long memberId, XxxCreateRequest request) {
        XxxEntity entity = XxxEntity.builder()
                .memberId(memberId)
                .title(request.getTitle())
                .price(request.getPrice())
                .build();
        return XxxResponse.from(xxxRepository.save(entity));
    }
}
```

### Step 4: Controller 작성

`controller/` 패키지에 생성합니다.

```java
package jymusic.jym_{서비스명}.controller;

import jakarta.validation.Valid;
import jymusic.jym_{서비스명}.dto.request.XxxCreateRequest;
import jymusic.jym_{서비스명}.dto.response.XxxResponse;
import jymusic.jym_{서비스명}.service.XxxService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/xxx")
@RequiredArgsConstructor
public class XxxController {

    private final XxxService xxxService;

    @PostMapping
    public ResponseEntity<XxxResponse> create(
            @AuthenticationPrincipal String memberId,    // Gateway가 주입한 X-User-Id
            @Valid @RequestBody XxxCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(xxxService.createXxx(Long.parseLong(memberId), request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<XxxResponse> getOne(@PathVariable Long id) {
        return ResponseEntity.ok(xxxService.getXxx(id));
    }

    @GetMapping
    public ResponseEntity<List<XxxResponse>> getAll(
            @AuthenticationPrincipal String memberId) {
        return ResponseEntity.ok(xxxService.getAllByMember(Long.parseLong(memberId)));
    }
}
```

**관리자 전용 API인 경우**:
```java
@RestController
@RequestMapping("/api/v1/admin/xxx")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ADMIN')")  // ← 관리자 권한 체크
public class AdminXxxController { ... }
```

### Step 5: SecurityConfig 경로 허용 (필요 시)

공개 API가 필요한 경우 SecurityConfig에 추가합니다:

```java
.authorizeHttpRequests(auth -> auth
    .requestMatchers("/api/v1/public/**").permitAll()   // ← 공개 경로 추가
    .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
    .anyRequest().authenticated()
)
```

### Step 6: API Gateway 라우팅 확인

`jym-api-gateway`의 라우팅 설정에서 새 경로가 올바른 서비스로 라우팅되는지 확인합니다.
대부분의 경우 `/api/v1/xxx/**` 경로는 이미 서비스별 prefix로 라우팅되어 있으므로 추가 설정이 불필요합니다.

---

## 체크리스트

- [ ] Request DTO에 `@NotBlank`, `@NotNull`, `@Positive` 등 Bean Validation 추가
- [ ] Response DTO에 `@Builder` + `static from()` 팩토리 메서드 포함
- [ ] Service에 `@Transactional(readOnly = true)` 클래스 레벨 기본, 변경 메서드에만 `@Transactional` 
- [ ] 비즈니스 예외는 `GlobalException` 사용
- [ ] Controller에서 `@AuthenticationPrincipal String memberId`로 사용자 식별
- [ ] `@Valid` 어노테이션이 Request Body에 적용되었는가
- [ ] Gateway 라우팅 확인
- [ ] openapi.yaml 스펙 업데이트 (SDD 원칙)

---

## 관련 스킬

- `_common/00_project_context.md` — 프로젝트 컨텍스트
- `_common/01_sdd_workflow.md` — SDD 워크플로우
- `backend/03_unit_test.md` — 단위 테스트 작성
- `backend/05_jpa_entity_repository.md` — JPA 엔티티/리포지토리 설계
