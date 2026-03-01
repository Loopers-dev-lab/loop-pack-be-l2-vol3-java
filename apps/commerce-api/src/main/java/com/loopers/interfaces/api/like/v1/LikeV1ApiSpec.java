package com.loopers.interfaces.api.like.v1;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Like V1 API", description = "좋아요 API 입니다.")
public interface LikeV1ApiSpec {

    @Operation(
            summary = "상품 좋아요 등록",
            description = "상품에 좋아요를 등록합니다. 이미 좋아요가 존재하면 아무 동작 없이 성공합니다."
    )
    ApiResponse<Object> likeProduct(Long userId, Long productId);

    @Operation(
            summary = "상품 좋아요 취소",
            description = "상품에 등록된 좋아요를 취소합니다. 좋아요가 존재하지 않으면 아무 동작 없이 성공합니다."
    )
    ApiResponse<Object> unlikeProduct(Long userId, Long productId);

    @Operation(
            summary = "좋아요 등록한 상품 목록 조회",
            description = "현재 로그인한 사용자가 좋아요를 등록한 상품 목록을 페이지네이션하여 조회합니다."
    )
    ApiResponse<PageResponse<LikeDto.LikedProductResponse>> getLikedProducts(Long userId, int page, int size);
}
