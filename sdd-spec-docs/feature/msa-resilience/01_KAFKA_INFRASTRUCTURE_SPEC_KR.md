# 01_KAFKA_INFRASTRUCTURE — Kafka 인프라 구축 스펙

> **목적**: Jymusic MSA 프로젝트에 Kafka를 도입하기 위한 인프라 설정, Topic 설계, Spring Boot 연동 구현 스펙  
> **선행 문서**: `00_MSA_RESILIENCE_GUIDE_KR.md`  
> **영향 범위**: Docker Compose, `jym-order-service`, `jym-payment-service`, `jym-catalog-service`

---

## 1. Docker Compose — Kafka 인프라 추가

### `docker-compose-dev.yml` 에 추가할 서비스

```yaml
  # ──────────────────────────────────────────
  # Kafka Infrastructure
  # ──────────────────────────────────────────
  zookeeper:
    image: confluentinc/cp-zookeeper:7.7.1
    container_name: jym-zookeeper
    restart: unless-stopped
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181
      ZOOKEEPER_TICK_TIME: 2000
    ports:
      - "2181:2181"
    volumes:
      - zookeeper_data:/var/lib/zookeeper/data
      - zookeeper_log:/var/lib/zookeeper/log
    networks:
      - jym-network

  kafka:
    image: confluentinc/cp-kafka:7.7.1
    container_name: jym-kafka
    restart: unless-stopped
    depends_on:
      - zookeeper
    ports:
      - "9092:9092"       # 호스트 접근용 (로컬 개발)
      - "29092:29092"     # Docker 내부 접근용
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:29092,PLAINTEXT_HOST://localhost:9092
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: PLAINTEXT:PLAINTEXT,PLAINTEXT_HOST:PLAINTEXT
      KAFKA_INTER_BROKER_LISTENER_NAME: PLAINTEXT
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1        # dev 단일 브로커
      KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR: 1
      KAFKA_TRANSACTION_STATE_LOG_MIN_ISR: 1
      KAFKA_AUTO_CREATE_TOPICS_ENABLE: "false"          # 수동 토픽 관리
      KAFKA_LOG_RETENTION_HOURS: 168                    # 7일 보관
    volumes:
      - kafka_data:/var/lib/kafka/data
    networks:
      - jym-network

  # Kafka UI — 개발 중 토픽/메시지 모니터링용
  kafka-ui:
    image: provectuslabs/kafka-ui:latest
    container_name: jym-kafka-ui
    restart: unless-stopped
    depends_on:
      - kafka
    ports:
      - "8090:8080"
    environment:
      KAFKA_CLUSTERS_0_NAME: jym-local
      KAFKA_CLUSTERS_0_BOOTSTRAPSERVERS: kafka:29092
      KAFKA_CLUSTERS_0_ZOOKEEPER: zookeeper:2181
    networks:
      - jym-network
```

### 추가할 Volumes

```yaml
volumes:
  redis_data:
  zookeeper_data:
  zookeeper_log:
  kafka_data:
```

### 서비스 의존성 업데이트

각 백엔드 서비스에 Kafka 의존성 추가:

```yaml
  jym-order-service:
    # ... 기존 설정 유지
    environment:
      SPRING_KAFKA_BOOTSTRAP_SERVERS: kafka:29092
    depends_on:
      - redis
      - kafka

  jym-payment-service:
    # ... 기존 설정 유지
    environment:
      SPRING_KAFKA_BOOTSTRAP_SERVERS: kafka:29092
    depends_on:
      - kafka

  jym-catalog-service:
    # ... 기존 설정 유지
    environment:
      SPRING_KAFKA_BOOTSTRAP_SERVERS: kafka:29092
    depends_on:
      - kafka
```

### 이미지 선택 근거

| 항목 | 선택 | 이유 |
|---|---|---|
| Kafka 이미지 | `confluentinc/cp-kafka:7.7.1` | Confluent 공식 이미지, 한국 현업 표준, Spring Kafka와 호환성 최상 |
| Zookeeper | `confluentinc/cp-zookeeper:7.7.1` | 동일 Confluent 스택으로 버전 일치 |
| UI | `provectuslabs/kafka-ui` | 오픈소스 무료, 직관적 UI, 토픽/메시지/Consumer Group 모니터링 |

