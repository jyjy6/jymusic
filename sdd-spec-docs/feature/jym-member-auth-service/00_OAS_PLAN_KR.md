############## Complete #################

# 00_OAS_PLAN (회원 및 인증 서비스)

## 1. 목적
`jym-member-auth-service`의 인증, 회원가입, 프로필 관리를 위한 API 명세를 정의합니다.

## 2. 핵심 엔드포인트
- `POST /api/v1/auth/login`: JWT 발급.
- `POST /api/v1/auth/register`: 신규 회원 가입.
- `GET /api/v1/members/me`: 현재 사용자 프로필 조회.

## 3. 기술 스택 요구사항
- 백엔드: Spring Boot (Spring Security)
- 데이터베이스: MySQL (사용자/권한 테이블)
- 오류 처리: `GlobalErrorHandler.GlobalException`
