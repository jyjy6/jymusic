package jymusic.jym_catalog_service.domain.repository;

import jymusic.jym_catalog_service.domain.entity.StockReservation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StockReservationRepository extends JpaRepository<StockReservation, Long> {
    Optional<StockReservation> findByOrderId(Long orderId);
}
