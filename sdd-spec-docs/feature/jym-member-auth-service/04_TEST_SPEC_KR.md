# 04_TEST_SPEC (Member & Auth Service)

## 1. 개요

본 문서는 `jym-member-auth-service`의 단위 테스트 명세를 정의한다.
테스트는 비즈니스 코드 **이후**에 작성하며, 구현이 아닌 OAS 스펙을 기준으로 한다.
모든 테스트는 단위 테스트이다. 통합 테스트는 `00_architecture.md §2.3`에 따라 보류한다.

---

## 2. 테스트 레이어 및 도구

| 레이어       | 어노테이션 / 도구                     | 범위                   |
| :----------- | :------------------------------------ | :--------------------- |
| Controller   | `@WebMvcTest` + `MockMvc`             | HTTP 요청/응답 계약    |
| Service      | `@ExtendWith(MockitoExtension.class)` | 비즈니스 로직 격리     |
| JwtProvider  | `@ExtendWith(MockitoExtension.class)` | 토큰 생성 및 클레임    |
| RedisService | `@ExtendWith(MockitoExtension.class)` | 리프레시 토큰 생명주기 |

---

## 3. Controller 레이어 테스트

### 3.1 `MemberAuthControllerTest`

#### `POST /api/v1/auth/register`

| #    | 시나리오                   | 입력                               | 기대 상태         | 기대 응답                                  |
| :--- | :------------------------- | :--------------------------------- | :---------------- | :----------------------------------------- |
| R-01 | 정상 — 유효한 가입         | 유효한 `MemberRegistrationRequest` | `201 Created`     | 올바른 필드를 가진 `MemberProfileResponse` |
| R-02 | 사용자명 중복              | 이미 존재하는 `username`           | `409 Conflict`    | `GlobalException` 에러 응답                |
| R-03 | 이메일 중복                | 이미 존재하는 `email`              | `409 Conflict`    | `GlobalException` 에러 응답                |
| R-04 | 필수 필드 누락(`nickname`) | `nickname` 없는 요청               | `400 Bad Request` | Bean Validation 에러                       |
| R-05 | 필수 필드 누락(`username`) | `username` 없는 요청               | `400 Bad Request` | Bean Validation 에러                       |

#### `POST /api/v1/auth/login`

| #    | 시나리오                | 입력                             | 기대 상태          | 기대 응답                                                 |
| :--- | :---------------------- | :------------------------------- | :----------------- | :-------------------------------------------------------- |
| L-01 | 정상 — 올바른 인증 정보 | 유효한 `MemberLoginRequest`      | `200 OK`           | `accessToken`과 `refreshToken`을 가진 `AuthTokenResponse` |
| L-02 | 비밀번호 오류           | 올바른 username, 잘못된 password | `401 Unauthorized` | `GlobalException` 에러 응답                               |
| L-03 | 존재하지 않는 사용자명  | 알 수 없는 `username`            | `401 Unauthorized` | `GlobalException` 에러 응답                               |
| L-04 | 비활성 계정             | `is_active = false`인 멤버       | `403 Forbidden`    | `GlobalException` 에러 응답                               |
| L-05 | 필수 필드 누락          | `password` 없는 요청             | `400 Bad Request`  | Bean Validation 에러                                      |

---

### 3.2 `MemberControllerTest`

#### `GET /api/v1/members/me`

| #    | 시나리오              | 입력                                                        | 기대 상태          | 기대 응답                                  |
| :--- | :-------------------- | :---------------------------------------------------------- | :----------------- | :----------------------------------------- |
| M-01 | 정상 — 인증된 요청    | 게이트웨이가 주입한 유효한 `X-User-Id` / `X-User-Role` 헤더 | `200 OK`           | 올바른 필드를 가진 `MemberProfileResponse` |
| M-02 | 사용자 식별 헤더 누락 | `X-User-Id` 헤더 없음                                       | `401 Unauthorized` | `GlobalException` 에러 응답                |
| M-03 | DB에 사용자 없음      | 유효한 헤더이나 멤버 삭제됨                                 | `404 Not Found`    | `GlobalException` 에러 응답                |

> **Note**: JWT 검증은 `jym-api-gateway`에서 수행한다. 본 서비스는 게이트웨이가 전달하는 `X-User-Id` / `X-User-Role` 헤더만 신뢰한다.

---

## 4. Service 레이어 테스트

### 4.1 `MemberAuthServiceTest`

#### `register(MemberRegistrationRequest)`

