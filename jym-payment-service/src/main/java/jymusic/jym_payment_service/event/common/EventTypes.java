package jymusic.jym_payment_service.event.common;

public final class EventTypes {
    private EventTypes() {}

    public static final String ORDER_CREATED     = "ORDER_CREATED";
    public static final String ORDER_CANCELLED   = "ORDER_CANCELLED";
    public static final String PAYMENT_COMPLETED = "PAYMENT_COMPLETED";
    public static final String PAYMENT_FAILED    = "PAYMENT_FAILED";
    public static final String PAYMENT_CANCELLED = "PAYMENT_CANCELLED";
    public static final String STOCK_RESERVED          = "STOCK_RESERVED";
    public static final String STOCK_RESERVATION_FAILED = "STOCK_RESERVATION_FAILED";
    public static final String STOCK_RELEASED          = "STOCK_RELEASED";
}
