package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductFacade;
import com.loopers.application.product.ProductInfo;
import com.loopers.domain.member.Member;
import com.loopers.domain.product.ProductSortCondition;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.resolver.LoginUser;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class ProductController {
    private static final int PAGE_SIZE = 20;
    private static final int MAX_PAGE = 50;

    private final ProductFacade productFacade;

    @GetMapping("/api/v1/brands/{brandId}/products")
    public ApiResponse<ProductDto.BrandProductListResponse> getProductsByBrand(
            @PathVariable Long brandId,
            @RequestParam(defaultValue = "0") int page
    ) {
        if (page < 0 || page > MAX_PAGE) {
            throw new CoreException(ErrorType.BAD_REQUEST,
                    "페이지는 0에서 " + MAX_PAGE + " 사이여야 합니다.");
        }

        Page<ProductInfo> productPage = productFacade.getProductsByBrand(brandId, page, PAGE_SIZE);
        List<ProductDto.BrandProductResponse> products = productPage.getContent().stream()
                .map(ProductDto.BrandProductResponse::from)
                .toList();
        return ApiResponse.success(new ProductDto.BrandProductListResponse(
                products, page, PAGE_SIZE, productPage.getTotalElements(), productPage.getTotalPages()
        ));
    }

    @GetMapping("/api/v1/products")
    public ApiResponse<ProductDto.ProductListResponse> getProductList(
            @RequestParam(required = false) ProductSortCondition sort,
            @LoginUser(required = false) Member member
    ) {
        Long userId = member != null ? member.getId() : null;
        ProductSortCondition sortCondition = sort != null ? sort : ProductSortCondition.LATEST;
        List<ProductInfo> productList = productFacade.getProductList(sortCondition, userId);
        return ApiResponse.success(ProductDto.ProductListResponse.from(productList));
    }

    @GetMapping("/api/v1/products/{productId}")
    public ApiResponse<ProductDto.ProductResponse> getProductDetail(
            @PathVariable Long productId,
            @LoginUser(required = false) Member member
    ) {
        Long userId = member != null ? member.getId() : null;
        ProductInfo productInfo = productFacade.getProductDetail(productId, userId);
        return ApiResponse.success(ProductDto.ProductResponse.from(productInfo));
    }
}
