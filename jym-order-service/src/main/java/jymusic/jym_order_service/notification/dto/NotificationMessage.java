package jymusic.jym_order_service.notification.dto;

import jymusic.jym_order_service.domain.entity.OrderStatus;
import jymusic.jym_order_service.event.payload.OrderStatusChangedNotiPayload;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class NotificationMessage {
    private String type;
    private Long orderId;
    private String title;
    private String message;
    private String status;
    private LocalDateTime occurredAt;

    public static NotificationMessage from(OrderStatusChangedNotiPayload payload) {
        return NotificationMessage.builder()
                .type("ORDER_STATUS_CHANGED")
                .orderId(payload.getOrderId())
                .title(titleFor(payload.getCurrentStatus()))
                .message(buildStatusMessage(payload))
                .status(payload.getCurrentStatus().name())
                .occurredAt(payload.getChangedAt())
                .build();
    }

    private static String buildStatusMessage(OrderStatusChangedNotiPayload payload) {
        String itemLabel = payload.getFirstItemTitle();
        if (payload.getItemCount() > 1) {
            itemLabel += " 외 " + (payload.getItemCount() - 1) + "건";
        }
        return "'" + itemLabel + "' 주문 상태: "
                + payload.getPreviousStatus() + " -> " + payload.getCurrentStatus();
    }

    private static String titleFor(OrderStatus status) {
        return switch (status) {
            case STOCK_RESERVED -> "재고가 예약되었습니다";
            case PAID -> "결제가 완료되었습니다";
            case SHIPPED -> "상품이 발송되었습니다";
            case COMPLETED -> "구매가 확정되었습니다";
            case CANCELLED -> "주문이 취소되었습니다";
            case PENDING -> "주문이 접수되었습니다";
        };
    }
}
