package com.loopers.interfaces.api.like;

import com.loopers.domain.user.User;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.auth.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Product Like API", description = "상품 좋아요 API")
public interface ProductLikeApiSpec {

    @Operation(summary = "상품 좋아요 등록", description = "상품에 좋아요를 등록합니다.")
    ApiResponse<LikeResponse.LikeResult> likeProduct(@AuthUser User user, Long productId);

    @Operation(summary = "상품 좋아요 취소", description = "상품 좋아요를 취소합니다.")
    ApiResponse<LikeResponse.LikeResult> unlikeProduct(@AuthUser User user, Long productId);
}
