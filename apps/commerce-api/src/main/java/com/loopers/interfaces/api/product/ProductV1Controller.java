package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductFacade;
import com.loopers.application.product.ProductInfo;
import com.loopers.application.product.ProductSort;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResponse;
import com.loopers.interfaces.api.product.dto.ProductV1Dto;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/products")
public class ProductV1Controller {

    private final ProductFacade productFacade;

    @GetMapping("/{productId}")
    public ApiResponse<ProductV1Dto.ProductResponse> getProduct(@PathVariable Long productId) {
        ProductInfo product = productFacade.getActiveProduct(productId);
        return ApiResponse.success(ProductV1Dto.ProductResponse.from(product));
    }

    @GetMapping
    public ApiResponse<PageResponse<ProductV1Dto.ProductResponse>> getProducts(
            @RequestParam(required = false) Long brandId,
            @RequestParam(defaultValue = "LATEST") ProductSort sort,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        Page<ProductV1Dto.ProductResponse> page = productFacade.getActiveProducts(brandId, sort, pageable)
                                                               .map(ProductV1Dto.ProductResponse::from);

        return ApiResponse.success(PageResponse.from(page));
    }
}
