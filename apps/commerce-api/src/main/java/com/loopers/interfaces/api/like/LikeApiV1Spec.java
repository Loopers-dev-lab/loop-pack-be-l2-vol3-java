package com.loopers.interfaces.api.like;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.auth.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Like API", description = "좋아요 API")
public interface LikeApiV1Spec {

    // Command

    @Operation(
            summary = "상품 좋아요 등록",
            description = "상품에 좋아요를 등록합니다. 이미 좋아요한 상품이면 현재 상태를 유지합니다."
    )
    ApiResponse<Void> like(Long productId, AuthenticatedUser authUser);
}
