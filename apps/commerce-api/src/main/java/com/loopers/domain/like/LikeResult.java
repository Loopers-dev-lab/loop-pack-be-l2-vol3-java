package com.loopers.domain.like;

import java.util.Optional;

/**
 * 좋아요 토글 결과.
 * @param newLike 새로 생성된 LikeModel (저장 필요), 기존 엔티티 변경만 발생한 경우 empty
 * @param countChanged likeCount 변경이 필요한지 여부
 */
public record LikeResult(Optional<LikeModel> newLike, boolean countChanged) {
}
