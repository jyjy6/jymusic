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
import jymusic.jym_catalog_service.dto.response.ProductSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    @Value("${spring.cloud.aws.s3.base-url}")
    private String s3BaseUrl;

    public ProductListResponse getProducts(int page, int size, Long categoryId) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Product> productPage;

        if (categoryId != null) {
            productPage = productRepository.findByCategoryIdAndIsAvailableTrue(categoryId, pageable);
        } else {
            productPage = productRepository.findByIsAvailableTrue(pageable);
        }

        Page<ProductSummaryResponse> mapped = productPage
                .map(p -> ProductSummaryResponse.from(p, s3BaseUrl));
        return ProductListResponse.from(mapped);
    }

    public ProductDetailResponse getProduct(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new GlobalException(
                        "상품을 찾을 수 없습니다.", "ERR_PRODUCT_NOT_FOUND", HttpStatus.NOT_FOUND));
        return ProductDetailResponse.from(product, s3BaseUrl);
    }

    @Transactional
    public ProductDetailResponse createProduct(ProductCreateRequest request) {
        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new GlobalException(
                        "카테고리를 찾을 수 없습니다.", "ERR_CATEGORY_NOT_FOUND", HttpStatus.NOT_FOUND));

        Product product = Product.builder()
                .title(request.getTitle())
                .artist(request.getArtist())
                .description(request.getDescription())
                .price(request.getPrice())
                .stockQuantity(request.getStockQuantity())
                .category(category)
                .imageKey(request.getImageKey())
                .isAvailable(true)
                .build();

        return ProductDetailResponse.from(productRepository.save(product), s3BaseUrl);
    }

    @Transactional
    public ProductDetailResponse updateProduct(Long id, ProductUpdateRequest request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new GlobalException(
                        "상품을 찾을 수 없습니다.", "ERR_PRODUCT_NOT_FOUND", HttpStatus.NOT_FOUND));

        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new GlobalException(
                        "카테고리를 찾을 수 없습니다.", "ERR_CATEGORY_NOT_FOUND", HttpStatus.NOT_FOUND));

        product.update(request.getTitle(), request.getArtist(), request.getDescription(),
                request.getPrice(), request.getStockQuantity(), category, request.getImageKey());

        return ProductDetailResponse.from(product, s3BaseUrl);
    }

    @Transactional
    public void deleteProduct(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new GlobalException(
                        "상품을 찾을 수 없습니다.", "ERR_PRODUCT_NOT_FOUND", HttpStatus.NOT_FOUND));
        product.softDelete();
    }
}
