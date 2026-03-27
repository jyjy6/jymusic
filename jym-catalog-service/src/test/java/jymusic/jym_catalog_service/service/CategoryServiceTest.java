package jymusic.jym_catalog_service.service;

import jymusic.jym_catalog_service.common.GlobalErrorHandler.GlobalException;
import jymusic.jym_catalog_service.domain.entity.Category;
import jymusic.jym_catalog_service.domain.repository.CategoryRepository;
import jymusic.jym_catalog_service.mapper.CategoryReadMapper;
import jymusic.jym_catalog_service.dto.request.CategoryCreateRequest;
import jymusic.jym_catalog_service.dto.request.CategoryUpdateRequest;
import jymusic.jym_catalog_service.dto.response.CategoryResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @InjectMocks
    CategoryService categoryService;

    @Mock
    CategoryRepository categoryRepository;

    @Mock
    CategoryReadMapper categoryReadMapper;

    // ─── getAllCategories ─────────────────────────────────────────────────

    @Test
    @DisplayName("카테고리 목록 조회 시 전체 CategoryResponse 리스트 반환")
    void getAllCategories_returnsList() {
        given(categoryReadMapper.findAllCategories()).willReturn(List.of(
                CategoryResponse.builder().id(1L).name("Rock").build(),
                CategoryResponse.builder().id(2L).name("Jazz").build()
        ));

        List<CategoryResponse> result = categoryService.getAllCategories();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getName()).isEqualTo("Rock");
        assertThat(result.get(1).getName()).isEqualTo("Jazz");
    }

    // ─── createCategory ───────────────────────────────────────────────────

    @Test
    @DisplayName("정상 카테고리 생성 시 저장 후 CategoryResponse 반환")
    void createCategory_validRequest_returnsResponse() {
        CategoryCreateRequest request = mockCreateRequest("Classical");
        Category saved = Category.builder().id(4L).name("Classical").build();

        given(categoryRepository.existsByName("Classical")).willReturn(false);
        given(categoryRepository.save(any(Category.class))).willReturn(saved);

        CategoryResponse response = categoryService.createCategory(request);

        assertThat(response.getId()).isEqualTo(4L);
        assertThat(response.getName()).isEqualTo("Classical");
        verify(categoryRepository).save(any(Category.class));
    }

    @Test
    @DisplayName("이름 중복 시 ERR_CATEGORY_DUPLICATE(409) 예외")
    void createCategory_duplicateName_throwsException() {
        CategoryCreateRequest request = mockCreateRequest("Rock");

        given(categoryRepository.existsByName("Rock")).willReturn(true);

        assertThatThrownBy(() -> categoryService.createCategory(request))
                .isInstanceOf(GlobalException.class)
                .satisfies(e -> {
                    GlobalException ge = (GlobalException) e;
                    assertThat(ge.getErrorCode()).isEqualTo("ERR_CATEGORY_DUPLICATE");
                    assertThat(ge.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
                });
    }

    // ─── updateCategory ───────────────────────────────────────────────────

    @Test
    @DisplayName("정상 카테고리 수정 시 이름이 변경되고 CategoryResponse 반환")
    void updateCategory_validRequest_returnsUpdatedResponse() {
        Category existing = Category.builder().id(1L).name("Rock").build();
        CategoryUpdateRequest request = mockUpdateRequest("Rock & Roll");

        given(categoryRepository.findById(1L)).willReturn(Optional.of(existing));
        given(categoryRepository.existsByNameAndIdNot("Rock & Roll", 1L)).willReturn(false);

        CategoryResponse response = categoryService.updateCategory(1L, request);

        assertThat(response.getName()).isEqualTo("Rock & Roll");
    }

    @Test
    @DisplayName("존재하지 않는 카테고리 수정 시 ERR_CATEGORY_NOT_FOUND(404) 예외")
    void updateCategory_notFound_throwsException() {
        CategoryUpdateRequest request = mockUpdateRequest("New Name");

        given(categoryRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.updateCategory(999L, request))
                .isInstanceOf(GlobalException.class)
                .satisfies(e -> {
                    GlobalException ge = (GlobalException) e;
                    assertThat(ge.getErrorCode()).isEqualTo("ERR_CATEGORY_NOT_FOUND");
                    assertThat(ge.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                });
    }

    @Test
    @DisplayName("수정 시 다른 카테고리와 이름 중복이면 ERR_CATEGORY_DUPLICATE(409) 예외")
    void updateCategory_duplicateName_throwsException() {
        Category existing = Category.builder().id(1L).name("Rock").build();
        CategoryUpdateRequest request = mockUpdateRequest("Jazz");

        given(categoryRepository.findById(1L)).willReturn(Optional.of(existing));
        given(categoryRepository.existsByNameAndIdNot("Jazz", 1L)).willReturn(true);

        assertThatThrownBy(() -> categoryService.updateCategory(1L, request))
                .isInstanceOf(GlobalException.class)
                .satisfies(e -> {
                    GlobalException ge = (GlobalException) e;
                    assertThat(ge.getErrorCode()).isEqualTo("ERR_CATEGORY_DUPLICATE");
                    assertThat(ge.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
                });
    }

    // ─── deleteCategory ───────────────────────────────────────────────────

    @Test
    @DisplayName("정상 카테고리 삭제 시 delete() 호출")
    void deleteCategory_found_callsDelete() {
        Category category = Category.builder().id(1L).name("Rock").build();

        given(categoryRepository.findById(1L)).willReturn(Optional.of(category));

        categoryService.deleteCategory(1L);

        verify(categoryRepository).delete(category);
    }

    @Test
    @DisplayName("존재하지 않는 카테고리 삭제 시 ERR_CATEGORY_NOT_FOUND(404) 예외")
    void deleteCategory_notFound_throwsException() {
        given(categoryRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.deleteCategory(999L))
                .isInstanceOf(GlobalException.class)
                .satisfies(e -> {
                    GlobalException ge = (GlobalException) e;
                    assertThat(ge.getErrorCode()).isEqualTo("ERR_CATEGORY_NOT_FOUND");
                    assertThat(ge.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                });
    }

    // ─── Helper methods ───────────────────────────────────────────────────

    private CategoryCreateRequest mockCreateRequest(String name) {
        CategoryCreateRequest req = new CategoryCreateRequest();
        ReflectionTestUtils.setField(req, "name", name);
        return req;
    }

    private CategoryUpdateRequest mockUpdateRequest(String name) {
        CategoryUpdateRequest req = new CategoryUpdateRequest();
        ReflectionTestUtils.setField(req, "name", name);
        return req;
    }
}
