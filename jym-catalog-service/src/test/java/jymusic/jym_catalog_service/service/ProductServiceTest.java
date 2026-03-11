package jymusic.jym_catalog_service.service;

import jymusic.jym_catalog_service.common.GlobalErrorHandler.GlobalException;
import jymusic.jym_catalog_service.domain.entity.Category;
import jymusic.jym_catalog_service.domain.entity.Product;
import jymusic.jym_catalog_service.domain.repository.CategoryRepository;
import jymusic.jym_catalog_service.domain.repository.ProductRepository;
import jymusic.jym_catalog_service.dto.request.ProductCreateRequest;
import jymusic.jym_catalog_service.dto.request.ProductUpdateRequest;
import jymusic.jym_catalog_service.dto.response.ProductDetailResponse;
import jymusic.jym_catalog_service.dto.response.ProductListResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @InjectMocks
    ProductService productService;

    @Mock
    ProductRepository productRepository;

    @Mock
    CategoryRepository categoryRepository;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(productService, "s3BaseUrl",
                "https://jymusic-dev-bucket.s3.ap-northeast-2.amazonaws.com");
    }

    // ─── getProduct ───────────────────────────────────────────────────────

    @Test
    @DisplayName("존재하지 않는 상품 조회 시 ERR_PRODUCT_NOT_FOUND 예외")
    void getProduct_notFound_throwsException() {
        given(productRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> productService.getProduct(999L))
                .isInstanceOf(GlobalException.class)
                .satisfies(e -> {
                    GlobalException ge = (GlobalException) e;
                    assertThat(ge.getErrorCode()).isEqualTo("ERR_PRODUCT_NOT_FOUND");
                    assertThat(ge.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                });
    }

    @Test
    @DisplayName("존재하는 상품 조회 시 ProductDetailResponse 반환")
    void getProduct_found_returnsDetail() {
        Category category = Category.builder().id(1L).name("Rock").build();
        Product product = Product.builder()
                .id(1L)
                .title("Abbey Road")
                .artist("The Beatles")
                .price(BigDecimal.valueOf(29000))
                .stockQuantity(10)
                .category(category)
                .isAvailable(true)
                .build();

        given(productRepository.findById(1L)).willReturn(Optional.of(product));

        ProductDetailResponse response = productService.getProduct(1L);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getTitle()).isEqualTo("Abbey Road");
        assertThat(response.getCategoryName()).isEqualTo("Rock");
    }

    // ─── getProducts ──────────────────────────────────────────────────────

    @Test
    @DisplayName("categoryId 없이 상품 목록 조회 시 전체 상품 반환")
    void getProducts_noCategoryFilter_returnsAll() {
        Page<Product> mockPage = new PageImpl<>(List.of());
        given(productRepository.findByIsAvailableTrue(any(Pageable.class))).willReturn(mockPage);

        ProductListResponse result = productService.getProducts(0, 12, null);

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
        verify(productRepository).findByIsAvailableTrue(any(Pageable.class));
    }

    @Test
    @DisplayName("categoryId 지정 시 카테고리 필터링 쿼리 실행")
    void getProducts_withCategoryFilter_callsFilterQuery() {
        Page<Product> mockPage = new PageImpl<>(List.of());
        given(productRepository.findByCategoryIdAndIsAvailableTrue(any(), any(Pageable.class)))
                .willReturn(mockPage);

        productService.getProducts(0, 12, 2L);

        verify(productRepository).findByCategoryIdAndIsAvailableTrue(any(), any(Pageable.class));
    }

    // ─── createProduct ────────────────────────────────────────────────────

    @Test
    @DisplayName("존재하지 않는 카테고리로 상품 등록 시 ERR_CATEGORY_NOT_FOUND 예외")
    void createProduct_categoryNotFound_throwsException() {
        ProductCreateRequest request = mockCreateRequest(99L);
        given(categoryRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> productService.createProduct(request))
                .isInstanceOf(GlobalException.class)
                .satisfies(e -> {
                    GlobalException ge = (GlobalException) e;
                    assertThat(ge.getErrorCode()).isEqualTo("ERR_CATEGORY_NOT_FOUND");
                    assertThat(ge.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                });
    }

    @Test
    @DisplayName("정상 상품 등록 시 저장 후 ProductDetailResponse 반환")
    void createProduct_validRequest_returnsResponse() {
        Category category = Category.builder().id(1L).name("Rock").build();
        ProductCreateRequest request = mockCreateRequest(1L);

        Product savedProduct = Product.builder()
                .id(42L)
                .title("Abbey Road")
                .artist("The Beatles")
                .price(BigDecimal.valueOf(29000))
                .stockQuantity(100)
                .category(category)
                .isAvailable(true)
                .build();

        given(categoryRepository.findById(1L)).willReturn(Optional.of(category));
        given(productRepository.save(any(Product.class))).willReturn(savedProduct);

        ProductDetailResponse response = productService.createProduct(request);

        assertThat(response.getId()).isEqualTo(42L);
        assertThat(response.getTitle()).isEqualTo("Abbey Road");
        verify(productRepository).save(any(Product.class));
    }

    // ─── updateProduct ────────────────────────────────────────────────────

    @Test
    @DisplayName("존재하지 않는 상품 수정 시 ERR_PRODUCT_NOT_FOUND 예외")
    void updateProduct_notFound_throwsException() {
        ProductUpdateRequest request = mockUpdateRequest(1L);
        given(productRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> productService.updateProduct(999L, request))
                .isInstanceOf(GlobalException.class)
                .satisfies(e -> assertThat(((GlobalException) e).getErrorCode())
                        .isEqualTo("ERR_PRODUCT_NOT_FOUND"));
    }

    // ─── deleteProduct ────────────────────────────────────────────────────

    @Test
    @DisplayName("정상 삭제 시 softDelete() 호출 (isAvailable = false)")
    void deleteProduct_found_callsSoftDelete() {
        Product product = Product.builder()
                .id(1L).title("Abbey Road").artist("The Beatles")
                .price(BigDecimal.valueOf(29000)).stockQuantity(10)
                .isAvailable(true).build();

        given(productRepository.findById(1L)).willReturn(Optional.of(product));

        productService.deleteProduct(1L);

        assertThat(product.getIsAvailable()).isFalse();
    }

    @Test
    @DisplayName("존재하지 않는 상품 삭제 시 ERR_PRODUCT_NOT_FOUND 예외")
    void deleteProduct_notFound_throwsException() {
        given(productRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> productService.deleteProduct(999L))
                .isInstanceOf(GlobalException.class)
                .satisfies(e -> assertThat(((GlobalException) e).getErrorCode())
                        .isEqualTo("ERR_PRODUCT_NOT_FOUND"));
    }

    // ─── Helper methods ───────────────────────────────────────────────────

    private ProductCreateRequest mockCreateRequest(Long categoryId) {
        ProductCreateRequest req = new ProductCreateRequest();
        ReflectionTestUtils.setField(req, "title", "Abbey Road");
        ReflectionTestUtils.setField(req, "artist", "The Beatles");
        ReflectionTestUtils.setField(req, "description", "명반");
        ReflectionTestUtils.setField(req, "price", BigDecimal.valueOf(29000));
        ReflectionTestUtils.setField(req, "stockQuantity", 100);
        ReflectionTestUtils.setField(req, "categoryId", categoryId);
        ReflectionTestUtils.setField(req, "imageKey", null);
        return req;
    }

    private ProductUpdateRequest mockUpdateRequest(Long categoryId) {
        ProductUpdateRequest req = new ProductUpdateRequest();
        ReflectionTestUtils.setField(req, "title", "Abbey Road (Remastered)");
        ReflectionTestUtils.setField(req, "artist", "The Beatles");
        ReflectionTestUtils.setField(req, "description", "리마스터링");
        ReflectionTestUtils.setField(req, "price", BigDecimal.valueOf(32000));
        ReflectionTestUtils.setField(req, "stockQuantity", 80);
        ReflectionTestUtils.setField(req, "categoryId", categoryId);
        ReflectionTestUtils.setField(req, "imageKey", null);
        return req;
    }
}
