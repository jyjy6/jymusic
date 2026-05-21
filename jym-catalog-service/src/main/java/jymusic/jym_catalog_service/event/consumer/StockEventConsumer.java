package jymusic.jym_catalog_service.event.consumer;

import tools.jackson.databind.ObjectMapper;
import jymusic.jym_catalog_service.domain.entity.*;
import jymusic.jym_catalog_service.domain.repository.ProductRepository;
import jymusic.jym_catalog_service.domain.repository.StockReservationRepository;
import jymusic.jym_catalog_service.event.common.EventEnvelope;
import jymusic.jym_catalog_service.event.common.EventTypes;
import jymusic.jym_catalog_service.event.common.KafkaTopics;
import jymusic.jym_catalog_service.event.inbox.InboxIdempotencyGuard;
import jymusic.jym_catalog_service.event.outbox.OutboxEventRecorder;
import jymusic.jym_catalog_service.event.payload.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 주문/결제 이벤트를 소비하여 재고를 관리합니다.
 *
 * 소비 이벤트:
 *   - ORDER_CREATED     (from order-service) → 재고 예약 (차감)
 *   - ORDER_CANCELLED   (from order-service) → 재고 복원
 *   - PAYMENT_FAILED    (from payment-service) → 재고 복원
 *   - PAYMENT_CANCELLED (from payment-service) → 재고 복원
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StockEventConsumer {

    private static final String CONSUMER_GROUP = "jym-catalog-service-group";

    private final ProductRepository productRepository;
    private final StockReservationRepository stockReservationRepository;
    private final OutboxEventRecorder outboxEventRecorder;
    private final InboxIdempotencyGuard inboxIdempotencyGuard;
    private final ObjectMapper objectMapper;

    // ──────────────────────────────────────────
    // Order Events 소비 — 재고 예약
    // ──────────────────────────────────────────

    @KafkaListener(
        topics = KafkaTopics.ORDER_EVENTS,
        groupId = CONSUMER_GROUP
    )
    @Transactional
    public void handleOrderEvent(ConsumerRecord<String, EventEnvelope<?>> record) {
        EventEnvelope<?> envelope = record.value();

        if (!inboxIdempotencyGuard.tryMarkProcessed(
                envelope.getEventId(), CONSUMER_GROUP, envelope.getEventType(), record)) {
            return;
        }

        switch (envelope.getEventType()) {
            case EventTypes.ORDER_CREATED -> handleOrderCreated(envelope);
            case EventTypes.ORDER_CANCELLED -> handleOrderCancelled(envelope);
            default -> log.debug("catalog-service가 처리하지 않는 주문 이벤트: {}",
                    envelope.getEventType());
        }
    }

    private void handleOrderCreated(EventEnvelope<?> envelope) {
        OrderCreatedPayload payload = objectMapper.convertValue(
                envelope.getPayload(), OrderCreatedPayload.class);

        log.info("재고 예약 시작: orderId={}", payload.getOrderId());

        // 멱등성 체크 — 이미 예약된 주문이면 skip
        if (stockReservationRepository.findByOrderId(payload.getOrderId()).isPresent()) {
            log.info("이미 재고 예약된 주문, skip: orderId={}", payload.getOrderId());
            return;
        }

        List<ReservedItem> reservedItems = new ArrayList<>();

        for (OrderItemPayload item : payload.getItems()) {
            // ========================================================================
            // [추후 변경 예정] 비관적 락(Pessimistic Lock) 적용
            // ========================================================================
            // TODO: 대량의 트래픽(예: 티켓팅, 타임세일) 시 동시성 문제(Lost Update)를 방지하기 위해 
            // 아래의 일반 findById 대신 비관적 락이 걸린 쿼리를 사용해야 합니다.
            //
            // Product product = productRepository.findByIdWithPessimisticLock(item.getProductId())
            //         .orElse(null);
            Product product = productRepository.findById(item.getProductId())
                    .orElse(null);

            if (product == null || !product.reserveStock(item.getQuantity())) {
                // 재고 부족 → 이미 예약한 항목 롤백 후 실패 이벤트를 Outbox 에 기록.
                // (재고 변경 + outbox INSERT 가 같은 트랜잭션에 묶여 정합성이 보장됨)
                rollbackReservedItems(reservedItems);

                outboxEventRecorder.record(
                        KafkaTopics.STOCK_EVENTS,
                        "STOCK",
                        payload.getOrderId().toString(),
                        EventTypes.STOCK_RESERVATION_FAILED,
                        StockReservationFailedPayload.builder()
                                .orderId(payload.getOrderId())
                                .failedProductId(item.getProductId())
                                .failedProductTitle(item.getProductTitle())
                                .requestedQuantity(item.getQuantity())
                                .availableStock(product != null ? product.getStockQuantity() : 0)
                                .build()
                );
                log.warn("재고 예약 실패: orderId={}, productId={}",
                        payload.getOrderId(), item.getProductId());
                return;
            }

            productRepository.save(product);
            reservedItems.add(ReservedItem.builder()
                    .productId(item.getProductId())
                    .quantity(item.getQuantity())
                    .remainingStock(product.getStockQuantity())
                    .build());
        }

        // 재고 예약 기록 저장 (보상 트랜잭션용)
        StockReservation reservation = StockReservation.builder()
                .orderId(payload.getOrderId())
                .status(StockReservationStatus.RESERVED)
                .build();
        for (ReservedItem ri : reservedItems) {
            reservation.getItems().add(StockReservationItem.builder()
                    .stockReservation(reservation)
                    .productId(ri.getProductId())
                    .quantity(ri.getQuantity())
                    .build());
        }
        stockReservationRepository.save(reservation);

        // 모든 상품 재고 예약 성공 — Outbox 에 기록 (같은 트랜잭션)
        outboxEventRecorder.record(
                KafkaTopics.STOCK_EVENTS,
                "STOCK",
                payload.getOrderId().toString(),
                EventTypes.STOCK_RESERVED,
                StockReservedPayload.builder()
                        .orderId(payload.getOrderId())
                        .reservedItems(reservedItems)
                        .build()
        );
        log.info("재고 예약 성공: orderId={}, items={}", payload.getOrderId(), reservedItems.size());
    }

    /**
     * 부분 예약 롤백 — 여러 상품 중 하나라도 재고 부족이면
     * 이미 예약 처리한 상품들의 재고를 복원합니다.
     */
    private void rollbackReservedItems(List<ReservedItem> reservedItems) {
        for (ReservedItem reserved : reservedItems) {
            productRepository.findById(reserved.getProductId()).ifPresent(product -> {
                product.releaseStock(reserved.getQuantity());
                productRepository.save(product);
            });
        }
    }

    private void handleOrderCancelled(EventEnvelope<?> envelope) {
        OrderCancelledPayload payload = objectMapper.convertValue(
                envelope.getPayload(), OrderCancelledPayload.class);

        log.info("주문 취소로 재고 복원 시작: orderId={}, reason={}",
                payload.getOrderId(), payload.getReason());

        releaseStockByOrderId(payload.getOrderId());
    }

    // ──────────────────────────────────────────
    // Payment Events 소비 — 재고 복원 (보상 트랜잭션)
    // ──────────────────────────────────────────

    @KafkaListener(
        topics = KafkaTopics.PAYMENT_EVENTS,
        groupId = CONSUMER_GROUP
    )
    @Transactional
    @SuppressWarnings("unchecked")
    public void handlePaymentEvent(ConsumerRecord<String, EventEnvelope<?>> record) {
        EventEnvelope<?> envelope = record.value();

        if (!inboxIdempotencyGuard.tryMarkProcessed(
                envelope.getEventId(), CONSUMER_GROUP, envelope.getEventType(), record)) {
            return;
        }

        switch (envelope.getEventType()) {
            case EventTypes.PAYMENT_FAILED -> handleStockRelease(envelope, "결제 실패");
            case EventTypes.PAYMENT_CANCELLED -> handleStockRelease(envelope, "결제 취소");
            case EventTypes.PAYMENT_COMPLETED -> {
                // 결제 성공 — catalog에서 추가 작업 없음 (재고는 이미 예약 시 차감됨)
                log.debug("결제 완료 이벤트 수신 (catalog 처리 없음): orderId={}", record.key());
            }
            default -> log.debug("catalog-service가 처리하지 않는 결제 이벤트: {}",
                    envelope.getEventType());
        }
    }

    private void handleStockRelease(EventEnvelope<?> envelope, String reason) {
        Map<String, Object> payloadMap = objectMapper.convertValue(envelope.getPayload(), Map.class);
        Long orderId = Long.valueOf(payloadMap.get("orderId").toString());

        log.info("재고 복원 시작: orderId={}, 사유={}", orderId, reason);
        releaseStockByOrderId(orderId);
    }

    /**
     * orderId 기반 재고 복원 — StockReservation 기록을 조회하여 정확한 수량만큼 복원.
     */
    private void releaseStockByOrderId(Long orderId) {
        stockReservationRepository.findByOrderId(orderId).ifPresentOrElse(
            reservation -> {
                if (reservation.getStatus() == StockReservationStatus.RELEASED) {
                    log.info("이미 복원된 재고, skip: orderId={}", orderId);
                    return;
                }

                for (StockReservationItem item : reservation.getItems()) {
                    productRepository.findById(item.getProductId()).ifPresent(product -> {
                        product.releaseStock(item.getQuantity());
                        productRepository.save(product);
                    });
                }
                reservation.release();
                stockReservationRepository.save(reservation);
                log.info("재고 복원 완료: orderId={}", orderId);
            },
            () -> log.warn("재고 예약 기록 없음, 복원 skip: orderId={}", orderId)
        );
    }
}




