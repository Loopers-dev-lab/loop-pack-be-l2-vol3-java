package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductFacade;
import com.loopers.application.product.ProductInfo;
import com.loopers.domain.member.Member;
import com.loopers.domain.product.ProductSortCondition;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.resolver.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {
    private final ProductFacade productFacade;

    @GetMapping
    public ApiResponse<ProductDto.ProductListResponse> getProductList(
            @RequestParam(required = false) ProductSortCondition sort,
            @LoginUser(required = false) Member member
    ) {
        Long userId = member != null ? member.getId() : null;
        ProductSortCondition sortCondition = sort != null ? sort : ProductSortCondition.LATEST;
        List<ProductInfo> productList = productFacade.getProductList(sortCondition, userId);
        return ApiResponse.success(ProductDto.ProductListResponse.from(productList));
    }

    @GetMapping("/{productId}")
    public ApiResponse<ProductDto.ProductResponse> getProductDetail(
            @PathVariable Long productId,
            @LoginUser(required = false) Member member
    ) {
        Long userId = member != null ? member.getId() : null;
        ProductInfo productInfo = productFacade.getProductDetail(productId, userId);
        return ApiResponse.success(ProductDto.ProductResponse.from(productInfo));
    }
}
