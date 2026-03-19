package jymusic.jym_order_service.dto.response;

import jymusic.jym_order_service.domain.entity.Order;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class OrderDetailResponse {
    private Long id;
    private BigDecimal totalAmount;
    private String status;
    private List<OrderItemDetailResponse> items;
    private LocalDateTime createdAt;

    public static OrderDetailResponse from(Order order) {
        return OrderDetailResponse.builder()
                .id(order.getId())
                .totalAmount(order.getTotalAmount())
                .status(order.getStatus().name())
                .items(order.getItems().stream()
                        .map(OrderItemDetailResponse::from)
                        .toList())
                .createdAt(order.getCreatedAt())
                .build();
    }
}
