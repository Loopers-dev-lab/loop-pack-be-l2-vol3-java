package com.loopers.application.like;

import com.loopers.domain.model.user.UserId;

public interface UnlikeUseCase {

    void unlike(UserId userId, Long productId);
}
