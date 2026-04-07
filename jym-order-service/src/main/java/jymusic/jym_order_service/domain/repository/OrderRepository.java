package jymusic.jym_order_service.domain.repository;

import jymusic.jym_order_service.domain.entity.Order;
import jymusic.jym_order_service.domain.entity.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findAllByMemberIdOrderByCreatedAtDesc(Long memberId);

    // [NEW] 타임아웃 조회 — 결제 대기 상태에서 일정 시간 초과한 주문
    List<Order> findByStatusInAndCreatedAtBefore(
            List<OrderStatus> statuses, LocalDateTime threshold);
}
