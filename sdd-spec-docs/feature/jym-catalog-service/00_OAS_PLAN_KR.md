# 00_OAS_PLAN (상품 카탈로그 서비스)

## 1. 목적
`jym-catalog-service`의 상품 목록 조회, 상세 정보, 카테고리 관리 및 관리자 상품 CRUD를 위한 API 명세를 정의합니다.

## 2. 핵심 엔드포인트

### 공개 API (인증 불필요)
- `GET /api/v1/products`: 상품 목록 조회 (페이징, 카테고리 필터 지원)
- `GET /api/v1/products/{id}`: 특정 상품의 상세 정보 확인
- `GET /api/v1/categories`: 음악 장르 카테고리 목록 조회

### 관리자 API (`ROLE_ADMIN` 필수 — `X-User-Role` 헤더 기반)
- `POST /api/v1/products`: 상품 등록 (응답: 201)
- `PUT /api/v1/products/{id}`: 상품 정보 수정 (응답: 200)
- `DELETE /api/v1/products/{id}`: 상품 삭제 — 논리 삭제(`isAvailable=false`) (응답: 204)
- `POST /api/v1/media/presigned-url`: S3 직접 업로드용 Presigned URL 발급 (응답: 200)

## 3. 기술 스택 요구사항
- 백엔드: Spring Boot 3.x (Java 21)
- 데이터베이스: MySQL (`jym_catalog_db`)
- 오류 처리: `GlobalException` + `GlobalExceptionHandler`
- 인가: Spring Security (`@PreAuthorize`) + API Gateway 헤더(`X-User-Id`, `X-User-Role`) 기반
- 파일 업로드: AWS S3 SDK v2 Presigned URL (직접 업로드 방식)

## 4. 인증/인가 흐름
```
클라이언트 → API Gateway (JWT 검증) → X-User-Id / X-User-Role 헤더 주입
    → jym-catalog-service (헤더 파싱 → SecurityContext 주입)
        → @PreAuthorize("hasRole('ADMIN')") 체크
```

## 5. 이미지 처리 전략
- 클라이언트: `POST /api/v1/media/presigned-url` → S3 직접 PUT 업로드 → `objectKey` 획득
- 상품 저장: `imageKey`(objectKey)를 상품 등록/수정 요청에 포함
- DB 저장: `image_key` (objectKey만 저장)
- 응답: 서비스에서 `s3BaseUrl + "/" + imageKey` 로 조합하여 `imageUrl`/`thumbnailUrl` 반환

## 6. 상세 구현 스펙 참조
- 구현 가이드: `02_IMPLEMENTATION_KR.md`
- DB 스키마: `01_TABLE_DESIGN_KR.md`
- OpenAPI 명세: `openapi.yaml`
