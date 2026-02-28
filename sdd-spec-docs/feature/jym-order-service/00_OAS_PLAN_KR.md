# 00_OAS_PLAN (주문 서비스)

## 1. 목적
`jym-order-service`의 주문 생성, 주문 내역 조회 및 상태 관리를 위한 API 명세를 정의합니다.

## 2. 핵심 엔드포인트
- `POST /api/v1/orders`: 새로운 주문 생성.
- `GET /api/v1/orders`: 사용자의 주문 내역 조회.
- `GET /api/v1/orders/{id}`: 특정 주문의 상세 정보 확인.

## 3. 기술 스택 요구사항
- 백엔드: Spring Boot
- 데이터베이스: MySQL (주문/주문 항목 테이블)
- 오류 처리: `GlobalErrorHandler.GlobalException`
