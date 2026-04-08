package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductFacade;
import com.loopers.application.product.ProductInfo;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductUserV1Controller implements ProductUserApiV1Spec {

    private final ProductFacade productFacade;

    // Query

    @GetMapping
    @Override
    public ApiResponse<PageResponse<ProductUserV1Dto.ProductResponse>> list(
            @Valid ProductRequest.ListActive request) {
        Page<ProductInfo> products = productFacade.getActiveList(
                request.brandId(), request.toPageable());
        PageResponse<ProductUserV1Dto.ProductResponse> pageResponse =
                PageResponse.from(products, ProductUserV1Dto.ProductResponse::from);
        return ApiResponse.success(pageResponse);
    }

    @GetMapping("/{productId}")
    @Override
    public ApiResponse<ProductUserV1Dto.ProductResponse> detail(
            @PathVariable Long productId,
            @RequestHeader(value = "User-Agent", required = false) String userAgent,
            @RequestHeader(value = "X-Loopers-UserId", required = false) Long userId,
            HttpServletRequest request
    ) {
        String anonymousId = (String) request.getAttribute("anonymous_id");
        ProductInfo info = productFacade.getActiveDetail(productId, userId, anonymousId, userAgent);
        return ApiResponse.success(ProductUserV1Dto.ProductResponse.from(info));
    }
}
