package com.loopers.interfaces.api.like;

import jakarta.validation.constraints.NotNull;

public class LikeV1Dto {

    public record LikeRequest(
        @NotNull(message = "사용자 ID는 필수입니다.")
        Long userId
    ) {}
}
