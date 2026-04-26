package jymusic.jym_order_service.service.admin;

import jymusic.jym_order_service.client.MemberClient;
import jymusic.jym_order_service.common.GlobalErrorHandler.GlobalException;
import jymusic.jym_order_service.domain.entity.Order;
import jymusic.jym_order_service.domain.entity.OrderStatus;
import jymusic.jym_order_service.domain.event.OrderStatusChangedDomainEvent;
import jymusic.jym_order_service.domain.repository.OrderRepository;
import jymusic.jym_order_service.dto.request.AdminOrderSearchRequest;
import jymusic.jym_order_service.dto.request.AdminStatusUpdateRequest;
import jymusic.jym_order_service.dto.response.AdminOrderDetailResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminOrderService 단위 테스트")
class AdminOrderServiceTest {

    @InjectMocks
    private AdminOrderService adminOrderService;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private MemberClient memberClient;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Test
    @DisplayName("keyword 검색 결과가 비어있으면 repository 조회 없이 빈 페이지를 반환한다")
    void search_withKeywordNoMatch_returnsEmptyPageWithoutRepositoryCall() {
        AdminOrderSearchRequest request = new AdminOrderSearchRequest();
        request.setKeyword("nouser");
        PageRequest pageable = PageRequest.of(0, 20);

        given(memberClient.searchMemberIds("nouser")).willReturn(List.of());

        Page<?> page = adminOrderService.search(request, pageable);

        assertThat(page.getTotalElements()).isZero();
        verify(orderRepository, never()).searchAdmin(any(), any());
    }

    @Test
    @DisplayName("상태 변경 성공 시 도메인 이벤트를 발행한다")
    void updateStatus_success_publishesDomainEvent() {
        Order order = Order.builder()
                .id(11L)
                .memberId(7L)
                .totalAmount(BigDecimal.valueOf(10000))
                .status(OrderStatus.PENDING)
                .build();

        given(orderRepository.findById(11L)).willReturn(Optional.of(order));
        given(orderRepository.save(any(Order.class))).willAnswer(invocation -> invocation.getArgument(0));
        given(memberClient.getMember(7L))
                .willReturn(new MemberClient.MemberSummary(7L, "user1", "닉네임", "u1@test.com"));

        AdminStatusUpdateRequest request = new AdminStatusUpdateRequest();
        setField(request, "status", OrderStatus.STOCK_RESERVED);
        setField(request, "reason", "테스트 변경");

        AdminOrderDetailResponse response = adminOrderService.updateStatus(11L, request, 1L);

        assertThat(response.getStatus()).isEqualTo("STOCK_RESERVED");

        ArgumentCaptor<OrderStatusChangedDomainEvent> captor =
                ArgumentCaptor.forClass(OrderStatusChangedDomainEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().getOrderId()).isEqualTo(11L);
        assertThat(captor.getValue().getPreviousStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(captor.getValue().getCurrentStatus()).isEqualTo(OrderStatus.STOCK_RESERVED);
    }

    @Test
    @DisplayName("상세 조회 시 주문이 없으면 ERR_ORDER_NOT_FOUND 예외가 발생한다")
    void getDetail_notFound_throwsGlobalException() {
        given(orderRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> adminOrderService.getDetail(999L))
                .isInstanceOf(GlobalException.class)
                .satisfies(ex -> {
                    GlobalException ge = (GlobalException) ex;
                    assertThat(ge.getErrorCode()).isEqualTo("ERR_ORDER_NOT_FOUND");
                    assertThat(ge.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                });
    }

    @Test
    @DisplayName("statusCounts는 조회되지 않은 상태를 0으로 채운다")
    void statusCounts_fillsMissingStatusesWithZero() {
        OrderRepository.StatusCount row = new OrderRepository.StatusCount() {
            @Override
            public OrderStatus getStatus() {
                return OrderStatus.PAID;
            }

            @Override
            public Long getCnt() {
                return 3L;
            }
        };
        given(orderRepository.countByStatusGrouped()).willReturn(List.of(row));

        Map<OrderStatus, Long> result = adminOrderService.statusCounts();

        assertThat(result.keySet()).containsAll(Set.of(OrderStatus.values()));
        assertThat(result.get(OrderStatus.PAID)).isEqualTo(3L);
        assertThat(result.get(OrderStatus.PENDING)).isZero();
    }

    @Test
    @DisplayName("검색 성공 시 member 배치 조회를 통해 응답 매핑한다")
    void search_success_mapsWithMemberBatchLookup() {
        AdminOrderSearchRequest request = new AdminOrderSearchRequest();
        PageRequest pageable = PageRequest.of(0, 20);

        Order order = Order.builder()
                .id(21L)
                .memberId(100L)
                .totalAmount(BigDecimal.valueOf(25000))
                .status(OrderStatus.PAID)
                .build();
        given(orderRepository.searchAdmin(any(), any()))
                .willReturn(new PageImpl<>(List.of(order), pageable, 1));
        given(memberClient.getMembers(Set.of(100L)))
                .willReturn(Map.of(100L, new MemberClient.MemberSummary(100L, "u100", "닉", "u100@test.com")));

        Page<?> result = adminOrderService.search(request, pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        verify(memberClient).getMembers(Set.of(100L));
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
