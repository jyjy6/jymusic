package jymusic.jym_catalog_service.event.payload;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter @Builder @NoArgsConstructor @AllArgsConstructor
public class OrderCancelledPayload {
    private Long orderId;
    private Long memberId;
    private String reason;
}
