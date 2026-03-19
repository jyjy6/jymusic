package jymusic.jym_payment_service.domain.repository;

import jymusic.jym_payment_service.domain.entity.PaymentPrepare;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentPrepareRepository extends JpaRepository<PaymentPrepare, Long> {
    Optional<PaymentPrepare> findByOrderId(Long orderId);
    void deleteByOrderId(Long orderId);
}
