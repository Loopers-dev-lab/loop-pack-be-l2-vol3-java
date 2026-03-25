package com.loopers.domain.like;

/**
 * 좋아요 취소 이벤트.
 * LikeFacade에서 좋아요 삭제 후 발행하며,
 * AFTER_COMMIT 시점에 LikeEventListener가 수신하여 상품 좋아요 수를 감소시킨다.
 */
public record LikeCancelledEvent(
        Long productId,
        Long userId
) {}
