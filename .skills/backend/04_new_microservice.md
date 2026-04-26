# 새 마이크로서비스 부트스트랩

> **참조**: `.skills/_common/00_project_context.md`

## 개요
새 Spring Boot 마이크로서비스를 프로젝트에 추가하는 전체 절차.

## 입력
- 서비스명 (예: `jym-review-service`), 포트번호, DB명, 핵심 도메인 설명

## 절차

### Step 1: Spring Initializr로 프로젝트 생성
- Group: `jymusic`, Java 21, Gradle (Groovy), Spring Boot 4.0.3
- 필수 의존성: Web, JPA, Security, Validation, Kafka, Actuator, Lombok, MySQL, DevTools

### Step 2: build.gradle 설정
`_common/00_project_context.md`의 "build.gradle 표준 의존성" 섹션 참조.
QueryDSL, Resilience4j 등은 필요에 따라 추가.

### Step 3: 패키지 스캐폴딩
```
src/main/java/jymusic/jym_{서비스명}/
├── config/
│   ├── SecurityConfig.java
│   ├── JpaConfig.java         # @EnableJpaAuditing
│   └── KafkaConfig.java       # (Kafka 사용 시)
├── controller/
├── service/
├── domain/
│   ├── entity/
│   │   └── BaseTimeEntity.java
│   └── repository/
├── dto/
│   ├── request/
│   └── response/
├── event/                     # (Kafka 사용 시)
│   ├── common/
│   │   ├── EventEnvelope.java
│   │   ├── EventTypes.java
│   │   └── KafkaTopics.java
│   ├── consumer/
│   ├── payload/
│   └── publisher/
│       └── EventPublisher.java
├── filter/
│   └── GatewayAuthenticationFilter.java
└── common/
    └── GlobalErrorHandler/
        ├── GlobalException.java
        └── GlobalExceptionHandler.java
```

### Step 4: 공통 코드 복사
기존 서비스에서 다음 파일을 복사 (패키지명만 변경):
1. `BaseTimeEntity.java`
2. `GlobalException.java` + `GlobalExceptionHandler.java`
3. `GatewayAuthenticationFilter.java`
4. `SecurityConfig.java`
5. `JpaConfig.java` (`@EnableJpaAuditing`)
6. `KafkaConfig.java` + `EventEnvelope.java` + `EventPublisher.java` (Kafka 사용 시)

### Step 5: application.yml 설정
```yaml
spring:
  application:
    name: jym-{서비스명}
  datasource:
    url: jdbc:mysql://localhost:3306/jym_{db명}
    username: root
    password: ${DB_PASSWORD}
  jpa:
    hibernate:
      ddl-auto: update
    properties:
      hibernate:
        format_sql: true
        dialect: org.hibernate.dialect.MySQLDialect
  kafka:
    bootstrap-servers: localhost:9092
    consumer:
      group-id: jym-{서비스명}-group
      auto-offset-reset: earliest
      properties:
        spring.json.trusted.packages: "*"
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer

server:
  port: {포트번호}
```

### Step 6: Dockerfile.dev 작성
```dockerfile
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app
COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle ./
RUN ./gradlew dependencies --no-daemon --quiet
COPY src src
RUN ./gradlew bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/build/libs/*.jar app.jar
EXPOSE {포트번호}
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### Step 7: docker-compose-dev.yml에 서비스 추가
```yaml
jym-{서비스명}:
  build:
    context: ./jym-{서비스명}
    dockerfile: Dockerfile.dev
  container_name: jym-{서비스명}
  restart: unless-stopped
  environment:
    SPRING_KAFKA_BOOTSTRAP_SERVERS: kafka:29092
  ports:
    - "{포트}:8080"
  depends_on:
    - kafka
  networks:
    - jym-network
```

### Step 8: API Gateway 라우팅 추가
Gateway의 설정에 새 서비스 라우트를 추가합니다.

### Step 9: sdd-spec-docs에 스펙 폴더 생성
```
sdd-spec-docs/feature/jym-{서비스명}/
├── 00_OAS_PLAN_KR.md
├── 01_TABLE_DESIGN_KR.md
└── openapi.yaml
```

## 체크리스트
- [ ] build.gradle 의존성 표준 준수
- [ ] 공통 코드 5종 복사 (패키지명 변경)
- [ ] application.yml DB/Kafka/포트 설정
- [ ] Dockerfile.dev 작성
- [ ] docker-compose-dev.yml 서비스 추가
- [ ] Gateway 라우팅 추가
- [ ] SDD 스펙 폴더 생성
- [ ] MySQL 데이터베이스 생성

## 관련 스킬
- `_common/00_project_context.md`, `infra/01_docker_service.md`
