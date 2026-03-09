package com.loopers.application.like;

import com.loopers.domain.like.LikeModel;

import java.time.ZonedDateTime;

/**
 * 좋아요 응답용 애플리케이션 DTO.
 * Controller 응답에 사용하며, interfaces DTO와 분리한다.
 */
public record LikeInfo(
    Long id,
    Long userId,
    Long productId,
    ZonedDateTime createdAt
) {
    /**
     * LikeModel을 LikeInfo로 변환한다. 입력 null은 즉시 실패하여 호출 계약을 명확히 한다.
     *
     * @param like 변환할 엔티티 (null이면 IllegalArgumentException)
     * @return 변환된 LikeInfo
     * @throws IllegalArgumentException like가 null인 경우
     */
    public static LikeInfo from(LikeModel like) {
        if (like == null) {
            throw new IllegalArgumentException("like must not be null");
        }
        return new LikeInfo(
            like.getId(),
            like.getUserId(),
            like.getProductId(),
            like.getCreatedAt()
        );
    }
}
