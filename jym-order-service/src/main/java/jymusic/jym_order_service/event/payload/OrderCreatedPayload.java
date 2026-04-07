package jymusic.jym_order_service.event.payload;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/** 주문 생성 완료 — order-service가 발행 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderCreatedPayload {
    private Long orderId;
    private Long memberId;
    private BigDecimal totalAmount;
    private List<OrderItemPayload> items;
}
