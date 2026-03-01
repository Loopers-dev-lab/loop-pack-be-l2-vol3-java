package com.loopers.interfaces.api.like;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.auth.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Like V1 API", description = "좋아요 API 입니다.")
public interface LikeV1ApiSpec {

    @Operation(summary = "좋아요 등록", description = "상품에 좋아요를 등록합니다.")
    ApiResponse<Void> like(@Parameter(hidden = true) AuthenticatedUser authUser, Long productId);

    @Operation(summary = "좋아요 취소", description = "상품의 좋아요를 취소합니다.")
    ApiResponse<Void> unlike(@Parameter(hidden = true) AuthenticatedUser authUser, Long productId);

    @Operation(summary = "내 좋아요 목록 조회", description = "내가 좋아요한 상품 목록을 조회합니다.")
    ApiResponse<LikeV1Dto.LikeListResponse> getMyLikes(@Parameter(hidden = true) AuthenticatedUser authUser);
}
