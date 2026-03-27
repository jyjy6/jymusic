package jymusic.jym_catalog_service.service;

import jymusic.jym_catalog_service.common.GlobalErrorHandler.GlobalException;
import jymusic.jym_catalog_service.domain.entity.Category;
import jymusic.jym_catalog_service.domain.repository.CategoryRepository;
import jymusic.jym_catalog_service.dto.request.CategoryCreateRequest;
import jymusic.jym_catalog_service.dto.request.CategoryUpdateRequest;
import jymusic.jym_catalog_service.dto.response.CategoryResponse;
import jymusic.jym_catalog_service.mapper.CategoryReadMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final CategoryReadMapper categoryReadMapper;

    public List<CategoryResponse> getAllCategories() {
        return categoryReadMapper.findAllCategories();
    }

    @Transactional
    public CategoryResponse createCategory(CategoryCreateRequest request) {
        if (categoryRepository.existsByName(request.getName())) {
            throw new GlobalException(
                    "이미 존재하는 카테고리 이름입니다.", "ERR_CATEGORY_DUPLICATE", HttpStatus.CONFLICT);
        }

        Category category = Category.builder()
                .name(request.getName())
                .build();

        return CategoryResponse.from(categoryRepository.save(category));
    }

    @Transactional
    public CategoryResponse updateCategory(Long id, CategoryUpdateRequest request) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new GlobalException(
                        "카테고리를 찾을 수 없습니다.", "ERR_CATEGORY_NOT_FOUND", HttpStatus.NOT_FOUND));

        if (categoryRepository.existsByNameAndIdNot(request.getName(), id)) {
            throw new GlobalException(
                    "이미 존재하는 카테고리 이름입니다.", "ERR_CATEGORY_DUPLICATE", HttpStatus.CONFLICT);
        }

        category.update(request.getName());
        return CategoryResponse.from(category);
    }

    @Transactional
    public void deleteCategory(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new GlobalException(
                        "카테고리를 찾을 수 없습니다.", "ERR_CATEGORY_NOT_FOUND", HttpStatus.NOT_FOUND));

        categoryRepository.delete(category);
    }
}
