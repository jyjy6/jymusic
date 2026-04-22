# 03_SSE_NOTIFICATION — 실시간 주문 알림 시스템

> **대상 서비스**: `jym-order-service` (확장)
> **통신 방식**: Server-Sent Events (SSE, `text/event-stream`)
> **역할**: Kafka 이벤트(Order/Stock/Payment) 소비 시, 해당 주문 소유자(유저) 및 관리자에게 실시간 Push 알림을 전송한다.

---

## 1. 설계 배경 — 왜 SSE인가?

| 후보 | 평가 | 선택 여부 |
|---|---|---|
| **SSE** | HTTP/1.1 단방향 스트림, 자동 재연결, JWT Bearer 그대로 사용 가능, Gateway(WebFlux) 프록시 호환 | **채택** |
| WebSocket | 양방향이지만 본 기능은 서버→클라 단방향. 연결·인증 설계가 복잡하며 Gateway 프록시 설정 추가 필요 | 미채택 |
| Polling | 구현은 간단하나 지연·부하 증가, 실시간성 부족 | 미채택 |
| WebPush(FCM) | 브라우저 종료 후에도 동작하나 푸시 프로바이더 의존성·동의 UI 필요 — MVP 범위 초과 | 추후 검토 |

**결론**: 본 기능은 "주문 상태 전이를 로그인한 사용자의 화면에 즉시 반영"이 핵심이므로 **SSE**가 최적.

---

## 2. 아키텍처 — Kafka + In-Memory SSE Registry (하이브리드)

```
┌──────────────────────────────────────────────────────────────────────┐
│  jym-catalog-service / jym-payment-service                           │
│    └─ Kafka produce: jym.stock.events, jym.payment.events            │
└──────────────────────────────────────────────────────────────────────┘
                                  │
                                  ▼
┌──────────────────────────────────────────────────────────────────────┐
│  jym-order-service                                                   │
│                                                                      │
│  ┌──────────────────────────┐   1) 주문 상태 전이                      │
│  │ OrderEventConsumer       │──────────────────┐                    │
│  │ (기존)                    │                   ▼                    │
│  └──────────────────────────┘        ┌────────────────────┐         │
│              │                        │ OrderRepository    │         │
│              │ 2) NotificationPayload │  (DB UPDATE)       │         │
│              │    작성 후 발행         └────────────────────┘         │
│              ▼                                                       │
│  ┌──────────────────────────┐                                        │
│  │ NotificationPublisher    │── Kafka publish ─┐                    │
│  │  (EventPublisher 재사용)  │                   │                    │
│  └──────────────────────────┘                   ▼                    │
│                                    jym.notification.events          │
│                                                 │                    │
│                                                 │ (자기자신 + 타 인스턴스) │
│                                                 ▼                    │
│  ┌────────────────────────────────────────────────────┐             │
│  │ NotificationEventConsumer                          │             │
│  │   groupId = jym-order-service-sse-${INSTANCE_ID}   │             │
│  │   (인스턴스마다 유니크 → fan-out)                     │             │
│  └────────────────────────────────────────────────────┘             │
│              │                                                       │
│              ▼                                                       │
│  ┌──────────────────────────┐                                        │
│  │ SseEmitterRegistry       │  key=memberId  → List<SseEmitter>     │
│  │ (ConcurrentHashMap)      │  key="ADMIN"   → List<SseEmitter>     │
│  └──────────────────────────┘                                        │
│              │ emitter.send()                                        │
└──────────────┼───────────────────────────────────────────────────────┘
               ▼
        ┌──────────────────────────────────────┐
        │ jym-api-gateway (Spring Cloud GW)    │   SSE passthrough
        │   text/event-stream 버퍼링 금지        │
        └──────────────────────────────────────┘
               ▼
        ┌──────────────────────────────────────┐
        │ jym-front (EventSource)              │
        │   /api/v1/notifications/stream       │
        └──────────────────────────────────────┘
```

### 2.1 왜 Kafka를 한 번 더 거치는가? (Notification 전용 토픽)

