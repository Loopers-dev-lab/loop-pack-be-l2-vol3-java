package com.loopers.interfaces.api.favorite;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Favorite V1 API", description = "좋아요 API 입니다.")
public interface FavoriteV1ApiSpec {

    @Operation(summary = "좋아요 등록", description = "상품에 좋아요를 등록합니다.")
    ApiResponse<Void> addFavorite(
            @Parameter(description = "로그인 ID", required = true) String loginId,
            @Parameter(description = "비밀번호", required = true) String password,
            @Parameter(description = "상품 ID", required = true) Long productId
    );

    @Operation(summary = "좋아요 취소", description = "상품의 좋아요를 취소합니다.")
    ApiResponse<Void> deleteFavorite(
            @Parameter(description = "로그인 ID", required = true) String loginId,
            @Parameter(description = "비밀번호", required = true) String password,
            @Parameter(description = "상품 ID", required = true) Long productId
    );
}
