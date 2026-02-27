package com.loopers.interfaces.api.like.v1;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.loopers.application.like.LikeProductUseCase;
import com.loopers.application.like.LikedProductResult;
import com.loopers.application.like.ReadLikedProductsUseCase;
import com.loopers.application.like.UnlikeProductUseCase;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResponse;
import com.loopers.interfaces.api.auth.LoginUser;
import com.loopers.interfaces.api.like.v1.LikeDto.LikedProductResponse;
import com.loopers.support.page.Page;
import com.loopers.support.page.PageSize;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class LikeV1Api implements LikeV1ApiSpec {

    private final LikeProductUseCase likeProductUseCase;
    private final UnlikeProductUseCase unlikeProductUseCase;
    private final ReadLikedProductsUseCase readLikedProductsUseCase;

    @PostMapping("/api/v1/products/{productId}/likes")
    @Override
    public ApiResponse<Object> likeProduct(@LoginUser Long userId, @PathVariable Long productId) {
        likeProductUseCase.execute(userId, productId);
        return ApiResponse.success();
    }

    @DeleteMapping("/api/v1/products/{productId}/likes")
    @Override
    public ApiResponse<Object> unlikeProduct(@LoginUser Long userId, @PathVariable Long productId) {
        unlikeProductUseCase.execute(userId, productId);
        return ApiResponse.success();
    }

    @GetMapping("/api/v1/users/me/likes")
    @Override
    public ApiResponse<PageResponse<LikeDto.LikedProductResponse>> getLikedProducts(
            @LoginUser Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Page<LikedProductResult> results = readLikedProductsUseCase.execute(userId, PageSize.withMaxSize(page, size));
        return ApiResponse.success(
                new PageResponse<>(
                        results.content()
                                .stream()
                                .map(LikedProductResponse::from)
                                .toList(),
                        results.hasNext()
                )
        );
    }
}
