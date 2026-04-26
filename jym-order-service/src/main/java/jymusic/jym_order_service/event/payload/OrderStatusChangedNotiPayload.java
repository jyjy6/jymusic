package jymusic.jym_order_service.event.payload;

import jymusic.jym_order_service.domain.entity.OrderStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
public class OrderStatusChangedNotiPayload {
    private Long orderId;
    private Long memberId;
    private OrderStatus previousStatus;
    private OrderStatus currentStatus;
    private BigDecimal totalAmount;
    private String firstItemTitle;
    private int itemCount;
    private LocalDateTime changedAt;
}
