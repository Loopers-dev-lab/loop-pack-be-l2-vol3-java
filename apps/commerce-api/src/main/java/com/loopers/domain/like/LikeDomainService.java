package com.loopers.domain.like;

import org.springframework.stereotype.Component;

/**
 * Like 도메인 서비스
 * Like 도메인 내부의 상태 전이 규칙을 담당한다.
 * 도메인 간 협력(Product.likeCount 변경 등)은 Application Layer에서 처리한다.
 */
@Component
public class LikeDomainService {

    public LikeProcessResult like(Long userId, Long productId, Like existingLike) {
        if (existingLike != null && !existingLike.isDeleted()) {
            return new LikeProcessResult(existingLike, false);
        }

        if (existingLike != null) {
            existingLike.restore();
            return new LikeProcessResult(existingLike, true);
        }

        Like like = Like.create(userId, productId);
        return new LikeProcessResult(like, true);
    }

    public boolean unlike(Like like) {
        if (like == null) {
            return false;
        }
        like.delete();
        return true;
    }

    public record LikeProcessResult(Like like, boolean requiresPersistence) {
    }
}
