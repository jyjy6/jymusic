package jymusic.jym_catalog_service.controller;

import jakarta.validation.Valid;
import jymusic.jym_catalog_service.dto.request.ProductCreateRequest;
import jymusic.jym_catalog_service.dto.request.ProductSearchRequest;
import jymusic.jym_catalog_service.dto.request.ProductUpdateRequest;
import jymusic.jym_catalog_service.dto.response.ProductDetailResponse;
import jymusic.jym_catalog_service.dto.response.ProductListResponse;
import jymusic.jym_catalog_service.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping
    public ResponseEntity<ProductListResponse> getProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size,
            @RequestParam(required = false) Long categoryId) {
        return ResponseEntity.ok(productService.getProducts(page, size, categoryId));
    }

    @GetMapping("/search")
    public ResponseEntity<ProductListResponse> searchProducts(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {

        ProductSearchRequest request = ProductSearchRequest.builder()
                .keyword(keyword)
                .categoryId(categoryId)
                .minPrice(minPrice)
                .maxPrice(maxPrice)
                .page(page)
                .size(size)
                .sort(sort)
                .build();

        return ResponseEntity.ok(productService.searchProducts(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductDetailResponse> getProduct(@PathVariable Long id) {
        return ResponseEntity.ok(productService.getProduct(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductDetailResponse> createProduct(
            @Valid @RequestBody ProductCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(productService.createProduct(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductDetailResponse> updateProduct(
            @PathVariable Long id,
            @Valid @RequestBody ProductUpdateRequest request) {
        return ResponseEntity.ok(productService.updateProduct(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id) {
        productService.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }
}
