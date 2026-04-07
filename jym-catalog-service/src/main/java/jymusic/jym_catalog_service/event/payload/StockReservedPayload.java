package jymusic.jym_catalog_service.event.payload;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter @Builder @NoArgsConstructor @AllArgsConstructor
public class StockReservedPayload {
    private Long orderId;
    private List<ReservedItem> reservedItems;
}
