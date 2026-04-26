package jymusic.jym_order_service.domain.event;

import jymusic.jym_order_service.domain.entity.OrderStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class OrderStatusChangedDomainEvent {
    private Long orderId;
    private Long memberId;
    private OrderStatus previousStatus;
    private OrderStatus currentStatus;
    private BigDecimal totalAmount;
    private String firstItemTitle;
    private int itemCount;

    public static OrderStatusChangedDomainEvent of(
            Long orderId,
            Long memberId,
            OrderStatus previousStatus,
            OrderStatus currentStatus,
            BigDecimal totalAmount,
            String firstItemTitle,
            int itemCount
    ) {
        return OrderStatusChangedDomainEvent.builder()
                .orderId(orderId)
                .memberId(memberId)
                .previousStatus(previousStatus)
                .currentStatus(currentStatus)
                .totalAmount(totalAmount)
                .firstItemTitle(firstItemTitle)
                .itemCount(itemCount)
                .build();
    }
}
