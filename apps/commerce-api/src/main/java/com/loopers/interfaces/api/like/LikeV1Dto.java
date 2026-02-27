package com.loopers.interfaces.api.like;

import com.loopers.application.like.LikeInfo;

import java.time.ZonedDateTime;
import java.util.List;

public class LikeV1Dto {

    /**
     * 좋아요 등록 응답
     */
    public record LikeResponse(
            Long likeId,
            Long productId,
            String productName,
            Long brandId,
            String brandName,
            int price,
            boolean inStock,
            int likeCount,
            ZonedDateTime likedAt
    ) {
        public static LikeResponse from(LikeInfo info) {
            return new LikeResponse(
                    info.id(),
                    info.productId(),
                    info.productName(),
                    info.brandId(),
                    info.brandName(),
                    info.price(),
                    info.inStock(),
                    info.likeCount(),
                    info.createdAt()
            );
        }
    }

    /**
     * 좋아요 목록 조회 응답
     */
    public record LikedProductListResponse(List<LikeResponse> likes) {
        public static LikedProductListResponse from(List<LikeInfo> infos) {
            return new LikedProductListResponse(
                    infos.stream().map(LikeResponse::from).toList()
            );
        }
    }
}
