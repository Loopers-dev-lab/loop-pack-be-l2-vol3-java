package com.loopers.application.like;

import com.loopers.domain.like.LikeModel;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 좋아요 정보 DTO.
 * 도메인 모델({@link LikeModel})을 직접 노출하지 않고 인터페이스 레이어에 전달하기 위한 응답 객체이다.
 */
@Getter
@Builder
public class LikeInfo {
    private final String userId;
    private final String productId;
    private final LocalDateTime createdAt;

    /**
     * LikeModel을 LikeInfo DTO로 변환한다.
     *
     * @param model 변환할 좋아요 엔티티
     * @return 좋아요 정보 DTO
     */
    public static LikeInfo from(LikeModel model) {
        return LikeInfo.builder()
                .userId(model.getUserId())
                .productId(model.getProductId())
                .createdAt(model.getCreatedAt())
                .build();
    }
}
