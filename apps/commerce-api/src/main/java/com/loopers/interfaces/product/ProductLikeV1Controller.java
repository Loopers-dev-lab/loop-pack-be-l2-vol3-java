package com.loopers.interfaces.product;

import com.loopers.application.product.ProductLikeFacade;
import com.loopers.application.product.dto.ProductLikeResult;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.auth.Login;
import com.loopers.interfaces.auth.LoginUser;
import com.loopers.interfaces.product.dto.ProductLikeV1Dto;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
public class ProductLikeV1Controller implements ProductLikeV1ApiSpec {

    private final ProductLikeFacade productLikeFacade;

    @PostMapping("/api/v1/products/{productId}/likes")
    @Override
    public ApiResponse<Object> like(
        @Login LoginUser loginUser,
        @PathVariable Long productId
    ) {
        productLikeFacade.like(loginUser.id(), productId);
        return ApiResponse.success();
    }

    @DeleteMapping("/api/v1/products/{productId}/likes")
    @Override
    public ApiResponse<Object> unlike(
        @Login LoginUser loginUser,
        @PathVariable Long productId
    ) {
        productLikeFacade.unlike(loginUser.id(), productId);
        return ApiResponse.success();
    }

    @GetMapping("/api/v1/users/me/likes")
    @Override
    public ApiResponse<ProductLikeV1Dto.ListResponse> getMyLikes(
        @Login LoginUser loginUser
    ) {
        List<ProductLikeResult> results = productLikeFacade.getMyLikedProducts(loginUser.id());

        return ApiResponse.success(
                new ProductLikeV1Dto.ListResponse(
                        results.stream()
                                .map(ProductLikeV1Dto.ListResponse.ListItem::from)
                                .toList()));
    }
}
