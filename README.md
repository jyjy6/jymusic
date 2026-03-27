# Jymusic

**음악 앨범 판매 이커머스 플랫폼** — Microservices Architecture (MSA) 기반

---

## 프로젝트 개요

Jymusic은 음악 앨범을 판매하는 이커머스 서비스입니다.  
모든 도메인이 독립적인 마이크로서비스로 분리되어 있으며, 단일 API 게이트웨이를 통해 클라이언트 요청을 라우팅합니다.

---

## 기술 스택

| 구분        | 기술                                                                                       |
| ----------- | ------------------------------------------------------------------------------------------ |
| Frontend    | Nuxt 4 (Vue 3, TypeScript, Tailwind CSS)                                                   |
| Backend     | Spring Boot 3.x / 4.x (Java 21), JPA(CUD), MyBatis(R), Spring Cloud, Kafka, Redis, LangChain4j, RAG |
| API Gateway | Spring Cloud Gateway (WebMvc->WebFlux 비동기 분산처리)                                        |
| Database    | MySQL (서비스별 독립 DB), Pinecone                                                         |
| 인증        | JWT (Stateless)                                                                            |
| 인프라      | Docker / Docker Compose                                                                    |

---

## 서비스 구조

```
jymusic/
├── jym-front/              # Nuxt 4 프론트엔드
├── jym-api-gateway/        # API 게이트웨이 (단일 진입점)
├── jym-member-auth-service/ # 회원 가입 / 로그인 / JWT 인증
├── jym-catalog-service/    # 음악 앨범 카탈로그 (상품 목록/상세)
├── jym-order-service/      # 주문 처리
├── jym-payment-service/    # 결제 처리
├── sdd-spec-docs/          # OpenAPI Spec 문서 (SDD 원칙)
└── docker/                 # Docker 환경 설정
```

### 각 서비스 역할

| 서비스                    | 역할                                                         |
| ------------------------- | ------------------------------------------------------------ |
| `jym-api-gateway`         | 모든 클라이언트 요청의 단일 진입점. 라우팅 및 인증 필터 처리 |
| `jym-member-auth-service` | 회원 가입, 로그인, JWT 발급 및 검증                          |
| `jym-catalog-service`     | 음악 앨범 상품 등록, 조회, 관리                              |
| `jym-order-service`       | 장바구니 및 주문 생성/조회                                   |
| `jym-payment-service`     | 결제 요청 및 결과 처리                                       |
| `jym-front`               | 사용자 대면 웹 UI                                            |

---

## 아키텍처 원칙

- **Database-per-service**: 각 서비스는 자신의 DB만 접근 (직접 크로스 DB 접근 금지)
- **Spec-Driven Development (SDD)**: 모든 API 변경은 OpenAPI Spec 작성에서 시작
- **Stateless**: 서버에 세션 저장 없음, JWT 기반 인가
- **단위 테스트 커버리지 70% 이상** 유지

---

## 로컬 실행

```bash
# 전체 서비스 Docker Compose로 실행
docker-compose -f docker-compose-dev.yml up
```
