package jymusic.jym_catalog_service.domain.repository;

import jymusic.jym_catalog_service.domain.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {

    Page<Product> findByIsAvailableTrue(Pageable pageable);

    Page<Product> findByCategoryIdAndIsAvailableTrue(Long categoryId, Pageable pageable);

    // ========================================================================
    // [추후 추가 예정] 동시성 제어 (비관적 락)
    // ========================================================================
    // TODO: 대용량 트래픽 발생 시 재고 차감 과정에서 "Lost Update" 방지를 위해 사용 예정
    // @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    // @org.springframework.data.jpa.repository.Query("SELECT p FROM Product p WHERE p.id = :id")
    // java.util.Optional<Product> findByIdWithPessimisticLock(@org.springframework.data.repository.query.Param("id") Long id);
}
