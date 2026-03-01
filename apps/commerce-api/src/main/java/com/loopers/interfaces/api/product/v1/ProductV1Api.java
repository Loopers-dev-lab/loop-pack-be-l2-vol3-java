package com.loopers.interfaces.api.product.v1;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.loopers.application.product.ProductDetail;
import com.loopers.application.product.ReadActiveProductDetailUseCase;
import com.loopers.application.product.ReadActiveProductsUseCase;
import com.loopers.domain.product.ProductSortType;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResponse;
import com.loopers.interfaces.api.auth.LoginUser;
import com.loopers.support.page.Page;
import com.loopers.support.page.PageSize;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/products")
public class ProductV1Api implements ProductV1ApiSpec {

    private final ReadActiveProductsUseCase readActiveProductsUseCase;
    private final ReadActiveProductDetailUseCase readActiveProductDetailUseCase;

    @GetMapping
    @Override
    public ApiResponse<PageResponse<ProductDto.ProductDetailResponse>> getActiveProducts(
            @LoginUser Long userId,
            @RequestParam(required = false) Long brandId,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        ProductSortType sortType = ProductSortType.from(sort);
        PageSize pageSize = PageSize.withMaxSize(page, size);
        Page<ProductDetail> products = readActiveProductsUseCase.execute(userId, brandId, sortType, pageSize);
        List<ProductDto.ProductDetailResponse> content = products.content().stream()
                .map(ProductDto.ProductDetailResponse::from)
                .toList();
        return ApiResponse.success(new PageResponse<>(content, products.hasNext()));
    }

    @GetMapping("/{productId}")
    @Override
    public ApiResponse<ProductDto.ProductDetailResponse> getActiveProduct(
            @LoginUser Long userId,
            @PathVariable Long productId
    ) {
        ProductDetail product = readActiveProductDetailUseCase.execute(userId, productId);
        return ApiResponse.success(ProductDto.ProductDetailResponse.from(product));
    }
}
