package com.loopers.interfaces.api.like;

import com.loopers.application.product.ProductFacade;
import com.loopers.application.product.ProductInfo;
import com.loopers.domain.like.LikeService;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.product.ProductV1Dto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequiredArgsConstructor
@RestController
public class LikeV1Controller implements LikeV1ApiSpec {

    private final LikeService likeService;
    private final ProductFacade productFacade;

    @PostMapping("/api/v1/products/{productId}/likes")
    @Override
    public ApiResponse<Void> like(
        @PathVariable Long productId,
        @Valid @RequestBody LikeV1Dto.LikeRequest request
    ) {
        likeService.like(request.userId(), productId);
        return ApiResponse.success(null);
    }

    @DeleteMapping("/api/v1/products/{productId}/likes")
    @Override
    public ApiResponse<Void> unlike(
        @PathVariable Long productId,
        @Valid @RequestBody LikeV1Dto.LikeRequest request
    ) {
        likeService.unlike(request.userId(), productId);
        return ApiResponse.success(null);
    }

    @GetMapping("/api/v1/users/{userId}/likes")
    @Override
    public ApiResponse<List<ProductV1Dto.ProductResponse>> getMyLikes(@PathVariable Long userId) {
        List<ProductInfo> products = productFacade.getMyLikedProducts(userId);
        List<ProductV1Dto.ProductResponse> response = products.stream()
            .map(ProductV1Dto.ProductResponse::from)
            .toList();
        return ApiResponse.success(response);
    }
}
