package com.loopers.interfaces.api.like;

import com.loopers.application.like.LikeInfo;

import java.time.ZonedDateTime;
import java.util.List;

public class LikeV1Dto {

    public record LikeResponse(
        Long likeId,
        Long productId,
        String productName,
        String brandName,
        int price,
        int likeCount,
        ZonedDateTime likedAt
    ) {
        public static LikeResponse from(LikeInfo info) {
            return new LikeResponse(
                info.likeId(), info.productId(), info.productName(),
                info.brandName(), info.price(), info.likeCount(), info.likedAt()
            );
        }
    }

    public record LikeListResponse(List<LikeResponse> likes) {
        public static LikeListResponse from(List<LikeInfo> infos) {
            List<LikeResponse> likes = infos.stream()
                .map(LikeResponse::from)
                .toList();
            return new LikeListResponse(likes);
        }
    }
}
