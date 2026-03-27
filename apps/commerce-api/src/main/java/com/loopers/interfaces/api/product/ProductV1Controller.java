package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductDetail;
import com.loopers.application.product.ProductFacade;
import com.loopers.domain.product.ProductSortType;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/products")
public class ProductV1Controller implements ProductV1ApiSpec {

    private final ProductFacade productFacade;

    @GetMapping
    @Override
    public ApiResponse<Page<ProductV1Dto.ProductSummaryResponse>> getAll(
        Pageable pageable,
        @RequestParam(defaultValue = "CREATED_DESC") String sortType
    ) {
        ProductSortType sort = ProductSortType.valueOf(sortType);
        Page<ProductDetail> products = productFacade.getProducts(null, sort, pageable);
        return ApiResponse.success(products.map(ProductV1Dto.ProductSummaryResponse::from));
    }

    @GetMapping("/{productId}")
    @Override
    public ApiResponse<ProductV1Dto.ProductDetailResponse> getById(@PathVariable Long productId) {
        ProductDetail detail = productFacade.getProduct(productId);
        return ApiResponse.success(ProductV1Dto.ProductDetailResponse.from(detail));
    }
}
