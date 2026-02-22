package com.loopers.interfaces.api.product.v1;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.loopers.application.product.ProductDetail;
import com.loopers.application.product.ProductService;
import com.loopers.domain.product.ProductSortType;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResponse;
import com.loopers.interfaces.api.auth.LoginUser;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.support.page.Page;
import com.loopers.support.page.PageSize;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/products")
public class ProductV1Api implements ProductV1ApiSpec {

    private final ProductService productService;

    @GetMapping
    @Override
    public ApiResponse<PageResponse<ProductDto.ProductDetailResponse>> getActiveProducts(
            @LoginUser Long userId,
            @RequestParam(required = false) Long brandId,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        ProductSortType sortType = parseSortType(sort);
        PageSize pageSize = new PageSize(page, size);
        Page<ProductDetail> products = brandId != null
                ? productService.getActiveProductsByBrandId(userId, brandId, sortType, pageSize)
                : productService.getActiveProducts(userId, sortType, pageSize);
        List<ProductDto.ProductDetailResponse> content = products.content().stream()
                .map(ProductDto.ProductDetailResponse::from)
                .toList();
        return ApiResponse.success(new PageResponse<>(content, products.hasNext()));
    }

    private ProductSortType parseSortType(String sort) {
        if (sort == null) {
            return ProductSortType.DEFAULT;
        }
        try {
            return ProductSortType.valueOf(sort);
        } catch (IllegalArgumentException e) {
            throw new CoreException(ErrorType.INVALID_SORT_TYPE);
        }
    }

    @GetMapping("/{productId}")
    @Override
    public ApiResponse<ProductDto.ProductDetailResponse> getActiveProduct(
            @LoginUser Long userId,
            @PathVariable Long productId
    ) {
        ProductDetail product = productService.getActiveProduct(userId, productId);
        return ApiResponse.success(ProductDto.ProductDetailResponse.from(product));
    }
}