1. **다중 인스턴스 확장성**: `jym-order-service`를 N개로 스케일아웃해도, 각 인스턴스는 **자기에게 접속된 SSE 클라이언트에만** 전달하면 됨. `groupId`를 인스턴스별 유니크로 설정해 **모든 인스턴스가 전체 이벤트를 수신**하도록 함.
2. **관심사 분리**: DB 트랜잭션(주문 상태 변경)과 Push(네트워크 I/O)를 서로 다른 Consumer 트랜잭션으로 분리 → DB 실패 시 Push 미발생, Push 실패 시 DB 롤백 영향 없음.
3. **다른 채널 확장 여지**: 추후 `EmailNotificationConsumer`, `WebPushConsumer`를 동일 토픽에 붙이기만 하면 됨.

### 2.2 단일 인스턴스 경로(폴백)

MVP 로컬 개발에서는 Kafka 우회가 가능하도록 `app.notification.broadcast-via-kafka=false`(설정값) 일 때는 `OrderEventConsumer`가 **직접 `SseEmitterRegistry.push()`** 를 호출한다.

---

## 3. 알림 이벤트 타입

`event/common/EventTypes.java`에 추가:

```java
// Notification Events (내부 fan-out 전용)
public static final String NOTI_ORDER_STATUS_CHANGED = "NOTI_ORDER_STATUS_CHANGED";
public static final String NOTI_ADMIN_ORDER_CREATED  = "NOTI_ADMIN_ORDER_CREATED";
```

`event/common/KafkaTopics.java`에 추가:

```java
public static final String NOTIFICATION_EVENTS = "jym.notification.events";
```

### 3.1 Payload 스키마

`event/payload/OrderStatusChangedNotiPayload.java`:

```java
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderStatusChangedNotiPayload {
    private Long orderId;
    private Long memberId;          // 알림 대상 (본인)
    private OrderStatus previousStatus;
    private OrderStatus currentStatus;
    private BigDecimal totalAmount;
    private String firstItemTitle;   // "○○○ 외 N건" UI용
    private int itemCount;
    private LocalDateTime changedAt;
}
```

`event/payload/AdminOrderCreatedNotiPayload.java`:

```java
@Getter @Builder @NoArgsConstructor @AllArgsConstructor
public class AdminOrderCreatedNotiPayload {
    private Long orderId;
    private Long memberId;
    private BigDecimal totalAmount;
    private int itemCount;
    private LocalDateTime createdAt;
}
```

---

## 4. 패키지 구조 (신규 파일)

```
src/main/java/jymusic/jym_order_service/
├── notification/
│   ├── controller/
│   │   └── NotificationController.java         # SSE 구독 엔드포인트
│   ├── service/
│   │   ├── NotificationService.java            # 알림 발행 API
│   │   └── SseEmitterRegistry.java             # 연결 관리
│   ├── dto/
│   │   └── NotificationMessage.java            # SSE 전송 DTO
│   └── consumer/
│       └── NotificationEventConsumer.java      # Kafka → SSE 브리지
├── domain/
│   └── event/
│       └── OrderStatusChangedDomainEvent.java  # 신규 — Spring ApplicationEvent (JVM 내부)
├── event/
│   ├── common/
│   │   ├── EventTypes.java                     # (수정) NOTI_* 추가
│   │   └── KafkaTopics.java                    # (수정) NOTIFICATION_EVENTS 추가
│   ├── payload/
│   │   ├── OrderStatusChangedNotiPayload.java  # 신규 (Kafka 페이로드)
│   │   └── AdminOrderCreatedNotiPayload.java   # 신규 (Kafka 페이로드)
│   └── consumer/
│       └── OrderEventConsumer.java             # (수정) 상태 전이 성공 후 ApplicationEventPublisher 로 도메인 이벤트 발행
└── listener/
    └── OrderNotificationListener.java          # 신규 — @TransactionalEventListener(AFTER_COMMIT)
```

### 4.1 두 종류의 "이벤트" 구분

본 프로젝트에서 "이벤트"라는 단어가 두 계층에서 쓰이므로 혼동하지 않아야 한다.

| 구분 | 예시 | 전달 경로 | 용도 |
|---|---|---|---|
| **도메인 이벤트** (Spring ApplicationEvent) | `OrderStatusChangedDomainEvent` | JVM 내부 (ApplicationEventPublisher ↔ Listener) | 트랜잭션 커밋과 후속 작업을 **디커플링**. 같은 프로세스 안에서만 동작 |
| **통합 이벤트** (Kafka) | `NOTI_ORDER_STATUS_CHANGED` 페이로드 | Kafka 토픽 → 타 서비스/타 인스턴스 | 서비스·인스턴스 간 **fan-out** |

