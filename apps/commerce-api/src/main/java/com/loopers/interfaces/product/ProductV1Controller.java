package com.loopers.interfaces.product;

import com.loopers.application.product.ProductFacade;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.product.dto.ProductV1Dto;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/products")
public class ProductV1Controller implements ProductV1ApiSpec {

    private final ProductFacade productFacade;

    @GetMapping
    @Override
    public ApiResponse<ProductV1Dto.ListResponse> list(
        @RequestParam(required = false) Long brandId,
        @RequestParam(defaultValue = "latest") String sort,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.success(
                ProductV1Dto.ListResponse.from(
                        switch (sort) {
                            case "price_asc", "price_desc" ->
                                    productFacade.getProductListByPrice(brandId, sort, page, size);
                            case "likes_desc" ->
                                    productFacade.getProductListByLikes(brandId, page, size);
                            default ->
                                    productFacade.getProductListLatest(brandId, page, size);
                        }));
    }

    @GetMapping("/{productId}")
    @Override
    public ApiResponse<ProductV1Dto.DetailResponse> getById(
        @PathVariable Long productId
    ) {
        return ApiResponse.success(
                ProductV1Dto.DetailResponse.from(productFacade.getProductDetail(productId)));
    }
}
