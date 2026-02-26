package com.loopers.application.like;

import com.loopers.domain.model.user.UserId;

public interface LikeUseCase {

    void like(UserId userId, Long productId);
}
