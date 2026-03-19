package jymusic.jym_order_service.domain.repository;

import jymusic.jym_order_service.domain.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findAllByMemberIdOrderByCreatedAtDesc(Long memberId);
}
