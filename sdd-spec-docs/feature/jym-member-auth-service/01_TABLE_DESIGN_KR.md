############## Complete #################

# 01_TABLE_DESIGN (회원 및 인증 서비스)

## 1. 개요
본 문서는 `openapi.yaml` 명세를 바탕으로 `jym-member-auth-service`의 데이터베이스 스키마를 정의합니다.

## 2. 테이블: `members`
사용자 계정 정보 및 인증 데이터를 저장합니다.

| 컬럼명 | 데이터 타입 | 제약 조건 | 설명 |
| :--- | :--- | :--- | :--- |
| `id` | BIGINT | PK, 자동 증가 | 사용자 고유 식별자 |
| `username` | VARCHAR(50) | Unique, Not Null | 유일한 로그인 식별 아이디 |
| `password` | VARCHAR(255) | | BCrypt 해시된 비밀번호 (OAuth 전용 가입 시 NULL 가능) |
| `email` | VARCHAR(100) | | 사용자 연락용 이메일 (선택적 수집 가능) |
| `nickname` | VARCHAR(50) | Not Null | 서비스 내 표시 닉네임 |
| `role` | VARCHAR(20) | Not Null | 사용자 권한 (ROLE_USER, ROLE_ADMIN) |
| `auth_provider` | VARCHAR(20) | Not Null | 로그인 출처 (LOCAL, GOOGLE, NAVER, KAKAO) |
| `provider_id` | VARCHAR(255) | | 소셜 서비스의 고유 식별자 |
| `is_active` | BOOLEAN | 기본값: TRUE | 계정 활성화 여부 |
| `created_at` | DATETIME | 기본값: 현재 시간 | 레코드 생성 일시 |
| `updated_at` | DATETIME | 기본값: 현재 시간 | 최종 수정 일시 |

## 3. 구현 참고 사항
- **Lombok**: 엔티티 클래스에서 `@Builder`, `@Getter`, `@NoArgsConstructor`를 적극 활용합니다.
- **보안**: 비밀번호는 절대 평문으로 저장하지 않으며 Spring Security의 `PasswordEncoder`를 사용합니다.
- **인덱스**: 빠른 검색을 위해 `email`과 `username`에 유니크 인덱스를 적용합니다.