> **KRaft 모드 (Zookeeper 없이)**: Kafka 3.7+에서 지원하지만, 한국 현업에서는 아직 Zookeeper 방식이 주류입니다.  
> 학습 단계에서는 레퍼런스가 풍부한 Zookeeper 방식을 권장합니다.

---

## 2. Topic 설계

### 2.1 Topic 목록

| Topic 명 | Producer | Consumer(s) | Key | 설명 |
|---|---|---|---|---|
| `jym.order.events` | order-service | payment-service, catalog-service | `orderId` | 주문 생성/취소 이벤트 |
| `jym.payment.events` | payment-service | order-service, catalog-service | `orderId` | 결제 완료/취소/실패 이벤트 |
| `jym.stock.events` | catalog-service | order-service | `orderId` | 재고 예약 성공/실패 이벤트 |
| `jym.order.events.DLT` | Kafka (자동) | 운영 모니터링 | — | order.events 처리 실패 메시지 |
| `jym.payment.events.DLT` | Kafka (자동) | 운영 모니터링 | — | payment.events 처리 실패 메시지 |
| `jym.stock.events.DLT` | Kafka (자동) | 운영 모니터링 | — | stock.events 처리 실패 메시지 |

### 2.2 Topic 명명 규칙

```
{프로젝트}.{도메인}.{유형}[.DLT]

예시:
  jym.order.events         ← 주문 도메인의 이벤트
  jym.order.events.DLT     ← 주문 이벤트 Dead Letter Topic
```

### 2.3 Partition 설계

| Topic | Partitions | Key | 이유 |
|---|---|---|---|
| `jym.order.events` | 3 | `orderId` | 같은 주문의 이벤트가 같은 파티션에 → 순서 보장 |
| `jym.payment.events` | 3 | `orderId` | 같은 주문의 결제 이벤트 순서 보장 |
| `jym.stock.events` | 3 | `orderId` | 같은 주문의 재고 이벤트 순서 보장 |
| `*.DLT` | 1 | — | DLT는 낮은 빈도, 단일 파티션 충분 |

> **파티션 수 3**: 개발 환경에서 충분. 프로덕션에서 서비스 인스턴스 수에 맞춰 확장 (인스턴스 수 ≤ 파티션 수).

### 2.4 Topic 생성 스크립트

`docker/kafka/create-topics.sh`:

```bash
#!/bin/bash
# Kafka Topic 생성 스크립트
# 사용법: docker exec -it jym-kafka bash /scripts/create-topics.sh

KAFKA_BIN=/usr/bin
BOOTSTRAP=localhost:29092

echo "=== Creating Kafka Topics ==="

# 메인 토픽
$KAFKA_BIN/kafka-topics --create --bootstrap-server $BOOTSTRAP \
  --topic jym.order.events --partitions 3 --replication-factor 1 \
  --config retention.ms=604800000 --if-not-exists

$KAFKA_BIN/kafka-topics --create --bootstrap-server $BOOTSTRAP \
  --topic jym.payment.events --partitions 3 --replication-factor 1 \
  --config retention.ms=604800000 --if-not-exists

$KAFKA_BIN/kafka-topics --create --bootstrap-server $BOOTSTRAP \
  --topic jym.stock.events --partitions 3 --replication-factor 1 \
  --config retention.ms=604800000 --if-not-exists

# Dead Letter Topics
$KAFKA_BIN/kafka-topics --create --bootstrap-server $BOOTSTRAP \
  --topic jym.order.events.DLT --partitions 1 --replication-factor 1 \
  --config retention.ms=2592000000 --if-not-exists

$KAFKA_BIN/kafka-topics --create --bootstrap-server $BOOTSTRAP \
  --topic jym.payment.events.DLT --partitions 1 --replication-factor 1 \
  --config retention.ms=2592000000 --if-not-exists

$KAFKA_BIN/kafka-topics --create --bootstrap-server $BOOTSTRAP \
  --topic jym.stock.events.DLT --partitions 1 --replication-factor 1 \
  --config retention.ms=2592000000 --if-not-exists

echo "=== Topic List ==="
$KAFKA_BIN/kafka-topics --list --bootstrap-server $BOOTSTRAP

echo "=== Done ==="
```

