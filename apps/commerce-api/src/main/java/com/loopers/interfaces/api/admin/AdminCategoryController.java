package com.loopers.interfaces.api.admin;

import com.loopers.application.category.CategoryApplicationService;
import com.loopers.domain.category.Category;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.category.CategoryDto;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api-admin/v1/categories")
public class AdminCategoryController {

    private final CategoryApplicationService categoryApplicationService;

    @GetMapping
    public ApiResponse<CategoryDto.CategoryListResponse> listCategories(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Category> categories = categoryApplicationService.list(pageable);
        return ApiResponse.success(CategoryDto.CategoryListResponse.from(categories));
    }

    @GetMapping("/{categoryId}")
    public ApiResponse<CategoryDto.CategoryResponse> getCategory(
            @PathVariable Long categoryId
    ) {
        Category category = categoryApplicationService.findById(categoryId);
        return ApiResponse.success(CategoryDto.CategoryResponse.from(category));
    }
}
