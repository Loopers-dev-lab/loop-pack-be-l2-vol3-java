package com.loopers.interfaces.api.like;

import com.loopers.application.like.LikeInfo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 좋아요 API V1 요청/응답 DTO 모음.
 *
 * <p>좋아요 관련 REST API의 HTTP 응답 데이터 구조를 정의한다.</p>
 */
public class LikeV1Dto {

    /**
     * 좋아요 조회 응답 DTO.
     *
     * <p>좋아요 등록된 상품 ID와 등록 일시를 포함한다.</p>
     */
    @Getter
    @AllArgsConstructor
    @Builder
    public static class LikeResponse {
        private String productId;
        private LocalDateTime createdAt;

        /**
         * {@link LikeInfo}를 좋아요 응답 DTO로 변환하는 팩토리 메서드.
         *
         * @param info 변환할 좋아요 도메인 Info 객체
         * @return 변환된 LikeResponse
         */
        public static LikeResponse from(LikeInfo info) {
            return LikeResponse.builder()
                    .productId(info.getProductId())
                    .createdAt(info.getCreatedAt())
                    .build();
        }
    }
}
