package jymusic.jym_order_service.domain.entity;

public enum OrderStatus {
    PENDING,
    STOCK_RESERVED,
    PAID,
    SHIPPED,
    COMPLETED,
    CANCELLED
}
