# Docker Compose 서비스 추가

> **참조**: `.skills/_common/00_project_context.md`

## 개요
`docker-compose-dev.yml`에 새 서비스(백엔드/인프라)를 추가하는 표준 절차.

## 입력
- 서비스명, 포트 매핑, 의존 서비스, 환경 변수

## 1. 백엔드 서비스 추가

### Dockerfile.dev (multi-stage)
```dockerfile
# ── Build Stage ──
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app
COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle ./
RUN ./gradlew dependencies --no-daemon --quiet  # 의존성 캐싱 레이어
COPY src src
RUN ./gradlew bootJar --no-daemon -x test

# ── Run Stage ──
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### docker-compose-dev.yml 서비스 블록
```yaml
jym-{서비스명}:
  build:
    context: ./jym-{서비스명}
    dockerfile: Dockerfile.dev
  container_name: jym-{서비스명}
  restart: unless-stopped
  environment:
    SPRING_KAFKA_BOOTSTRAP_SERVERS: kafka:29092     # Docker 내부 호스트명
    SPRING_DATA_REDIS_HOST: redis                   # Redis 사용 시
    SPRING_DATA_REDIS_PORT: 6379
  ports:
    - "{호스트포트}:8080"    # 컨테이너 내부는 항상 8080
  depends_on:
    - kafka                  # 의존 서비스
    - redis                  # 필요 시
  networks:
    - jym-network
```

## 2. 프론트엔드 서비스 (Nuxt)
```yaml
jym-front:
  build:
    context: ./jym-front
    dockerfile: Dockerfile.dev
  container_name: jym-front
  restart: unless-stopped
  environment:
    NUXT_PUBLIC_API_BASE_URL: http://localhost:8080
  ports:
    - "3000:3000"
  volumes:
    - ./jym-front:/app          # 소스 마운트 (HMR)
    - /app/node_modules         # node_modules 격리
  depends_on:
    - jym-api-gateway
  networks:
    - jym-network
```

## 3. 인프라 서비스 추가 예시 (Elasticsearch 등)
```yaml
elasticsearch:
  image: docker.elastic.co/elasticsearch/elasticsearch:8.x
  container_name: jym-elasticsearch
  restart: unless-stopped
  environment:
    - discovery.type=single-node
    - xpack.security.enabled=false
  ports:
    - "9200:9200"
  volumes:
    - es_data:/usr/share/elasticsearch/data
  networks:
    - jym-network
```
volumes 섹션에 추가:
```yaml
volumes:
  es_data:
```

## 4. API Gateway 환경 변수 업데이트

새 서비스 추가 시 Gateway의 환경 변수에 서비스 URL을 추가합니다:
```yaml
jym-api-gateway:
  environment:
    SERVICES_MEMBER_AUTH_URL: http://jym-member-auth-service:8080
    SERVICES_CATALOG_URL: http://jym-catalog-service:8080
    SERVICES_ORDER_URL: http://jym-order-service:8080
    SERVICES_PAYMENT_URL: http://jym-payment-service:8080
    SERVICES_{NEW_SERVICE}_URL: http://jym-{서비스명}:8080   # ← 추가
  depends_on:
    - jym-{서비스명}   # ← depends_on에도 추가
```

## 현재 포트 매핑 현황

| 서비스 | 호스트 포트 | 컨테이너 포트 |
|--------|:-----------:|:------------:|
| Redis | 6379 | 6379 |
| Zookeeper | 2181 | 2181 |
| Kafka | 9092 / 29092 | 9092 / 29092 |
| Kafka UI | 8090 | 8080 |
| member-auth | 8081 | 8080 |
| catalog | 8082 | 8080 |
| order | 8083 | 8080 |
| payment | 8084 | 8080 |
| api-gateway | 8080 | 8080 |
| front | 3000 | 3000 |

> 새 서비스는 **8085** 부터 할당합니다.

## 체크리스트
- [ ] Dockerfile.dev multi-stage 빌드
- [ ] docker-compose-dev.yml 서비스 블록 추가
- [ ] `networks: jym-network` 연결
- [ ] Kafka/Redis 등 Docker 내부 호스트명으로 환경 변수 설정
- [ ] Gateway 환경 변수 & depends_on 업데이트
- [ ] 포트 충돌 없는지 확인
- [ ] 볼륨 필요 시 volumes 섹션에 추가

## 관련 스킬
- `backend/04_new_microservice.md` — 새 서비스 전체 부트스트랩
