# 00_OAS_PLAN (상품 카탈로그 서비스)

## 1. 목적
`jym-catalog-service`의 상품 목록 조회, 상세 정보 및 카테고리 관리를 위한 API 명세를 정의합니다.

## 2. 핵심 엔드포인트
- `GET /api/v1/products`: 상품 목록 조회 (페이징 지원).
- `GET /api/v1/products/{id}`: 특정 상품의 상세 정보 확인.
- `GET /api/v1/categories`: 상품 카테고리 조회.

## 3. 기술 스택 요구사항
- 백엔드: Spring Boot
- 데이터베이스: MySQL (상품/카테고리/재고 테이블)
- 오류 처리: `GlobalErrorHandler.GlobalException`