`OrderStatusChangedDomainEvent`는 **전자**(JVM 내부용)이며, `OrderStatusChangedNotiPayload`는 **후자**(Kafka 직렬화용)이다. 리스너는 도메인 이벤트를 받아 Kafka 페이로드로 변환하여 발행한다.

---

## 5. 핵심 클래스 설계

### 5.1 `SseEmitterRegistry.java`

```java
@Component
@Slf4j
public class SseEmitterRegistry {

    private static final long DEFAULT_TIMEOUT_MS = 30 * 60 * 1000L; // 30분
    public static final String ADMIN_KEY = "ROLE_ADMIN";

    /** key: memberId(String) or "ROLE_ADMIN", value: 동시 연결된 Emitter 목록(탭 다중) */
    private final Map<String, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter register(String key) {
        SseEmitter emitter = new SseEmitter(DEFAULT_TIMEOUT_MS);
        emitters.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>()).add(emitter);

        Runnable remove = () -> {
            List<SseEmitter> list = emitters.get(key);
            if (list != null) {
                list.remove(emitter);
                if (list.isEmpty()) emitters.remove(key);
            }
        };
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(ex -> remove.run());

        try {
            // 연결 확인용 초기 이벤트
            emitter.send(SseEmitter.event().name("CONNECTED").data("ok"));
        } catch (IOException e) {
            remove.run();
        }
        return emitter;
    }

    public void sendToMember(Long memberId, String eventName, Object data) {
        send(String.valueOf(memberId), eventName, data);
    }

    public void sendToAdmins(String eventName, Object data) {
        send(ADMIN_KEY, eventName, data);
    }

    private void send(String key, String eventName, Object data) {
        List<SseEmitter> list = emitters.get(key);
        if (list == null || list.isEmpty()) return;

        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(data));
            } catch (IOException ex) {
                emitter.completeWithError(ex); // onError → remove
            }
        }
    }

    /** 15초마다 heartbeat — 프록시 타임아웃/연결 유실 감지용 */
    @Scheduled(fixedRate = 15_000)
    public void heartbeat() {
        emitters.forEach((key, list) ->
            list.forEach(em -> {
                try { em.send(SseEmitter.event().name("PING").data("")); }
                catch (IOException e) { em.completeWithError(e); }
            })
        );
    }
}
```

> `@EnableScheduling` 을 `config/AppConfig.java`에 추가해야 한다.

### 5.2 `NotificationController.java`

```java
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final SseEmitterRegistry registry;

    /**
     * 로그인한 사용자의 알림 스트림 구독.
     * 헤더 Last-Event-ID는 본 MVP에서 사용하지 않으나, 추후 재연결 시 누락 이벤트 전송에 활용 가능.
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(@AuthenticationPrincipal String memberId) {
        return registry.register(memberId);
    }

    /** 관리자 전용 알림(신규 주문 발생 등) 구독. */
    @GetMapping(value = "/admin/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public SseEmitter subscribeAdmin() {
        return registry.register(SseEmitterRegistry.ADMIN_KEY);
    }
}
```

### 5.3 `NotificationService.java`

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final EventPublisher eventPublisher;
    private final SseEmitterRegistry registry;

    @Value("${app.notification.broadcast-via-kafka:true}")
    private boolean broadcastViaKafka;

    public void publishOrderStatusChanged(OrderStatusChangedNotiPayload payload) {
        if (broadcastViaKafka) {
            eventPublisher.publish(
                KafkaTopics.NOTIFICATION_EVENTS,
                String.valueOf(payload.getOrderId()),
                EventTypes.NOTI_ORDER_STATUS_CHANGED,
                payload
            );
        } else {
            // 단일 인스턴스 경로 — 즉시 로컬 브로드캐스트
            registry.sendToMember(payload.getMemberId(),
                EventTypes.NOTI_ORDER_STATUS_CHANGED,
                NotificationMessage.from(payload));
        }
    }

    public void publishAdminOrderCreated(AdminOrderCreatedNotiPayload payload) {
        if (broadcastViaKafka) {
            eventPublisher.publish(
                KafkaTopics.NOTIFICATION_EVENTS,
                String.valueOf(payload.getOrderId()),
                EventTypes.NOTI_ADMIN_ORDER_CREATED,
                payload
            );
        } else {
            registry.sendToAdmins(EventTypes.NOTI_ADMIN_ORDER_CREATED,
                NotificationMessage.from(payload));
        }
    }
}
```

### 5.4 `NotificationEventConsumer.java`

```java
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.notification.broadcast-via-kafka", havingValue = "true", matchIfMissing = true)
public class NotificationEventConsumer {

