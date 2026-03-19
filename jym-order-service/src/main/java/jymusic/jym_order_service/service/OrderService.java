package jymusic.jym_order_service.service;

import jymusic.jym_order_service.client.CatalogClient;
import jymusic.jym_order_service.common.GlobalErrorHandler.GlobalException;
import jymusic.jym_order_service.domain.entity.Order;
import jymusic.jym_order_service.domain.entity.OrderItem;
import jymusic.jym_order_service.domain.entity.OrderStatus;
import jymusic.jym_order_service.domain.repository.CartRepository;
import jymusic.jym_order_service.domain.repository.OrderRepository;
import jymusic.jym_order_service.dto.request.OrderCreateRequest;
import jymusic.jym_order_service.dto.request.OrderItemRequest;
import jymusic.jym_order_service.dto.response.OrderDetailResponse;
import jymusic.jym_order_service.dto.response.OrderResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderService {

    private final OrderRepository orderRepository;
    private final CartRepository cartRepository;
    private final CatalogClient catalogClient;

    @Transactional
    public OrderResponse createOrder(Long memberId, OrderCreateRequest request) {
        record ItemInfo(CatalogClient.ProductInfo info, int quantity) {}

        List<ItemInfo> infos = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;

        for (OrderItemRequest itemReq : request.getItems()) {
            CatalogClient.ProductInfo info = catalogClient.getProductInfo(itemReq.getProductId());
            if (info.stockQuantity() < itemReq.getQuantity()) {
                throw new GlobalException(
                        "상품 '" + info.title() + "'의 재고가 부족합니다.",
                        "ERR_INSUFFICIENT_STOCK"
                );
            }
            totalAmount = totalAmount.add(info.price().multiply(BigDecimal.valueOf(itemReq.getQuantity())));
            infos.add(new ItemInfo(info, itemReq.getQuantity()));
        }

        Order order = Order.builder()
                .memberId(memberId)
                .totalAmount(totalAmount)
                .status(OrderStatus.PENDING)
                .build();

        for (ItemInfo i : infos) {
            OrderItem orderItem = OrderItem.builder()
                    .order(order)
                    .productId(i.info().productId())
                    .productTitle(i.info().title())
                    .unitPrice(i.info().price())
                    .quantity(i.quantity())
                    .build();
            order.getItems().add(orderItem);
        }

        Order savedOrder = orderRepository.save(order);

        cartRepository.findByMemberId(memberId).ifPresent(cart -> {
            cart.getItems().clear();
            cartRepository.save(cart);
        });

        return OrderResponse.from(savedOrder);
    }

    public List<OrderResponse> getMyOrders(Long memberId) {
        return orderRepository.findAllByMemberIdOrderByCreatedAtDesc(memberId)
                .stream()
                .map(OrderResponse::from)
                .toList();
    }

    public OrderDetailResponse getOrderDetail(Long memberId, Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new GlobalException("주문을 찾을 수 없습니다.", "ERR_ORDER_NOT_FOUND", HttpStatus.NOT_FOUND));

        if (!order.getMemberId().equals(memberId)) {
            throw new GlobalException("접근 권한이 없습니다.", "ERR_FORBIDDEN", HttpStatus.FORBIDDEN);
        }

        return OrderDetailResponse.from(order);
    }

    @Transactional
    public void updateOrderStatus(Long orderId, OrderStatus newStatus) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new GlobalException("주문을 찾을 수 없습니다.", "ERR_ORDER_NOT_FOUND", HttpStatus.NOT_FOUND));
        order.updateStatus(newStatus);
    }

}
