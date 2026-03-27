package jymusic.jym_catalog_service.mapper;

import jymusic.jym_catalog_service.dto.response.CategoryResponse;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface CategoryReadMapper {

    List<CategoryResponse> findAllCategories();
}
