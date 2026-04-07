package jymusic.jym_order_service.event.payload;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/** 재고 예약 성공 — catalog-service가 발행 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockReservedPayload {
    private Long orderId;
    private List<ReservedItem> reservedItems;
}
