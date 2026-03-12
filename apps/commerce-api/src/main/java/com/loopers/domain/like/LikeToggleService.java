package com.loopers.domain.like;

import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 좋아요 토글 도메인 서비스.
 * Like 엔티티의 상태를 종합하여 좋아요 반응을 결정한다.
 *
 * 인프라(Repository, DB) 의존 없이 순수 비즈니스 의사결정만 담당.
 * likeCount 변경은 호출자가 원자적 업데이트로 처리한다.
 */
@Component
public class LikeToggleService {

    /**
     * 좋아요 토글: 상태에 따라 신규 생성 / 복구 / 멱등 무시를 결정한다.
     *
     * @return LikeResult — 새 LikeModel(저장 필요 여부) + likeCount 변경 여부
     */
    public LikeResult like(Optional<LikeModel> existing, Long userId, Long productId) {
        if (existing.isEmpty()) {
            return new LikeResult(Optional.of(new LikeModel(userId, productId)), true);
        }

        LikeModel like = existing.get();
        if (like.getDeletedAt() != null) {
            like.restore();
            return new LikeResult(Optional.empty(), true);
        }

        // 이미 활성 좋아요 → 멱등 무시
        return new LikeResult(Optional.empty(), false);
    }

    /**
     * 좋아요 취소: 활성 좋아요를 삭제한다.
     */
    public void unlike(LikeModel like) {
        like.delete();
    }
}
