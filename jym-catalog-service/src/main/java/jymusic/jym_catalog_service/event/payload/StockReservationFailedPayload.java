package jymusic.jym_catalog_service.event.payload;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter @Builder @NoArgsConstructor @AllArgsConstructor
public class StockReservationFailedPayload {
    private Long orderId;
    private Long failedProductId;
    private String failedProductTitle;
    private int requestedQuantity;
    private int availableStock;
}
