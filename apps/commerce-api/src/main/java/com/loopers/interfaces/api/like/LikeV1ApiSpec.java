package com.loopers.interfaces.api.like;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;

@Tag(name = "Like V1 API", description = "좋아요 API")
public interface LikeV1ApiSpec {

    @Operation(
        summary = "좋아요 추가",
        description = "상품에 좋아요를 등록합니다. 로그인 필요."
    )
    ApiResponse<LikeV1Dto.LikeResponse> addLike(
        @Parameter(description = "로그인 사용자 ID (X-Loopers-LoginId)", required = true)
        String loginId,
        @Schema(description = "좋아요 추가 요청 (상품 ID)")
        @Valid LikeV1Dto.AddLikeRequest request
    );

    @Operation(
        summary = "좋아요 취소",
        description = "상품에 대한 좋아요를 취소합니다."
    )
    ApiResponse<Void> removeLike(
        @Parameter(description = "로그인 사용자 ID (X-Loopers-LoginId)", required = true)
        String loginId,
        @Parameter(description = "상품 ID", required = true)
        Long productId
    );

    @Operation(
        summary = "내 좋아요 목록 조회",
        description = "로그인한 사용자의 좋아요 목록을 페이징하여 조회합니다."
    )
    ApiResponse<LikeV1Dto.PagedLikesResponse> getMyLikes(
        @Parameter(description = "로그인 사용자 ID (X-Loopers-LoginId)", required = true)
        String loginId,
        Pageable pageable
    );
}
