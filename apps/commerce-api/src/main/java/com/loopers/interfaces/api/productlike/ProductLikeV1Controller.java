package com.loopers.interfaces.api.productlike;

import com.loopers.application.productlike.ProductLikeFacade;
import com.loopers.application.productlike.ProductLikeInfo;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/products/{productId}/likes")
@RequiredArgsConstructor
public class ProductLikeV1Controller {

    private final ProductLikeFacade productLikeFacade;

    @PostMapping
    public ApiResponse<ProductLikeV1Dto.Response> registerLike(
            @PathVariable Long productId,
            @RequestHeader(value = "X-User-Id") Long userId
    ) {
        ProductLikeInfo productLikeInfo = productLikeFacade.registerLike(userId, productId);
        return ApiResponse.success(ProductLikeV1Dto.Response.from(productLikeInfo));
    }

    @DeleteMapping
    public ApiResponse<Void> cancelLike(
            @PathVariable Long productId,
            @RequestHeader(value = "X-User-Id") Long userId
    ) {
        productLikeFacade.cancelLike(userId, productId);
        return ApiResponse.success(null);
    }
}
