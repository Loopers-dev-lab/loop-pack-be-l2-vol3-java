package com.loopers.interfaces.api.admin;

import com.loopers.application.category.CategoryApplicationService;
import com.loopers.domain.category.Category;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.category.CategoryDto;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api-admin/v1/categories")
public class AdminCategoryController {

    private static final String HEADER_LDAP = "X-Loopers-Ldap";
    private static final String LDAP_ADMIN = "loopers.admin";

    private final CategoryApplicationService categoryApplicationService;

    @GetMapping
    public ApiResponse<CategoryDto.CategoryListResponse> listCategories(
            @RequestHeader(value = HEADER_LDAP, required = false) String ldap,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        validateAdmin(ldap);
        Pageable pageable = PageRequest.of(page, size);
        Page<Category> categories = categoryApplicationService.list(pageable);
        return ApiResponse.success(CategoryDto.CategoryListResponse.from(categories));
    }

    @GetMapping("/{categoryId}")
    public ApiResponse<CategoryDto.CategoryResponse> getCategory(
            @RequestHeader(value = HEADER_LDAP, required = false) String ldap,
            @PathVariable Long categoryId
    ) {
        validateAdmin(ldap);
        Category category = categoryApplicationService.findById(categoryId);
        return ApiResponse.success(CategoryDto.CategoryResponse.from(category));
    }

    private void validateAdmin(String ldap) {
        if (ldap == null || ldap.isBlank()) {
            throw new CoreException(ErrorType.UNAUTHORIZED, "관리자 인증 헤더가 없습니다.");
        }
        if (!LDAP_ADMIN.equals(ldap)) {
            throw new CoreException(ErrorType.FORBIDDEN, "관리자 권한이 없습니다.");
        }
    }
}