> **retention.ms**: 일반 토픽 7일(604800000ms), DLT 30일(2592000000ms. 수동 처리 여유).

---

## 3. 이벤트 스키마 설계

### 3.1 공통 이벤트 Envelope

모든 이벤트는 다음 공통 구조를 따릅니다:

```java
/**
 * 모든 Kafka 이벤트의 공통 래퍼.
 * 이벤트 메타데이터(추적, 중복 방지)와 실제 페이로드를 포함.
 */
@Getter @Builder
public class EventEnvelope<T> {
    private String eventId;        // UUID — 이벤트 고유 식별자 (멱등성 체크용)
    private String eventType;      // 이벤트 타입 (예: "ORDER_CREATED")
    private int version;           // 스키마 버전 (하위 호환성 관리)
    private LocalDateTime timestamp; // 이벤트 발생 시각
    private String source;         // 발행 서비스명 (예: "jym-order-service")
    private T payload;             // 실제 이벤트 데이터
}
```

**JSON 직렬화 예시:**

```json
{
  "eventId": "550e8400-e29b-41d4-a716-446655440000",
  "eventType": "ORDER_CREATED",
  "version": 1,
  "timestamp": "2026-04-01T18:30:00",
  "source": "jym-order-service",
  "payload": {
    "orderId": 42,
    "memberId": 7,
    "totalAmount": 87000,
    "items": [
      { "productId": 5, "quantity": 2, "unitPrice": 29000 },
      { "productId": 8, "quantity": 1, "unitPrice": 29000 }
    ]
  }
}
```

### 3.2 이벤트 타입별 Payload

#### Order 도메인 이벤트 (Topic: `jym.order.events`)

```java
// orderId를 Kafka message key로 사용

/** 주문 생성 완료 — order-service가 발행 */
@Getter @Builder
public class OrderCreatedPayload {
    private Long orderId;
    private Long memberId;
    private BigDecimal totalAmount;
    private List<OrderItemPayload> items;
}

@Getter @Builder
public class OrderItemPayload {
    private Long productId;
    private String productTitle;
    private BigDecimal unitPrice;
    private int quantity;
}

/** 주문 취소 — order-service가 발행 (타임아웃 또는 재고 예약 실패 보상) */
@Getter @Builder
public class OrderCancelledPayload {
    private Long orderId;
    private Long memberId;
    private String reason;  // "STOCK_RESERVATION_FAILED", "PAYMENT_TIMEOUT", "USER_CANCELLED"
}
```

#### Payment 도메인 이벤트 (Topic: `jym.payment.events`)

```java
// orderId를 Kafka message key로 사용

/** 결제 승인 완료 — payment-service가 발행 */
@Getter @Builder
public class PaymentCompletedPayload {
    private Long orderId;
    private Long memberId;
    private String paymentKey;
    private BigDecimal amount;
    private String method;       // "CARD", "TRANSFER" 등
}

/** 결제 실패 — payment-service가 발행 */
@Getter @Builder
public class PaymentFailedPayload {
    private Long orderId;
    private Long memberId;
    private String reason;       // Toss API 에러 메시지 또는 내부 에러 코드
}

/** 결제 취소 완료 — payment-service가 발행 */
@Getter @Builder
public class PaymentCancelledPayload {
    private Long orderId;
    private Long memberId;
    private String paymentKey;
    private String cancelReason;
}
```

#### Stock 도메인 이벤트 (Topic: `jym.stock.events`)

