package jymusic.jym_payment_service.domain.repository;

import jymusic.jym_payment_service.domain.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByOrderId(Long orderId);
    Optional<Payment> findByPaymentKey(String paymentKey);
}