    private final ObjectMapper objectMapper;
    private final SseEmitterRegistry registry;

    /**
     * groupId는 인스턴스마다 유니크해야 한다 (fan-out).
     * ${INSTANCE_ID:...} 은 설정값으로 주입 — docker-compose에서 컨테이너 ID/호스트명 등 사용.
     */
    @KafkaListener(
        topics = KafkaTopics.NOTIFICATION_EVENTS,
        groupId = "jym-order-service-sse-${INSTANCE_ID:${random.uuid}}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handle(ConsumerRecord<String, EventEnvelope<?>> record) {
        EventEnvelope<?> envelope = record.value();
        switch (envelope.getEventType()) {
            case EventTypes.NOTI_ORDER_STATUS_CHANGED -> {
                var p = objectMapper.convertValue(envelope.getPayload(), OrderStatusChangedNotiPayload.class);
                registry.sendToMember(p.getMemberId(), envelope.getEventType(), NotificationMessage.from(p));
            }
            case EventTypes.NOTI_ADMIN_ORDER_CREATED -> {
                var p = objectMapper.convertValue(envelope.getPayload(), AdminOrderCreatedNotiPayload.class);
                registry.sendToAdmins(envelope.getEventType(), NotificationMessage.from(p));
            }
            default -> log.debug("무시: {}", envelope.getEventType());
        }
    }
}
```

### 5.5 `NotificationMessage.java` (SSE 전송 표준 DTO)

```java
@Getter @Builder
public class NotificationMessage {
    private String type;          // "ORDER_STATUS_CHANGED" / "ADMIN_ORDER_CREATED"
    private Long orderId;
    private String title;         // UI 표시용 제목 — "결제가 완료되었습니다"
    private String message;       // UI 표시용 본문
    private String status;        // 현재 OrderStatus
    private LocalDateTime occurredAt;

    public static NotificationMessage from(OrderStatusChangedNotiPayload p) {
        return NotificationMessage.builder()
            .type("ORDER_STATUS_CHANGED")
            .orderId(p.getOrderId())
            .status(p.getCurrentStatus().name())
            .title(titleFor(p.getCurrentStatus()))
            .message(String.format("'%s%s' 주문 상태: %s → %s",
                p.getFirstItemTitle(),
                p.getItemCount() > 1 ? " 외 " + (p.getItemCount()-1) + "건" : "",
                p.getPreviousStatus(), p.getCurrentStatus()))
            .occurredAt(p.getChangedAt())
            .build();
    }

    public static NotificationMessage from(AdminOrderCreatedNotiPayload p) {
        return NotificationMessage.builder()
            .type("ADMIN_ORDER_CREATED")
            .orderId(p.getOrderId())
            .title("신규 주문이 들어왔습니다")
            .message(String.format("주문 #%d — %,d원 (%d개 상품)",
                p.getOrderId(), p.getTotalAmount().longValue(), p.getItemCount()))
            .occurredAt(p.getCreatedAt())
            .build();
    }

