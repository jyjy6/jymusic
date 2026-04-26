# SDD (Spec-Driven Development) 워크플로우

> **참조**: `.skills/_common/00_project_context.md`

---

## 개요

이 스킬은 새로운 기능을 개발할 때 **스펙 문서 작성 → 비즈니스 코드 → 테스트 코드** 순서로 진행하는 SDD 워크플로우를 안내합니다.

---

## 전제조건

- 프로젝트 아키텍처 헌법 이해 (`_common/00_project_context.md`)
- OpenAPI Specification (OAS 3.0/3.1) 기본 지식

---

## 워크플로우

```
① 스펙 작성 (OAS openapi.yaml + 설계 문서 .md)
        ↓
② 비즈니스 코드 작성
        ↓
③ 테스트 코드 작성
   - 정상 케이스
   - 경계값 / 예외 케이스
   - 스펙에서 놓친 부분 발견 시 → 스펙 & 비즈니스 코드 보완
        ↓
④ 이후 기능 변경 시 테스트가 회귀 방어막 역할
```

---

## SDD 문서 디렉토리 구조

```
sdd-spec-docs/
├── 00_architecture.md           # 아키텍처 개요 (EN)
├── 00_architecture_KR.md        # 아키텍처 개요 (KR)
└── feature/
    ├── jym-api-gateway/         # Gateway 관련 스펙
    ├── jym-catalog-service/     # 카탈로그 서비스 스펙
    ├── jym-front/               # 프론트엔드 페이지 스펙
    ├── jym-member-auth-service/ # 인증 서비스 스펙
    ├── jym-order-service/       # 주문 서비스 스펙
    ├── jym-payment-service/     # 결제 서비스 스펙
    └── msa-resilience/          # Kafka/Saga/CircuitBreaker 스펙
```

---

## 파일 네이밍 컨벤션

### 번호 접두사
```
00_OAS_PLAN_KR.md          # API 개요 & 계획
01_TABLE_DESIGN_KR.md      # 테이블 설계
02_IMPLEMENTATION_KR.md    # 구현 상세
03_XXX_FEATURE_KR.md       # 추가 기능별 상세
04_TEST_SPEC_KR.md         # 테스트 스펙
openapi.yaml               # OpenAPI 3.0 Spec
```

### 다국어 접미사
- `_KR.md` — 한국어
- `_EN.md` — 영어
- `_JP.md` — 일본어

---

## 새 기능 스펙 작성 절차

### Step 1: OAS 계획 문서 작성
대상 서비스의 `sdd-spec-docs/feature/{서비스명}/` 폴더에 계획 문서를 생성합니다.

```markdown
# {기능명} API 계획

## 개요
이 기능이 해결하는 비즈니스 문제

## API 엔드포인트
| Method | Path | 설명 |
|--------|------|------|
| POST | /api/v1/xxx | ... |

## 의존성
- 다른 서비스와의 통신 방식 (REST / Kafka)
- 필요한 인프라 (Redis, S3 등)
```

### Step 2: openapi.yaml 업데이트
해당 서비스의 `openapi.yaml`에 새 엔드포인트를 추가합니다.

```yaml
paths:
  /api/v1/xxx:
    post:
      operationId: createXxx
      summary: XXX 생성
      tags:
        - Xxx
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/XxxCreateRequest'
      responses:
        '201':
          description: 성공
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/XxxResponse'
        '400':
          $ref: '#/components/responses/BadRequest'
```

### Step 3: 구현 상세 문서 작성 (선택)
복잡한 기능의 경우 구현 가이드 문서를 작성합니다.

```markdown
# {기능명} 구현 상세

## 패키지 구조 변경

## 엔티티/테이블 설계

## 비즈니스 로직 흐름

## Kafka 이벤트 설계 (해당 시)

## 보안/권한 설정
```

### Step 4: 코드 구현
스펙에 맞춰 비즈니스 코드를 작성합니다. (`backend/01_new_api_endpoint.md` 참조)

### Step 5: 테스트 작성
구현이 스펙과 일치하는지 검증합니다. (`backend/03_unit_test.md` 참조)

---

## 체크리스트

- [ ] `sdd-spec-docs/feature/{서비스명}/` 에 계획 문서가 작성되었는가?
- [ ] `openapi.yaml`에 새 엔드포인트가 정의되었는가?
- [ ] 스펙 문서 번호가 기존 문서와 순서대로 이어지는가?
- [ ] 다국어 문서가 필요한 경우 `_KR.md`, `_EN.md`, `_JP.md` 접미사를 사용했는가?
- [ ] 구현된 코드가 openapi.yaml 스펙과 일치하는가?
- [ ] 테스트 코드가 스펙의 모든 케이스를 커버하는가?

---

## 관련 스킬

- `_common/00_project_context.md` — 프로젝트 컨텍스트
- `backend/01_new_api_endpoint.md` — API 구현
- `backend/03_unit_test.md` — 테스트 작성
