package jymusic.jym_order_service.controller;

import jakarta.validation.Valid;
import jymusic.jym_order_service.domain.entity.OrderStatus;
import jymusic.jym_order_service.dto.request.OrderCreateRequest;
import jymusic.jym_order_service.dto.response.OrderDetailResponse;
import jymusic.jym_order_service.dto.response.OrderResponse;
import jymusic.jym_order_service.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(
            @AuthenticationPrincipal String memberId,
            @Valid @RequestBody OrderCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(orderService.createOrder(Long.parseLong(memberId), request));
    }

    @GetMapping
    public ResponseEntity<List<OrderResponse>> getMyOrders(@AuthenticationPrincipal String memberId) {
        return ResponseEntity.ok(orderService.getMyOrders(Long.parseLong(memberId)));
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<OrderDetailResponse> getOrderDetail(
            @AuthenticationPrincipal String memberId,
            @PathVariable Long orderId) {
        return ResponseEntity.ok(orderService.getOrderDetail(Long.parseLong(memberId), orderId));
    }

    // payment-service가 결제 완료·취소 후 주문 상태를 동기적으로 업데이트하기 위한 내부 엔드포인트
    @PutMapping("/{orderId}/status")
    public ResponseEntity<Void> updateStatus(
            @PathVariable Long orderId,
            @RequestBody Map<String, String> body) {
        orderService.updateOrderStatus(orderId, OrderStatus.valueOf(body.get("status")));
        return ResponseEntity.noContent().build();
    }
}
