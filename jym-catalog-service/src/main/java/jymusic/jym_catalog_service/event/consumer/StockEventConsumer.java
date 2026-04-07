package jymusic.jym_catalog_service.event.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import jymusic.jym_catalog_service.domain.entity.*;
import jymusic.jym_catalog_service.domain.repository.ProductRepository;
import jymusic.jym_catalog_service.domain.repository.StockReservationRepository;
import jymusic.jym_catalog_service.event.common.EventEnvelope;
import jymusic.jym_catalog_service.event.common.EventTypes;
import jymusic.jym_catalog_service.event.common.KafkaTopics;
import jymusic.jym_catalog_service.event.payload.*;
import jymusic.jym_catalog_service.event.publisher.EventPublisher;
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

    private final ProductRepository productRepository;
    private final StockReservationRepository stockReservationRepository;
    private final EventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    // ──────────────────────────────────────────
    // Order Events 소비 — 재고 예약
    // ──────────────────────────────────────────

    @KafkaListener(
        topics = KafkaTopics.ORDER_EVENTS,
        groupId = "jym-catalog-service-group"
    )
    @Transactional
    public void handleOrderEvent(ConsumerRecord<String, EventEnvelope<?>> record) {
        EventEnvelope<?> envelope = record.value();

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
            Product product = productRepository.findById(item.getProductId())
                    .orElse(null);

            if (product == null || !product.reserveStock(item.getQuantity())) {
                // 재고 부족 → 이미 예약한 항목 롤백 후 실패 이벤트 발행
                rollbackReservedItems(reservedItems);

                eventPublisher.publish(
                        KafkaTopics.STOCK_EVENTS,
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

        // 모든 상품 재고 예약 성공
        eventPublisher.publish(
                KafkaTopics.STOCK_EVENTS,
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
        groupId = "jym-catalog-service-group"
    )
    @Transactional
    @SuppressWarnings("unchecked")
    public void handlePaymentEvent(ConsumerRecord<String, EventEnvelope<?>> record) {
        EventEnvelope<?> envelope = record.value();

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
