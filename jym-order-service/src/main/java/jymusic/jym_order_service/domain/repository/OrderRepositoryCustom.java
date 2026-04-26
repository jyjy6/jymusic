package jymusic.jym_order_service.domain.repository;

import jymusic.jym_order_service.domain.entity.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface OrderRepositoryCustom {
    Page<Order> searchAdmin(AdminOrderSearchCriteria criteria, Pageable pageable);
}
