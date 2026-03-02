package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductFacade;
import com.loopers.application.product.ProductInfo;
import com.loopers.domain.product.ProductSortType;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/products")
public class ProductV1Controller implements ProductV1ApiSpec {

    private final ProductFacade productFacade;

    @GetMapping
    @Override
    public ApiResponse<Page<ProductV1Dto.ProductResponse>> getAll(
        Pageable pageable,
        @RequestParam(defaultValue = "latest") String sort
    ) {
        ProductSortType sortType = ProductSortType.valueOf(sort.toUpperCase());
        Page<ProductV1Dto.ProductResponse> response = productFacade.getAll(pageable, sortType)
            .map(ProductV1Dto.ProductResponse::from);
        return ApiResponse.success(response);
    }

    @GetMapping("/{productId}")
    @Override
    public ApiResponse<ProductV1Dto.ProductResponse> getProduct(@PathVariable Long productId) {
        ProductInfo info = productFacade.getProduct(productId);
        return ApiResponse.success(ProductV1Dto.ProductResponse.from(info));
    }
}
