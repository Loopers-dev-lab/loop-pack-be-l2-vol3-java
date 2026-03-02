package com.loopers.interfaces.api.like;

import com.loopers.application.like.LikeInfo;

import java.math.BigDecimal;
import java.util.List;

public class LikeDto {

    public record ToggleResponse(boolean liked) {
        public static ToggleResponse from(boolean liked) {
            return new ToggleResponse(liked);
        }
    }

    public record LikeResponse(
            Long likeId,
            Long productId,
            String productName,
            BigDecimal basePrice
    ) {
        public static LikeResponse from(LikeInfo info) {
            return new LikeResponse(
                    info.getLikeId(),
                    info.getProductId(),
                    info.getProductName(),
                    info.getBasePrice().getAmount()
            );
        }
    }

    public record LikeListResponse(List<LikeResponse> likes) {
        public static LikeListResponse from(List<LikeInfo> infoList) {
            return new LikeListResponse(
                    infoList.stream().map(LikeResponse::from).toList()
            );
        }
    }
}
