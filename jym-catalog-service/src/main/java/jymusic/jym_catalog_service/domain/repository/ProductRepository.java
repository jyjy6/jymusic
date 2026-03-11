package jymusic.jym_catalog_service.domain.repository;

import jymusic.jym_catalog_service.domain.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {

    Page<Product> findByIsAvailableTrue(Pageable pageable);

    Page<Product> findByCategoryIdAndIsAvailableTrue(Long categoryId, Pageable pageable);
}
