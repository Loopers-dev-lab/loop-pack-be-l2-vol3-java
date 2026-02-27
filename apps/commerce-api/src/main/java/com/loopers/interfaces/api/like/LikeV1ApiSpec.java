package com.loopers.interfaces.api.like;

import com.loopers.application.user.UserInfo;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.support.LoginUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "좋아요 API")
public interface LikeV1ApiSpec {

    @Operation(summary = "상품 좋아요 등록", description = "인증된 회원이 상품에 좋아요를 등록합니다.")
    ApiResponse<LikeV1Dto.LikeResponse> createLike(@LoginUser UserInfo loginUser, long productId);

    @Operation(summary = "상품 좋아요 취소", description = "인증된 회원이 상품의 좋아요를 취소합니다.")
    ApiResponse<Void> deleteLike(@LoginUser UserInfo loginUser, long productId);

    @Operation(summary = "좋아요한 상품 목록 조회", description = "인증된 회원의 좋아요 목록을 조회합니다.")
    ApiResponse<LikeV1Dto.LikedProductListResponse> getLikes(@LoginUser UserInfo loginUser);
}
