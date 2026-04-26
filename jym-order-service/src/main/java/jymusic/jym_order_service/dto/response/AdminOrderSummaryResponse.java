package jymusic.jym_order_service.dto.response;

import jymusic.jym_order_service.client.MemberClient;
import jymusic.jym_order_service.domain.entity.Order;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
public class AdminOrderSummaryResponse {
    private Long orderId;
    private Long memberId;
    private String username;
    private String nickname;
    private BigDecimal totalAmount;
    private String status;
    private int itemCount;
    private String firstItemTitle;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static AdminOrderSummaryResponse of(Order order, MemberClient.MemberSummary memberSummary) {
        int itemCount = order.getItems().size();
        String firstItemTitle = itemCount == 0
                ? ""
                : itemCount == 1
                ? order.getItems().get(0).getProductTitle()
                : order.getItems().get(0).getProductTitle() + " 외 " + (itemCount - 1) + "건";

        return AdminOrderSummaryResponse.builder()
                .orderId(order.getId())
                .memberId(order.getMemberId())
                .username(memberSummary.username())
                .nickname(memberSummary.nickname())
                .totalAmount(order.getTotalAmount())
                .status(order.getStatus().name())
                .itemCount(itemCount)
                .firstItemTitle(firstItemTitle)
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }
}
