package com.loopers.interfaces.api.product;

import com.loopers.application.event.ApplicationDomainEventPublisher;
import com.loopers.application.product.ProductFacade;
import com.loopers.application.product.ProductInfo;
import com.loopers.application.product.ProductPageInfo;
import com.loopers.domain.product.ProductSortType;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/products")
public class ProductV1Controller implements ProductV1ApiSpec {

    private final ProductFacade productFacade;
    private final ApplicationDomainEventPublisher applicationDomainEventPublisher;

    @GetMapping
    @Override
    public ApiResponse<ProductV1Dto.ProductListResponse> getAll(
        Pageable pageable,
        @RequestParam(defaultValue = "latest") String sort,
        @RequestParam(required = false) Long brandId
    ) {
        ProductSortType sortType = ProductSortType.valueOf(sort.toUpperCase());
        ProductPageInfo pageInfo = productFacade.getAll(pageable, sortType, brandId);
        return ApiResponse.success(ProductV1Dto.ProductListResponse.from(pageInfo));
    }

    @GetMapping("/{productId}")
    @Override
    public ApiResponse<ProductV1Dto.ProductResponse> getProduct(@PathVariable Long productId) {
        ProductInfo info = productFacade.getProduct(productId);
        applicationDomainEventPublisher.publishProductClicked(productId, null);
        applicationDomainEventPublisher.publishProductViewed(productId, null);
        return ApiResponse.success(ProductV1Dto.ProductResponse.from(info));
    }
}
