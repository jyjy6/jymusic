package jymusic.jym_order_service.domain.entity;

import jakarta.persistence.*;
import jymusic.jym_order_service.common.GlobalErrorHandler.GlobalException;
import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class Order extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long memberId;

    @Column(nullable = false, precision = 12, scale = 0)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<OrderItem> items = new ArrayList<>();

    /**
     * 상태 전이 유효성 검증.
     * 유효하지 않은 전이 시 GlobalException 발생.
     */
    public void transitionTo(OrderStatus newStatus) {
        if (!isValidTransition(this.status, newStatus)) {
            throw new GlobalException(
                String.format("주문 상태를 %s에서 %s로 변경할 수 없습니다.", this.status, newStatus),
                "ERR_INVALID_ORDER_TRANSITION"
            );
        }
        this.status = newStatus;
    }

    private boolean isValidTransition(OrderStatus from, OrderStatus to) {
        return switch (from) {
            case PENDING        -> to == OrderStatus.STOCK_RESERVED || to == OrderStatus.CANCELLED;
            case STOCK_RESERVED -> to == OrderStatus.PAID || to == OrderStatus.CANCELLED;
            case PAID           -> to == OrderStatus.SHIPPED || to == OrderStatus.CANCELLED;
            case SHIPPED        -> to == OrderStatus.COMPLETED;
            case COMPLETED, CANCELLED -> false;  // 종료 상태 — 전이 불가
        };
    }

    /**
     * @deprecated Use {@link #transitionTo(OrderStatus)} instead for safe state transitions.
     */
    @Deprecated
    public void updateStatus(OrderStatus newStatus) {
        this.status = newStatus;
    }
}
