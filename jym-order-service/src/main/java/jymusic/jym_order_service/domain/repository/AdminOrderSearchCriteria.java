package jymusic.jym_order_service.domain.repository;

import jymusic.jym_order_service.domain.entity.OrderStatus;

import java.time.LocalDateTime;
import java.util.List;

public record AdminOrderSearchCriteria(
        List<Long> memberIds,
        String productTitle,
        OrderStatus status,
        List<OrderStatus> statuses,
        LocalDateTime startAt,
        LocalDateTime endAt,
        Long minAmount,
        Long maxAmount
) {
}
