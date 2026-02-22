package com.loopers.interfaces.api.like;

import com.loopers.domain.user.User;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.auth.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Brand Like API", description = "브랜드 좋아요 API")
public interface BrandLikeApiSpec {

    @Operation(summary = "브랜드 좋아요 등록", description = "브랜드에 좋아요를 등록합니다.")
    ApiResponse<Object> likeBrand(@AuthUser User user, Long brandId);

    @Operation(summary = "브랜드 좋아요 취소", description = "브랜드 좋아요를 취소합니다.")
    ApiResponse<Object> unlikeBrand(@AuthUser User user, Long brandId);
}