```java
// orderId를 Kafka message key로 사용

/** 재고 예약 성공 — catalog-service가 발행 */
@Getter @Builder
public class StockReservedPayload {
    private Long orderId;
    private List<ReservedItem> reservedItems;
}

@Getter @Builder
public class ReservedItem {
    private Long productId;
    private int quantity;         // 예약된 수량
    private int remainingStock;   // 예약 후 남은 재고
}

/** 재고 예약 실패 — catalog-service가 발행 */
@Getter @Builder
public class StockReservationFailedPayload {
    private Long orderId;
    private Long failedProductId;    // 재고 부족 상품 ID
    private String failedProductTitle;
    private int requestedQuantity;
    private int availableStock;
}
```

### 3.3 이벤트 타입 상수 (공유 가능)

```java
/**
 * Kafka 이벤트 타입 상수.
 * 각 서비스에서 공통으로 사용.
 * (공유 라이브러리 또는 각 서비스에 복사)
 */
public final class EventTypes {
    private EventTypes() {}

    // Order Events
    public static final String ORDER_CREATED    = "ORDER_CREATED";
    public static final String ORDER_CANCELLED  = "ORDER_CANCELLED";

    // Payment Events
    public static final String PAYMENT_COMPLETED = "PAYMENT_COMPLETED";
    public static final String PAYMENT_FAILED    = "PAYMENT_FAILED";
    public static final String PAYMENT_CANCELLED = "PAYMENT_CANCELLED";

    // Stock Events
    public static final String STOCK_RESERVED          = "STOCK_RESERVED";
    public static final String STOCK_RESERVATION_FAILED = "STOCK_RESERVATION_FAILED";
    public static final String STOCK_RELEASED          = "STOCK_RELEASED";
}
```

### 3.4 직렬화 방식: JSON (Jackson)

| 방식 | 장점 | 단점 | 선택 |
|---|---|---|---|
| JSON | 사람이 읽기 쉬움, Spring Boot 기본 지원, 디버깅 용이 | 바이나리 대비 크기 큼 | **✅ 선택** |
| Avro | 스키마 강제, 크기 효율 | Schema Registry 추가 인프라 필요 | 프로덕션 확장 시 |
| Protobuf | 바이나리 효율 최상 | 생태계 호환성 낮음 | 해당 없음 |

> **선택 이유**: 학습 단계에서는 JSON이 디버깅과 Kafka UI 조회에 최적. Avro는 프로덕션 확장 시 도입.

---

## 4. Spring Boot Kafka 설정

### 4.1 Gradle 의존성 (모든 백엔드 서비스 공통)

`build.gradle`에 추가:

```groovy
dependencies {
    // ... 기존 의존성

    // Kafka
    implementation 'org.springframework.kafka:spring-kafka'
    
    // 테스트
    testImplementation 'org.springframework.kafka:spring-kafka-test'
}
```

### 4.2 application-dev.properties (공통 패턴)

각 서비스의 `application-dev.properties`에 추가:

```properties
# ──────────────────────────────────────────
# Kafka Configuration
# ──────────────────────────────────────────
spring.kafka.bootstrap-servers=localhost:9092

# Producer 설정
spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer
spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JsonSerializer
spring.kafka.producer.acks=all
spring.kafka.producer.retries=3
spring.kafka.producer.properties.enable.idempotence=true
spring.kafka.producer.properties.max.in.flight.requests.per.connection=1

# Consumer 설정
spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer
spring.kafka.consumer.value-deserializer=org.springframework.kafka.support.serializer.JsonDeserializer
spring.kafka.consumer.auto-offset-reset=earliest
spring.kafka.consumer.enable-auto-commit=false
spring.kafka.consumer.properties.spring.json.trusted.packages=jymusic.*
```

**서비스별 Consumer Group ID:**

```properties
# jym-order-service
spring.kafka.consumer.group-id=jym-order-service-group

# jym-payment-service  
spring.kafka.consumer.group-id=jym-payment-service-group

# jym-catalog-service
spring.kafka.consumer.group-id=jym-catalog-service-group
```

### 4.3 설정 파라미터 상세 설명