/**
 * 코드의 잠재적 위험 요소 (Review)
 * Saga 패턴 구현 관점에서 몇 가지 주의할 점이 보입니다:
 *
 * 원자성(Atomicity) 문제:
 * rollbackReservedItems 내부에서도 productRepository.save()를 호출합니다. 만약 여기서 에러가 나면 진짜 복잡해집니다. 사실 @Transactional 환경이라면, 수동 롤백보다는 성공한 것들만 모아서 최종적으로 한 번에 저장하거나, 실패 시 비즈니스 예외를 던지고 TransactionPhase.AFTER_ROLLBACK에서 이벤트를 발행하는 것이 더 깔끔할 수 있습니다.
 *
 * 비관적 락(Pessimistic Lock)의 부재:
 * 주석에도 써두셨듯이, findById는 락을 걸지 않습니다.
 *
 * Thread A가 상품 재고 1개를 확인하고 0으로 만듦 (아직 save 전).
 *
 * Thread B가 동시에 상품 재고 1개를 확인하고 0으로 만듦.
 *
 * 결과적으로 재고는 1개였는데 2개가 예약되는 Lost Update 문제가 발생합니다. 실전에서는 반드시 LockModeType.PESSIMISTIC_WRITE가 필요합니다.
 *
 *
 *
 * 방법 A: @TransactionalEventListener 사용 (추천)
 * Spring에서 제공하는 기능을 사용하면, DB는 롤백하되 이벤트 발행은 롤백 이후에 따로 실행할 수 있습니다.
 * // 1. 서비스 로직에서는 예외만 던짐
 * if (product == null || !product.reserveStock(item.getQuantity())) {
 *     throw new StockShortageException(payload.getOrderId()); // 예외 발생! DB는 롤백됨
 * }
 *
 * // 2. 리스너에서 롤백된 후에 이벤트를 발행
 * @TransactionalEventListener(phase = TransactionPhase.AFTER_ROLLBACK)
 * public void handleRollback(StockShortageException ex) {
 *     // DB는 깨끗하게 롤백된 상태에서 실패 이벤트만 Kafka로 전송
 *     eventPublisher.publish(KafkaTopics.STOCK_EVENTS, ... STOCK_RESERVATION_FAILED);
 * }
 *
 *
 *
 *

 *
 * */








