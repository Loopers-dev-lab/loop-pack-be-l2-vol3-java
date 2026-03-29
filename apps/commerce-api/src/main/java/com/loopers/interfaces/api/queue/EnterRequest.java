package com.loopers.interfaces.api.queue;

import jakarta.validation.constraints.NotNull;

public record EnterRequest(
        @NotNull(message = "회원 ID는 필수입니다.")
        Long memberId
) {
}