| 설정 | 값 | 이유 |
|---|---|---|
| `acks=all` | 모든 replica에 기록 확인 후 응답 | 결제/주문 이벤트는 유실 불가, 안전성 최우선 |
| `retries=3` | 일시적 네트워크 에러 시 3회 재시도 | Producer 레벨 재시도 |
| `enable.idempotence=true` | 동일 메시지 중복 발행 방지 | Producer 재시도 시 중복 메시지 차단 |
| `max.in.flight.requests.per.connection=1` | 순서 보장 강화 | 재시도 시 순서 역전 방지 |
| `enable-auto-commit=false` | 수동 offset 커밋 | 메시지 처리 완료 후에만 commit → 처리 보장 |
| `auto-offset-reset=earliest` | 첫 소비 시 가장 오래된 메시지부터 | 서비스 재시작 시 놓친 메시지 없음 |
| `trusted.packages` | `jymusic.*` | JSON 역직렬화 허용 패키지 |

### 4.4 KafkaConfig.java (각 서비스 공통)

```java
@Configuration
public class KafkaConfig {

    /**
     * Kafka Listener Container Factory — 수동 Ack 모드 설정.
     * enable-auto-commit=false와 함께 사용하여
     * 메시지 처리 완료 후에만 offset을 commit합니다.
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory(
            ConsumerFactory<String, Object> consumerFactory) {

        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);

        // 에러 핸들러 — 3회 재시도 후 DLT로 이동
        factory.setCommonErrorHandler(new DefaultErrorHandler(
                new DeadLetterPublishingRecoverer(kafkaTemplate()),
                new FixedBackOff(1000L, 3L)  // 1초 간격, 3회 재시도
        ));

        return factory;
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate(
            ProducerFactory<String, Object> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }
}
```

### 4.5 EventPublisher.java (공통 유틸리티, 각 서비스 동일)

```java
/**
 * Kafka 이벤트 발행 유틸리티.
 * EventEnvelope로 감싸서 일관된 포맷으로 발행합니다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${spring.application.name}")
    private String serviceName;

    /**
     * 이벤트를 Kafka 토픽에 발행합니다.
     *
     * @param topic     대상 토픽명
     * @param key       파티션 분배용 키 (orderId 등)
     * @param eventType 이벤트 타입 (EventTypes 상수)
     * @param payload   이벤트 데이터
     */
    public <T> void publish(String topic, String key, String eventType, T payload) {
        EventEnvelope<T> envelope = EventEnvelope.<T>builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(eventType)
                .version(1)
                .timestamp(LocalDateTime.now())
                .source(serviceName)
                .payload(payload)
                .build();

        kafkaTemplate.send(topic, key, envelope)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("이벤트 발행 실패: topic={}, key={}, type={}",
                                topic, key, eventType, ex);
                    } else {
                        log.info("이벤트 발행 성공: topic={}, key={}, type={}, offset={}",
                                topic, key, eventType,
                                result.getRecordMetadata().offset());
                    }
                });
    }
}
```

### 4.6 토픽명 상수 (각 서비스에 필요한 것만 복사)

```java
public final class KafkaTopics {
    private KafkaTopics() {}

    public static final String ORDER_EVENTS   = "jym.order.events";
    public static final String PAYMENT_EVENTS = "jym.payment.events";
    public static final String STOCK_EVENTS   = "jym.stock.events";
}
```

---

## 5. 패키지 구조 변경

### 각 서비스에 추가되는 Kafka 관련 패키지:

```
src/main/java/jymusic/jym_{service}_service/
├── ... (기존 패키지 유지)
├── config/
│   └── KafkaConfig.java              # [NEW] Kafka 설정
├── event/
│   ├── common/
│   │   ├── EventEnvelope.java        # [NEW] 공통 이벤트 래퍼
│   │   ├── EventTypes.java           # [NEW] 이벤트 타입 상수
│   │   └── KafkaTopics.java          # [NEW] 토픽명 상수
│   ├── payload/
│   │   ├── OrderCreatedPayload.java  # [NEW] 서비스별 이벤트 페이로드 (필요한 것만)
│   │   └── ...
│   ├── publisher/
│   │   └── EventPublisher.java       # [NEW] 이벤트 발행 유틸리티
│   └── consumer/
│       └── XxxEventConsumer.java     # [NEW] 이벤트 소비 핸들러
```

