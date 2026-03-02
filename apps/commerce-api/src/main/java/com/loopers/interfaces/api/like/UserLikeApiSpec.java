package com.loopers.interfaces.api.like;

import com.loopers.domain.user.User;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.auth.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "User Like API", description = "내 좋아요 목록 API")
public interface UserLikeApiSpec {

    @Operation(summary = "내가 좋아요한 상품 목록 조회", description = "내가 좋아요한 상품 목록을 최근 좋아요순으로 조회합니다.")
    ApiResponse<LikeResponse.LikedProductListResponse> getMyProductLikes(
            @AuthUser User user, Long userId, int page, int size);

    @Operation(summary = "내가 좋아요한 브랜드 목록 조회", description = "내가 좋아요한 브랜드 목록을 최근 좋아요순으로 조회합니다.")
    ApiResponse<LikeResponse.LikedBrandListResponse> getMyBrandLikes(
            @AuthUser User user, int page, int size);
}
