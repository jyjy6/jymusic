package jymusic.jym_order_service.listener;

import jymusic.jym_order_service.domain.entity.OrderStatus;
import jymusic.jym_order_service.domain.event.OrderStatusChangedDomainEvent;
import jymusic.jym_order_service.event.payload.OrderStatusChangedNotiPayload;
import jymusic.jym_order_service.notification.service.NotificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderNotificationListener 단위 테스트")
class OrderNotificationListenerTest {

    @InjectMocks
    private OrderNotificationListener orderNotificationListener;

    @Mock
    private NotificationService notificationService;

    @Test
    @DisplayName("도메인 이벤트 수신 시 알림 payload를 생성해 NotificationService에 전달한다")
    void onStatusChanged_invokesNotificationService() {
        OrderStatusChangedDomainEvent event = OrderStatusChangedDomainEvent.of(
                1L,
                2L,
                OrderStatus.PAID,
                OrderStatus.SHIPPED,
                BigDecimal.valueOf(39000),
                "앨범A",
                2
        );

        orderNotificationListener.onStatusChanged(event);

        ArgumentCaptor<OrderStatusChangedNotiPayload> captor =
                ArgumentCaptor.forClass(OrderStatusChangedNotiPayload.class);
        verify(notificationService).publishOrderStatusChanged(captor.capture());
        OrderStatusChangedNotiPayload payload = captor.getValue();
        assertThat(payload.getOrderId()).isEqualTo(1L);
        assertThat(payload.getMemberId()).isEqualTo(2L);
        assertThat(payload.getPreviousStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(payload.getCurrentStatus()).isEqualTo(OrderStatus.SHIPPED);
    }
}