---

## 6. 단위 테스트 — Kafka 연동

### 6.1 `@EmbeddedKafka` 사용

```java
@SpringBootTest
@EmbeddedKafka(
    partitions = 1,
    topics = { KafkaTopics.ORDER_EVENTS, KafkaTopics.PAYMENT_EVENTS },
    brokerProperties = { "listeners=PLAINTEXT://localhost:9093" }
)
class EventPublisherTest {

    @Autowired
    private EventPublisher eventPublisher;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Test
    void publish_shouldSendEventToTopic() {
        // given
        OrderCreatedPayload payload = OrderCreatedPayload.builder()
                .orderId(1L).memberId(42L).totalAmount(new BigDecimal("87000"))
                .items(List.of(/* ... */))
                .build();

        // when
        eventPublisher.publish(
                KafkaTopics.ORDER_EVENTS,
                "1",  // orderId as key
                EventTypes.ORDER_CREATED,
                payload
        );

        // then — Consumer를 설정하여 메시지 수신 확인
        // ConsumerRecord 수신 및 payload 검증
    }
}
```

### 6.2 Consumer 테스트

```java
@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = { KafkaTopics.PAYMENT_EVENTS })
class PaymentEventConsumerTest {

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @MockitoBean
    private OrderService orderService;  // Consumer가 호출할 서비스 mock

    @Test
    void handlePaymentCompleted_shouldUpdateOrderStatusToPaid() throws Exception {
        // given
        EventEnvelope<PaymentCompletedPayload> event = /* ... */;

        // when — 이벤트 발행
        kafkaTemplate.send(KafkaTopics.PAYMENT_EVENTS, "1", event).get();

        // then — 잠시 대기 후 서비스 호출 확인
        Thread.sleep(2000); // 또는 Awaitility 사용
        verify(orderService).updateOrderStatus(1L, OrderStatus.PAID);
    }
}
```

---

## 7. 운영 체크리스트

### 7.1 Kafka UI 접근

- **URL**: http://localhost:8090
- **기능**: 토픽 목록, 메시지 검색, Consumer Group lag 확인, DLT 메시지 확인

### 7.2 유용한 Kafka CLI 명령어

```bash
# 토픽 목록 확인
docker exec jym-kafka kafka-topics --list --bootstrap-server localhost:29092

# 토픽 상세 정보
docker exec jym-kafka kafka-topics --describe --topic jym.order.events --bootstrap-server localhost:29092

# 메시지 확인 (콘솔 Consumer)
docker exec jym-kafka kafka-console-consumer --bootstrap-server localhost:29092 \
  --topic jym.order.events --from-beginning --max-messages 10

# Consumer Group 상태 확인 (Lag 모니터링)
docker exec jym-kafka kafka-consumer-groups --bootstrap-server localhost:29092 \
  --group jym-order-service-group --describe

# DLT 메시지 확인
docker exec jym-kafka kafka-console-consumer --bootstrap-server localhost:29092 \
  --topic jym.order.events.DLT --from-beginning
```

### 7.3 트러블슈팅 가이드

| 증상 | 원인 | 해결 |
|---|---|---|
| `Connection refused: localhost:9092` | Kafka 미기동 | `docker-compose up kafka` |
| `Topic not found` | auto.create.topics=false | `create-topics.sh` 실행 |
| Consumer가 메시지를 못 받음 | trusted.packages 미설정 | `spring.json.trusted.packages=jymusic.*` |
| DLT에 메시지 쌓임 | Consumer 처리 로직 에러 | DLT 메시지 확인 후 버그 수정 |
| Consumer Lag 증가 | 처리 속도 < 발행 속도 | 파티션/인스턴스 수 증가 |

---

_이 문서는 Kafka 인프라 구축 스펙입니다. Saga 패턴 구현은 `02_SAGA_ORDER_PAYMENT_SPEC_KR.md`를 참고하세요._