| #     | 시나리오           | Mock 동작                                                                                  | 기대 결과                          |
| :---- | :----------------- | :----------------------------------------------------------------------------------------- | :--------------------------------- |
| RS-01 | 정상               | `repo.existsByUsername` → false, `repo.existsByEmail` → false, `repo.save` → 저장된 엔티티 | `MemberProfileResponse` 반환       |
| RS-02 | 사용자명 이미 존재 | `repo.existsByUsername` → true                                                             | `GlobalException`(CONFLICT) 발생   |
| RS-03 | 이메일 이미 존재   | `repo.existsByEmail` → true                                                                | `GlobalException`(CONFLICT) 발생   |
| RS-04 | 비밀번호 인코딩    | BCryptPasswordEncoder가 원문 비밀번호로 호출됨                                             | 저장된 비밀번호는 원문과 같지 않음 |

#### `login(MemberLoginRequest)`

| #     | 시나리오                   | Mock 동작                                  | 기대 결과                                                 |
| :---- | :------------------------- | :----------------------------------------- | :-------------------------------------------------------- |
| LS-01 | 정상                       | 멤버 발견, 비밀번호 일치, 계정 활성        | 빈 값이 아닌 토큰을 가진 `AuthTokenResponse` 반환         |
| LS-02 | 사용자명 미발견            | `repo.findByUsername` → `Optional.empty()` | `GlobalException`(UNAUTHORIZED) 발생                      |
| LS-03 | 비밀번호 불일치            | `passwordEncoder.matches` → false          | `GlobalException`(UNAUTHORIZED) 발생                      |
| LS-04 | 계정 비활성                | 멤버는 있으나 `is_active = false`          | `GlobalException`(FORBIDDEN) 발생                         |
| LS-05 | Redis에 리프레시 토큰 저장 | 로그인 성공                                | `redisService.save(RT:{username}, refreshToken)` 1회 호출 |

#### `getMyProfile(Long userId)`

| #     | 시나리오    | Mock 동작                                    | 기대 결과                         |
| :---- | :---------- | :------------------------------------------- | :-------------------------------- |
| MP-01 | 정상        | `repo.findById(userId)` → 멤버 존재          | `MemberProfileResponse` 반환      |
| MP-02 | 멤버 미발견 | `repo.findById(userId)` → `Optional.empty()` | `GlobalException`(NOT_FOUND) 발생 |

---

## 5. JWT 컴포넌트 테스트

### 5.1 `JwtProviderTest`

| #    | 시나리오                         | 기대 결과                                                  |
| :--- | :------------------------------- | :--------------------------------------------------------- |
| J-01 | 액세스 토큰 생성                 | 빈 값이 아닌 JWT 문자열 반환                               |
| J-02 | 액세스 토큰에 올바른 클레임 포함 | `sub` = username, `userId`, `role` 모두 올바른 값으로 존재 |
| J-03 | 액세스 토큰 만료가 스펙 내       | 발급 시점으로부터 약 15~30분으로 설정                      |
| J-04 | 리프레시 토큰 생성               | 빈 값이 아닌 토큰 문자열 반환                              |
| J-05 | 리프레시 토큰 만료가 스펙 내     | 발급 시점으로부터 약 7일로 설정                            |

---

## 6. RedisService 테스트

### 6.1 `RedisServiceTest`

| #     | 시나리오                       | 기대 결과                               |
| :---- | :----------------------------- | :-------------------------------------- |
| RD-01 | 리프레시 토큰 저장             | 키 `RT:{username}`에 올바른 값으로 저장 |
| RD-02 | 리프레시 토큰 조회 — 존재      | 저장된 토큰 문자열 반환                 |
| RD-03 | 리프레시 토큰 조회 — 미존재    | `null` 또는 `Optional.empty()` 반환     |
| RD-04 | 리프레시 토큰 삭제             | 키 `RT:{username}` 제거                 |
| RD-05 | RTR — 재발급 시 구 토큰 무효화 | 구 RT 삭제, 신규 RT를 동일 키로 저장    |

---

## 7. 명명 규칙

- 테스트 클래스: `{TargetClass}Test.java`
- 테스트 메서드: `{method}_{scenario}_{expectedResult}` (예: `login_wrongPassword_throwsUnauthorized`)
- 사람이 읽기 쉬운 설명에는 `@DisplayName`으로 한국어를 사용한다.

---

## 8. 범위 외 (보류)

- 통합 테스트 (DB, Redis 컨테이너 기반)
- 시큐리티 필터 체인 테스트
- 소셜 로그인 (`GOOGLE`, `NAVER`, `KAKAO`) 경로
