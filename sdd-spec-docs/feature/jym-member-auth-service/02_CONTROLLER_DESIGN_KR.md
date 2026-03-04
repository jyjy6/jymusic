############## Complete #################

# 02_CONTROLLER_DESIGN (회원 및 인증 서비스)

## 1. 개요
본 문서는 `openapi.yaml` 명세를 바탕으로 `jym-member-auth-service`의 REST API 컨트롤러를 정의합니다.

## 2. 컨트롤러: `MemberAuthController`
회원가입 및 인증 워크플로우를 처리합니다.

| 엔드포인트 | 메서드 | 요청 DTO | 응답 DTO | 설명 |
| :--- | :--- | :--- | :--- | :--- |
| `/api/v1/auth/register` | `POST` | `MemberRegistrationRequest` | `MemberProfileResponse` | 신규 사용자 계정 생성 |
| `/api/v1/auth/login` | `POST` | `MemberLoginRequest` | `AuthTokenResponse` | 사용자 인증 및 JWT 발급 |

## 3. 컨트롤러: `MemberController`
프로필 관리 및 사용자 전용 쿼리를 처리합니다.

| 엔드포인트 | 메서드 | 요청 DTO | 응답 DTO | 설명 |
| :--- | :--- | :--- | :--- | :--- |
| `/api/v1/members/me` | `GET` | (없음) | `MemberProfileResponse` | 현재 사용자 프로필 조회 |

## 4. 구현 상세 사항
- **유효성 검증**: 요청 DTO에 `@Valid` 어노테이션을 사용하여 빈 검증을 수행합니다.
- **성공 응답**: 회원가입 시 `HttpStatus.CREATED`(201), 프로필 조회 시 `HttpStatus.OK`(200)를 반환합니다.
- **서비스 상호작용**: 컨트롤러는 오직 서비스 계층의 메서드만 호출해야 합니다.
