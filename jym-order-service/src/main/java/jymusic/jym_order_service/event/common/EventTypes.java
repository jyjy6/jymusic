package jymusic.jym_order_service.event.common;

/**
 * Kafka 이벤트 타입 상수.
 * 각 서비스에서 공통으로 사용.
 */
public final class EventTypes {
    private EventTypes() {}

    // Order Events
    public static final String ORDER_CREATED    = "ORDER_CREATED";
    public static final String ORDER_CANCELLED  = "ORDER_CANCELLED";

    // Payment Events
    public static final String PAYMENT_COMPLETED = "PAYMENT_COMPLETED";
    public static final String PAYMENT_FAILED    = "PAYMENT_FAILED";
    public static final String PAYMENT_CANCELLED = "PAYMENT_CANCELLED";

    // Stock Events
    public static final String STOCK_RESERVED          = "STOCK_RESERVED";
    public static final String STOCK_RESERVATION_FAILED = "STOCK_RESERVATION_FAILED";
    public static final String STOCK_RELEASED          = "STOCK_RELEASED";
}
