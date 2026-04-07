package jymusic.jym_catalog_service.event.payload;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter @Builder @NoArgsConstructor @AllArgsConstructor
public class OrderItemPayload {
    private Long productId;
    private String productTitle;
    private BigDecimal unitPrice;
    private int quantity;
}
