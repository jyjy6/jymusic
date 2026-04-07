package jymusic.jym_order_service.event.payload;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 재고 예약 실패 — catalog-service가 발행 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockReservationFailedPayload {
    private Long orderId;
    private Long failedProductId;
    private String failedProductTitle;
    private int requestedQuantity;
    private int availableStock;
}
