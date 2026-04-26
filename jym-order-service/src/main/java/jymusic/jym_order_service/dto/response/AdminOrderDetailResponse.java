package jymusic.jym_order_service.dto.response;

import jymusic.jym_order_service.client.MemberClient;
import jymusic.jym_order_service.domain.entity.Order;
import jymusic.jym_order_service.domain.entity.OrderStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class AdminOrderDetailResponse {
    private Long orderId;
    private Long memberId;
    private String username;
    private String nickname;
    private String email;
    private BigDecimal totalAmount;
    private String status;
    private List<OrderItemDetailResponse> items;
    private List<OrderStatus> allowedNextStatuses;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static AdminOrderDetailResponse of(
            Order order,
            MemberClient.MemberSummary memberSummary,
            List<OrderStatus> allowedNextStatuses
    ) {
        return AdminOrderDetailResponse.builder()
                .orderId(order.getId())
                .memberId(order.getMemberId())
                .username(memberSummary.username())
                .nickname(memberSummary.nickname())
                .email(memberSummary.email())
                .totalAmount(order.getTotalAmount())
                .status(order.getStatus().name())
                .items(order.getItems().stream().map(OrderItemDetailResponse::from).toList())
                .allowedNextStatuses(allowedNextStatuses)
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }
}
