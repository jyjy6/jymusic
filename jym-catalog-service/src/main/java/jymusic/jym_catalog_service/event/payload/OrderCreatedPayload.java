package jymusic.jym_catalog_service.event.payload;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Getter @Builder @NoArgsConstructor @AllArgsConstructor
public class OrderCreatedPayload {
    private Long orderId;
    private Long memberId;
    private BigDecimal totalAmount;
    private List<OrderItemPayload> items;
}
