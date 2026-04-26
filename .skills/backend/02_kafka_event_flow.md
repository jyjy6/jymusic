# Kafka 이벤트 발행/소비 구현

> **참조**: `.skills/_common/00_project_context.md`

## 개요
서비스 간 비동기 통신을 위한 Kafka 이벤트 구현 표준. Choreography Saga 패턴, `EventEnvelope<T>` 래퍼 사용.

## 입력
- 이벤트명, 발행/소비 서비스, 토픽명, 페이로드 필드, 보상 트랜잭션 필요 여부

## 절차

### Step 1: EventTypes 상수 추가 (발행/소비 양쪽)
```java
public static final String REVIEW_CREATED = "REVIEW_CREATED";
```

### Step 2: KafkaTopics 상수 추가 (새 토픽인 경우)
```java
public static final String REVIEW_EVENTS = "jym.review.events";
```

### Step 3: 페이로드 DTO (발행/소비 양쪽 독립 생성)
```java
@Getter @Builder @NoArgsConstructor @AllArgsConstructor
public class ReviewCreatedPayload {
    private Long reviewId;
    private Long productId;
    private Long memberId;
    private int rating;
}
```

### Step 4: 이벤트 발행
```java
eventPublisher.publish(
    KafkaTopics.REVIEW_EVENTS,
    saved.getProductId().toString(),  // Kafka key
    EventTypes.REVIEW_CREATED,
    payload
);
```
> EventPublisher가 EventEnvelope로 자동 래핑 (eventId, timestamp, source 포함)

### Step 5: 이벤트 소비
```java
@KafkaListener(
    topics = KafkaTopics.REVIEW_EVENTS,
    groupId = "jym-{서비스명}-group",
    containerFactory = "kafkaListenerContainerFactory"
)
@Transactional  // 최상위 메서드에 배치 (AOP Proxy self-invocation 방지)
public void handleReviewEvent(ConsumerRecord<String, EventEnvelope<?>> record) {
    EventEnvelope<?> envelope = record.value();
    switch (envelope.getEventType()) {
        case EventTypes.REVIEW_CREATED -> handleReviewCreated(envelope);
        default -> log.warn("처리하지 않는 이벤트: {}", envelope.getEventType());
    }
}

private void handleReviewCreated(EventEnvelope<?> envelope) {
    var payload = objectMapper.convertValue(envelope.getPayload(), ReviewCreatedPayload.class);
    // 멱등성 체크 후 비즈니스 로직
}
```

## Saga 보상 트랜잭션 패턴
```
[정상] A → CREATED → B → COMPLETED → A 상태 업데이트
[실패] B 실패 → FAILED 이벤트 → A 롤백, C 원복
```
핵심: 멱등성(상태 체크), 순서 보장(같은 key→같은 파티션), DLT 실패 격리

## KafkaConfig (이미 존재)
- DeadLetterPublishingRecoverer → `{토픽}.DLT` 자동 전달
- FixedBackOff(1000L, 3L) → 1초 간격 3회 재시도
- 역직렬화 실패 시 skip 처리

## 체크리스트
- [ ] EventTypes, KafkaTopics 상수 양쪽 서비스에 추가
- [ ] 페이로드 DTO 양쪽 독립 생성 (`@Getter @Builder @NoArgsConstructor @AllArgsConstructor`)
- [ ] groupId가 서비스별 고유값
- [ ] @Transactional 최상위 리스너 메서드에 적용
- [ ] 멱등성 체크 포함
- [ ] 보상 트랜잭션 설계 (필요 시)

## 관련 스킬
- `infra/02_kafka_topic_setup.md`, `backend/01_new_api_endpoint.md`
