package jymusic.jym_payment_service.event.common;

public final class KafkaTopics {
    private KafkaTopics() {}

    public static final String ORDER_EVENTS   = "jym.order.events";
    public static final String PAYMENT_EVENTS = "jym.payment.events";
    public static final String STOCK_EVENTS   = "jym.stock.events";
}
