package jymusic.jym_order_service.service.admin;

import jymusic.jym_order_service.client.MemberClient;
import jymusic.jym_order_service.common.GlobalErrorHandler.GlobalException;
import jymusic.jym_order_service.domain.entity.Order;
import jymusic.jym_order_service.domain.entity.OrderStatus;
import jymusic.jym_order_service.domain.event.OrderStatusChangedDomainEvent;
import jymusic.jym_order_service.domain.repository.AdminOrderSearchCriteria;
import jymusic.jym_order_service.domain.repository.OrderRepository;
import jymusic.jym_order_service.dto.request.AdminOrderSearchRequest;
import jymusic.jym_order_service.dto.request.AdminStatusUpdateRequest;
import jymusic.jym_order_service.dto.response.AdminOrderDetailResponse;
import jymusic.jym_order_service.dto.response.AdminOrderSummaryResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class AdminOrderService {

    private final OrderRepository orderRepository;
    private final MemberClient memberClient;
    private final ApplicationEventPublisher eventPublisher;

    public Page<AdminOrderSummaryResponse> search(AdminOrderSearchRequest req, Pageable pageable) {
        List<Long> memberIds = StringUtils.hasText(req.getKeyword())
                ? memberClient.searchMemberIds(req.getKeyword())
                : null;

        if (StringUtils.hasText(req.getKeyword()) && (memberIds == null || memberIds.isEmpty())) {
            return Page.empty(pageable);
        }

        AdminOrderSearchCriteria criteria = new AdminOrderSearchCriteria(
                memberIds,
                req.getProductTitle(),
                req.getStatus(),
                req.getStatuses(),
                req.getStartDate() == null ? null : req.getStartDate().atStartOfDay(),
                req.getEndDate() == null ? null : req.getEndDate().atTime(LocalTime.MAX),
                req.getMinAmount(),
                req.getMaxAmount()
        );

        Page<Order> page = orderRepository.searchAdmin(criteria, pageable);

        Set<Long> memberIdSet = page.getContent().stream()
                .map(Order::getMemberId)
                .collect(Collectors.toSet());
        Map<Long, MemberClient.MemberSummary> memberMap = memberIdSet.isEmpty()
                ? Map.of()
                : memberClient.getMembers(memberIdSet);

        return page.map(order -> AdminOrderSummaryResponse.of(
                order,
                memberMap.getOrDefault(order.getMemberId(), MemberClient.MemberSummary.unknown(order.getMemberId()))
        ));
    }

    public AdminOrderDetailResponse getDetail(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new GlobalException("주문을 찾을 수 없습니다.", "ERR_ORDER_NOT_FOUND", HttpStatus.NOT_FOUND));
        MemberClient.MemberSummary memberSummary = memberClient.getMember(order.getMemberId());
        return AdminOrderDetailResponse.of(order, memberSummary, allowedNextStatusesOf(order.getStatus()));
    }

    @Transactional
    public AdminOrderDetailResponse updateStatus(Long orderId, AdminStatusUpdateRequest req, Long adminId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new GlobalException("주문을 찾을 수 없습니다.", "ERR_ORDER_NOT_FOUND", HttpStatus.NOT_FOUND));

        OrderStatus previous = order.getStatus();
        order.transitionTo(req.getStatus());
        orderRepository.save(order);

        eventPublisher.publishEvent(OrderStatusChangedDomainEvent.of(
                order.getId(),
                order.getMemberId(),
                previous,
                order.getStatus(),
                order.getTotalAmount(),
                order.getItems().isEmpty() ? "" : order.getItems().get(0).getProductTitle(),
                order.getItems().size()
        ));

        log.info("ADMIN status update: orderId={}, adminId={}, {}->{}, reason={}",
                orderId, adminId, previous, req.getStatus(), req.getReason());

        return getDetail(orderId);
    }

    public Map<OrderStatus, Long> statusCounts() {
        Map<OrderStatus, Long> result = new EnumMap<>(OrderStatus.class);
        for (OrderStatus status : OrderStatus.values()) {
            result.put(status, 0L);
        }
        for (OrderRepository.StatusCount row : orderRepository.countByStatusGrouped()) {
            result.put(row.getStatus(), row.getCnt());
        }
        return result;
    }

    private List<OrderStatus> allowedNextStatusesOf(OrderStatus current) {
        return switch (current) {
            case PENDING -> List.of(OrderStatus.STOCK_RESERVED, OrderStatus.CANCELLED);
            case STOCK_RESERVED -> List.of(OrderStatus.PAID, OrderStatus.CANCELLED);
            case PAID -> List.of(OrderStatus.SHIPPED, OrderStatus.CANCELLED);
            case SHIPPED -> List.of(OrderStatus.COMPLETED);
            case COMPLETED, CANCELLED -> List.of();
        };
    }
}
