package jymusic.jym_catalog_service.domain.repository;

import jymusic.jym_catalog_service.domain.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, Long> {
}
