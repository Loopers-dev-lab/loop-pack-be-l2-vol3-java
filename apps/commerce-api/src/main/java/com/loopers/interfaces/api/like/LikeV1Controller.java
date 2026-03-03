package com.loopers.interfaces.api.like;

import com.loopers.application.like.LikeFacade;
import com.loopers.application.like.LikeProductInfo;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResponse;
import com.loopers.interfaces.api.auth.AuthUser;
import com.loopers.interfaces.api.auth.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class LikeV1Controller implements LikeApiV1Spec {

    private final LikeFacade likeFacade;

    // Command

    @PostMapping("/api/v1/products/{productId}/likes")
    @Override
    public ApiResponse<Void> like(
            @PathVariable Long productId,
            @AuthUser AuthenticatedUser authUser) {
        likeFacade.like(authUser.id(), productId);
        return ApiResponse.success();
    }

    @DeleteMapping("/api/v1/products/{productId}/likes")
    @Override
    public ApiResponse<Void> unlike(
            @PathVariable Long productId,
            @AuthUser AuthenticatedUser authUser) {
        likeFacade.unlike(authUser.id(), productId);
        return ApiResponse.success();
    }

    // Query

    @GetMapping("/api/v1/likes")
    @Override
    public ApiResponse<PageResponse<LikeV1Dto.LikeProductResponse>> list(
            @Valid LikeRequest.ListLiked request,
            @AuthUser AuthenticatedUser authUser) {
        Page<LikeProductInfo> likedProducts = likeFacade.getLikedProducts(
                authUser.id(), request.toPageable());
        return ApiResponse.success(PageResponse.from(likedProducts, LikeV1Dto.LikeProductResponse::from));
    }
}
