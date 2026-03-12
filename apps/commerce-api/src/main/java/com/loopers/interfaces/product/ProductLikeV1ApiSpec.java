package com.loopers.interfaces.product;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.auth.LoginUser;
import com.loopers.interfaces.product.dto.ProductLikeV1Dto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Like V1 API", description = "좋아요 API 입니다.")
public interface ProductLikeV1ApiSpec {

    @Operation(
        summary = "상품 좋아요 등록",
        description = "상품에 좋아요를 등록합니다."
    )
    ApiResponse<Object> like(
        @Parameter(hidden = true) LoginUser loginUser,
        @Parameter(description = "상품 ID", required = true, example = "1") Long productId
    );

    @Operation(
        summary = "상품 좋아요 취소",
        description = "상품의 좋아요를 취소합니다."
    )
    ApiResponse<Object> unlike(
        @Parameter(hidden = true) LoginUser loginUser,
        @Parameter(description = "상품 ID", required = true, example = "1") Long productId
    );

    @Operation(
        summary = "내가 좋아요한 상품 목록 조회",
        description = "로그인한 사용자가 좋아요한 상품 목록을 조회합니다."
    )
    ApiResponse<ProductLikeV1Dto.ListResponse> getMyLikes(
        @Parameter(hidden = true) LoginUser loginUser
    );
}