    private static String titleFor(OrderStatus s) {
        return switch (s) {
            case STOCK_RESERVED -> "재고가 예약되었습니다";
            case PAID           -> "결제가 완료되었습니다";
            case SHIPPED        -> "상품이 발송되었습니다";
            case COMPLETED      -> "구매가 확정되었습니다";
            case CANCELLED      -> "주문이 취소되었습니다";
            case PENDING        -> "주문이 접수되었습니다";
        };
    }
}
```

### 5.6 `OrderStatusChangedDomainEvent.java` — 도메인 이벤트 정의

**위치**: `domain/event/OrderStatusChangedDomainEvent.java`

**무엇인가**: Spring의 `ApplicationEventPublisher`/`ApplicationEvent` 메커니즘을 타는 **JVM 내부 이벤트**. "주문 상태가 바뀌었다"는 사실을 상태 변경 주체(Kafka Consumer / 관리자 API)와, 후속 처리자(Push 알림 리스너)가 서로를 몰라도 되도록 매개하는 역할.

**왜 필요한가**:
1. **트랜잭션 게이트**: `@TransactionalEventListener(AFTER_COMMIT)` 와 결합해 **DB 커밋 확정 이후에만** Push 알림을 발송. 롤백 시 거짓 알림 방지.
2. **디커플링**: `OrderEventConsumer` / `AdminOrderService` 는 "상태 변경"에만 집중하고, "누구에게 어떻게 알릴지"는 리스너가 담당.
3. **확장 여지**: 이메일 발송, 감사 로그 적재, 포인트 적립 등 추가 후속 작업이 생기면 **같은 이벤트에 리스너만 추가**하면 됨.

```java
package jymusic.jym_order_service.domain.event;

import jymusic.jym_order_service.domain.entity.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 주문 상태 전이가 성공적으로 이루어졌음을 알리는 도메인 이벤트.
 * Spring ApplicationEventPublisher 로 발행되며, 동일 JVM 내부에서만 전파된다.
 *
 * 발행처:
 *   - event.consumer.OrderEventConsumer       (Kafka 이벤트 소비로 인한 자동 전이)
 *   - admin.service.AdminOrderService         (관리자 수동 전이)
 *
 * 수신처:
 *   - listener.OrderNotificationListener      (@TransactionalEventListener AFTER_COMMIT)
 *
 * Java record 를 사용해 불변성을 보장한다.
 */
public record OrderStatusChangedDomainEvent(
        Long orderId,
        Long memberId,
        OrderStatus previous,
        OrderStatus current,
        BigDecimal totalAmount,
        String firstItemTitle,
        int itemCount,
        LocalDateTime changedAt
) {
    public static OrderStatusChangedDomainEvent of(
            Long orderId, Long memberId,
            OrderStatus previous, OrderStatus current,
            BigDecimal totalAmount, String firstItemTitle, int itemCount) {
        return new OrderStatusChangedDomainEvent(
                orderId, memberId, previous, current,
                totalAmount, firstItemTitle, itemCount, LocalDateTime.now());
    }
}
```

### 5.7 `OrderNotificationListener.java` — 트랜잭션 커밋 후 Push 트리거

**위치**: `listener/OrderNotificationListener.java`

```java
package jymusic.jym_order_service.listener;

