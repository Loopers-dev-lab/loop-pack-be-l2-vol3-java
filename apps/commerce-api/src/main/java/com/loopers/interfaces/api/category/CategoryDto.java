package com.loopers.interfaces.api.category;

import com.loopers.domain.category.Category;
import org.springframework.data.domain.Page;

import java.util.List;

public class CategoryDto {

    public record CategoryResponse(
            Long id,
            String name
    ) {
        public static CategoryResponse from(Category category) {
            return new CategoryResponse(category.id(), category.name());
        }
    }

    public record CategoryListResponse(
            List<CategoryResponse> items,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {
        public static CategoryListResponse from(Page<Category> pageData) {
            return new CategoryListResponse(
                    pageData.getContent().stream().map(CategoryResponse::from).toList(),
                    pageData.getNumber(),
                    pageData.getSize(),
                    pageData.getTotalElements(),
                    pageData.getTotalPages()
            );
        }
    }
}
