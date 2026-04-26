package jymusic.jym_order_service.event.common;

/**
 * Kafka 토픽명 상수.
 */
public final class KafkaTopics {
    private KafkaTopics() {}

    public static final String ORDER_EVENTS   = "jym.order.events";
    public static final String PAYMENT_EVENTS = "jym.payment.events";
    public static final String STOCK_EVENTS   = "jym.stock.events";
    public static final String NOTIFICATION_EVENTS = "jym.notification.events";
}
