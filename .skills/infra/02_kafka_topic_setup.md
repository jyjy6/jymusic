# Kafka 토픽 & DLT 설정

> **참조**: `.skills/_common/00_project_context.md`

## 개요
새 Kafka 토픽과 Dead Letter Topic을 설정하는 표준 절차.

## 입력
- 토픽명, 사용 서비스(발행/소비), 이벤트 타입 목록

## 절차

### Step 1: KafkaTopics 상수 추가

발행/소비하는 **모든 서비스**의 `event/common/KafkaTopics.java`에 추가:

```java
public final class KafkaTopics {
    private KafkaTopics() {}

    public static final String ORDER_EVENTS        = "jym.order.events";
    public static final String PAYMENT_EVENTS      = "jym.payment.events";
    public static final String STOCK_EVENTS        = "jym.stock.events";
    public static final String NOTIFICATION_EVENTS = "jym.notification.events";

    // ── 새 토픽 ──
    public static final String REVIEW_EVENTS = "jym.review.events";
}
```

**네이밍 컨벤션**: `jym.{도메인}.events`

### Step 2: EventTypes 상수 추가

발행/소비하는 **모든 서비스**의 `event/common/EventTypes.java`에 추가:

```java
public static final String REVIEW_CREATED  = "REVIEW_CREATED";
public static final String REVIEW_UPDATED  = "REVIEW_UPDATED";
public static final String REVIEW_DELETED  = "REVIEW_DELETED";
```

### Step 3: KafkaConfig 토픽 자동 생성 (선택)

Kafka `auto.create.topics.enable=true`(현재 설정)이면 자동 생성되지만,
명시적으로 토픽 빈을 등록할 수도 있습니다:

```java
@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic reviewEventsTopic() {
        return TopicBuilder.name(KafkaTopics.REVIEW_EVENTS)
                .partitions(3)      // 파티션 수
                .replicas(1)        // dev: 1, prod: 3
                .build();
    }
}
```

### Step 4: DLT (Dead Letter Topic) 동작 확인

현재 `KafkaConfig`에 `DeadLetterPublishingRecoverer`가 설정되어 있으므로,
처리 실패 메시지는 자동으로 `{원본토픽}.DLT` 토픽으로 전달됩니다.

```
jym.review.events      ← 원본 토픽
jym.review.events.DLT  ← 실패 메시지 자동 전달
```

재시도 정책: `FixedBackOff(1000L, 3L)` — 1초 간격 3회 재시도 후 DLT 전송

### Step 5: Kafka UI에서 검증

1. `http://localhost:8090` 접속 (Kafka UI)
2. Topics 메뉴에서 새 토픽 확인
3. 테스트 메시지 발행 후 소비 확인
4. DLT 토픽에 실패 메시지가 쌓이는지 확인

## 현재 토픽 목록

| 토픽 | 발행 서비스 | 소비 서비스 | 용도 |
|------|-------------|-------------|------|
| `jym.order.events` | order | catalog | 주문 생성/취소 → 재고 예약/해제 |
| `jym.payment.events` | payment | order, catalog | 결제 완료/실패 → 주문 상태, 재고 복구 |
| `jym.stock.events` | catalog | order | 재고 예약 성공/실패 → 주문 상태 |
| `jym.notification.events` | order | order (내부) | SSE 알림 트리거 |

## application.yml Kafka 설정 참고

```yaml
spring:
  kafka:
    bootstrap-servers: localhost:9092    # 로컬 개발
    # Docker 내부: kafka:29092 (docker-compose 환경 변수로 오버라이드)
    consumer:
      group-id: jym-{서비스명}-group
      auto-offset-reset: earliest
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      properties:
        spring.json.trusted.packages: "*"
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
```

## 체크리스트
- [ ] `KafkaTopics.java` — 발행/소비 양쪽 서비스에 상수 추가
- [ ] `EventTypes.java` — 발행/소비 양쪽 서비스에 상수 추가
- [ ] 토픽 네이밍: `jym.{도메인}.events`
- [ ] DLT가 KafkaConfig에 설정되어 있는지 확인
- [ ] Kafka UI에서 토픽 생성 확인
- [ ] consumer group-id가 서비스별 고유값인지 확인

## 관련 스킬
- `backend/02_kafka_event_flow.md` — 이벤트 발행/소비 구현
- `infra/01_docker_service.md` — Docker 인프라
