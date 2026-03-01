package com.loopers.interfaces.api.like;

import com.loopers.application.like.LikeApplicationService;
import com.loopers.application.like.LikedProductDetail;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.auth.AuthUser;
import com.loopers.interfaces.api.auth.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RequiredArgsConstructor
@RestController
public class LikeV1Controller implements LikeV1ApiSpec {

    private final LikeApplicationService likeApplicationService;

    @PostMapping("/api/v1/products/{productId}/likes")
    @Override
    public ApiResponse<Void> like(@AuthUser AuthenticatedUser authUser, @PathVariable Long productId) {
        likeApplicationService.like(authUser.userId(), productId);
        return ApiResponse.success();
    }

    @DeleteMapping("/api/v1/products/{productId}/likes")
    @Override
    public ApiResponse<Void> unlike(@AuthUser AuthenticatedUser authUser, @PathVariable Long productId) {
        likeApplicationService.unlike(authUser.userId(), productId);
        return ApiResponse.success();
    }

    @GetMapping("/api/v1/likes")
    @Override
    public ApiResponse<LikeV1Dto.LikeListResponse> getMyLikes(@AuthUser AuthenticatedUser authUser) {
        List<LikedProductDetail> details = likeApplicationService.getMyLikesWithDetails(authUser.userId());

        List<LikeV1Dto.LikeResponse> likeResponses = details.stream()
            .map(detail -> LikeV1Dto.LikeResponse.from(detail.like(), detail.product(), detail.brand()))
            .toList();

        return ApiResponse.success(LikeV1Dto.LikeListResponse.from(likeResponses));
    }
}
