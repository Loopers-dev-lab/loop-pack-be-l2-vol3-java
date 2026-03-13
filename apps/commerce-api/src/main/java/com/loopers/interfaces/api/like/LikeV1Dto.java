package com.loopers.interfaces.api.like;

import jakarta.validation.constraints.NotNull;

public class LikeV1Dto {

    public record LikeRequest(
        @NotNull(message = "회원 ID는 필수입니다")
        Long memberId,

        @NotNull(message = "상품 ID는 필수입니다")
        Long productId
    ) {}
}
