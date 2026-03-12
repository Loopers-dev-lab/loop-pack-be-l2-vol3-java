package com.loopers.domain.like;

import java.util.Objects;

/**
 * 좋아요 복합키 Value Object.
 * userId와 productId의 조합으로 유일성을 보장.
 */
public record LikeId(Long userId, Long productId) {

    public LikeId {
        Objects.requireNonNull(userId, "userId는 필수입니다.");
        Objects.requireNonNull(productId, "productId는 필수입니다.");
    }
}
