package jymusic.jym_catalog_service.mapper;

import jymusic.jym_catalog_service.dto.request.ProductSearchRequest;
import jymusic.jym_catalog_service.dto.response.ProductDetailResponse;
import jymusic.jym_catalog_service.dto.response.ProductSummaryResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ProductReadMapper {

    List<ProductSummaryResponse> findProducts(
            @Param("categoryId") Long categoryId,
            @Param("offset") int offset,
            @Param("size") int size
    );

    long countProducts(@Param("categoryId") Long categoryId);

    ProductDetailResponse findProductById(@Param("id") Long id);

    List<ProductSummaryResponse> searchProducts(@Param("req") ProductSearchRequest request);

    long countSearchProducts(@Param("req") ProductSearchRequest request);
}