import jymusic.jym_order_service.domain.event.OrderStatusChangedDomainEvent;
import jymusic.jym_order_service.event.payload.OrderStatusChangedNotiPayload;
import jymusic.jym_order_service.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderNotificationListener {

    private final NotificationService notificationService;

    /**
     * DB 트랜잭션이 커밋된 후에만 Push 이벤트를 발행한다.
     * 롤백 시에는 이 메서드가 호출되지 않으므로 거짓 알림이 발생하지 않는다.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStatusChanged(OrderStatusChangedDomainEvent ev) {
        notificationService.publishOrderStatusChanged(
            OrderStatusChangedNotiPayload.builder()
                .orderId(ev.orderId())
                .memberId(ev.memberId())
                .previousStatus(ev.previous())
                .currentStatus(ev.current())
                .totalAmount(ev.totalAmount())
                .firstItemTitle(ev.firstItemTitle())
                .itemCount(ev.itemCount())
                .changedAt(ev.changedAt())
                .build()
        );
    }
}
```

### 5.8 기존 `OrderEventConsumer` 수정 지점

상태 전이 **성공 직후** `ApplicationEventPublisher` 로 `OrderStatusChangedDomainEvent` 를 발행한다. `NotificationService` 를 직접 호출하지 않는 이유는 **트랜잭션 커밋 이전에 Push가 나가는 것을 방지**하기 위함.

```java
// 기존 필드에 추가
private final ApplicationEventPublisher applicationEventPublisher;

// 상태 전이 성공 후 (예: handlePaymentCompleted 내부)
OrderStatus previous = order.getStatus();
order.transitionTo(OrderStatus.PAID);
orderRepository.save(order);

applicationEventPublisher.publishEvent(
    OrderStatusChangedDomainEvent.of(
        order.getId(), order.getMemberId(),
        previous, order.getStatus(),
        order.getTotalAmount(),
        order.getItems().isEmpty() ? "" : order.getItems().get(0).getProductTitle(),
        order.getItems().size()
    )
);
```

> **실행 타임라인**:
> ```
> 1. OrderEventConsumer.handlePaymentEvent (트랜잭션 시작)
> 2.   → order.transitionTo(PAID)              [메모리 변경]
> 3.   → orderRepository.save(order)            [DB UPDATE, 아직 미커밋]
> 4.   → applicationEventPublisher.publishEvent [이벤트 큐에 적재만 됨]
> 5. 메서드 리턴 → 트랜잭션 커밋
> 6. ★ AFTER_COMMIT 단계에서 OrderNotificationListener.onStatusChanged 호출
> 7.   → NotificationService.publishOrderStatusChanged → Kafka 발행
> 8.   → NotificationEventConsumer 가 소비 → SseEmitter.send
> ```
> 만약 3번에서 DB 오류로 롤백되면 6번은 절대 호출되지 않는다 → 데이터 정합성 보장.

---

## 6. OpenAPI Specification 추가분

`sdd-spec-docs/feature/jym-order-service/openapi.yaml` 에 다음을 병합한다.

```yaml
paths:
  /notifications/stream:
    get:
      tags: [알림]
      summary: 내 주문 알림 실시간 스트림 구독 (SSE)
      description: |
        `text/event-stream` 으로 주문 상태 변경 이벤트를 푸시한다.
        - 이벤트명: `CONNECTED`, `PING`, `NOTI_ORDER_STATUS_CHANGED`
        - 타임아웃: 서버 30분 (클라이언트는 자동 재연결)
      security:
        - bearerAuth: []
      responses:
        '200':
          description: SSE 스트림 연결
          content:
            text/event-stream:
              schema:
                $ref: '#/components/schemas/NotificationMessage'
        '401':
          $ref: '#/components/responses/ErrorResponse'

  /notifications/admin/stream:
    get:
      tags: [알림]
      summary: 운영자용 신규 주문 알림 스트림 구독 (SSE, ADMIN)
      security:
        - bearerAuth: []
      responses:
        '200':
          description: SSE 스트림 연결
        '403':
          $ref: '#/components/responses/ErrorResponse'

components:
  schemas:
    NotificationMessage:
      type: object
      properties:
        type:
          type: string
          enum: [ORDER_STATUS_CHANGED, ADMIN_ORDER_CREATED]
        orderId:     { type: integer, format: int64 }
        title:       { type: string }
        message:     { type: string }
        status:
          type: string
          enum: [PENDING, STOCK_RESERVED, PAID, SHIPPED, COMPLETED, CANCELLED]
        occurredAt:  { type: string, format: date-time }
```

---

## 7. Gateway 라우팅 및 설정

`jym-api-gateway/src/main/java/jymusic/jym_api_gateway/config/GatewayRouteConfig.java`:

```java
.route("order_service", r -> r
    .path("/api/v1/cart/**", "/api/v1/orders/**", "/api/v1/notifications/**")
    .uri(orderUrl))
```

`application.properties` (Gateway):

```properties
# SSE 응답 버퍼링 비활성 (기본값이지만 명시)
spring.cloud.gateway.httpclient.response-timeout=-1
# Reactor Netty 커넥션 풀이 SSE 스트림을 지속 유지하도록 keepalive
spring.cloud.gateway.httpclient.pool.max-idle-time=60s
```

---

## 8. 보안

| 항목 | 정책 |
|---|---|
| 인증 방식 | 기존 JWT + `X-User-Id` / `X-User-Role` 헤더 주입 (Gateway → Service) |
| 유저 스트림 | 인증된 사용자만 구독 가능. `memberId`는 본인 것만 — 별도 쿼리 파라미터 받지 않음 |
| 운영자 스트림 | `@PreAuthorize("hasAuthority('ROLE_ADMIN')")` |
| CORS | 기존 Gateway CORS 정책 상속 |
| 토큰 만료 | Access Token 만료 시 Gateway가 401 반환 → 클라이언트는 재로그인 후 재구독 |

> **주의**: `EventSource`는 커스텀 헤더를 지원하지 않는다. Gateway가 쿠키 기반 인증도 허용하거나, URL 쿼리 `?access_token=...` 방식을 별도 엔드포인트로 허용해야 한다. 본 MVP에서는 **쿠키(HttpOnly refresh + 단명 access)** 또는 `polyfill(event-source-polyfill)` 로 Bearer 헤더 주입 (프론트 스펙에서 상세 규정).

---

## 9. 설정 (application-dev.properties)

```properties
# Notification SSE
app.notification.broadcast-via-kafka=true
# 인스턴스 식별자 — docker-compose에서 주입
INSTANCE_ID=${HOSTNAME:default-instance}

# Kafka — 기존 설정 상속 (jym.notification.events 토픽 자동 생성)
spring.kafka.consumer.auto-offset-reset=latest
```

---

## 10. 운영 고려사항

| 항목 | 정책 |
|---|---|
| **멱등성** | `eventId`(UUID) 기반 중복 수신 방지는 현재 미구현. Emitter 다수 전송은 UI 측에서 `orderId+status+occurredAt` 중복 제거 |
| **백프레셔** | Emitter 전송 실패 시 즉시 해당 Emitter 제거 (좀비 연결 방지) |
| **동시 탭** | 사용자 한 명이 여러 탭을 열면 `CopyOnWriteArrayList`로 N개 Emitter 보유 |
| **재연결** | 브라우저 `EventSource`는 연결 끊김 시 기본 3초 뒤 자동 재연결. MVP는 누락 이벤트 보정 없음 → 재연결 직후 클라이언트가 `/api/v1/orders` 재조회로 동기화 |
| **모니터링** | `emitters.size()` 를 Actuator 커스텀 게이지로 노출 (옵션) |
| **부하 한계** | 인스턴스당 동시 연결 약 1만 개까지 Spring MVC(Tomcat) 기준 권장. 초과 시 WebFlux로 전환 혹은 Redis Streams 도입 검토 |

---

## 11. 단위 테스트 가이드

### `SseEmitterRegistryTest`

| 테스트 메서드 | 검증 내용 |
|---|---|
| `register_returnsEmitter_andStoresInMap` | 등록 시 Map에 저장 |
| `register_removesOnCompletion` | 완료 콜백 호출 시 Map에서 제거 |
| `sendToMember_deliversToAllEmittersForSameKey` | 같은 memberId 다중 탭 모두 전송 |
| `sendToMember_skipsWhenNoSubscribers` | 미연결 memberId는 무시 |
| `send_removesEmitter_whenIOExceptionThrown` | 전송 실패 시 자동 제거 |

### `NotificationServiceTest`

| 테스트 메서드 | 검증 내용 |
|---|---|
| `publishOrderStatusChanged_viaKafka_whenEnabled` | Kafka 경로 — EventPublisher 호출 |
| `publishOrderStatusChanged_viaLocal_whenDisabled` | 로컬 경로 — registry.sendToMember 호출 |
| `publishAdminOrderCreated_sendsToAdminKey` | 관리자 키로 전송 |

### `NotificationEventConsumerTest`

| 테스트 메서드 | 검증 내용 |
|---|---|
| `handle_routesOrderStatusChanged_toMember` | memberId로 전달 |
| `handle_routesAdminOrderCreated_toAdmins` | ADMIN 키로 전달 |
| `handle_ignoresUnknownEventType` | 미지원 타입 로그만 기록 |

### `OrderNotificationListenerTest`

| 테스트 메서드 | 검증 내용 |
|---|---|
| `onStatusChanged_invokesNotificationService_afterCommit` | `@TransactionalEventListener` 동작 검증 (TestTransaction 또는 `@SpringBootTest` 슬라이스) |

**커버리지 목표**: 알림 관련 클래스 70% 이상 (헌법 §2.3).

---

## 12. 구현 순서 (점진 배포)

1. `EventTypes`, `KafkaTopics`, `NotificationMessage`, Payload DTO 작성 → 빌드 확인
2. `SseEmitterRegistry` + `NotificationController` 작성 → 로컬에서 `curl -N` 으로 수동 검증
3. `NotificationService` + `OrderNotificationListener` + `OrderEventConsumer` 수정 → **로컬 브로드캐스트 모드**(`broadcast-via-kafka=false`)로 E2E 검증
4. `NotificationEventConsumer` 추가 및 Kafka 경로 스위치 → 다중 인스턴스 fan-out 검증
5. Gateway 라우팅 추가 후 프론트 연동
