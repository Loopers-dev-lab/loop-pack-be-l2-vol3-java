package com.loopers.domain.like;

/**
 * 좋아요 등록 이벤트.
 * LikeFacade에서 좋아요 저장 후 발행하며,
 * AFTER_COMMIT 시점에 LikeEventListener가 수신하여 상품 좋아요 수를 증가시킨다.
 */
public record LikeCreatedEvent(
        Long productId,
        Long userId
) {}
