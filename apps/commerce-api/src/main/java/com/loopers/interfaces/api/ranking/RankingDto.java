package com.loopers.interfaces.api.ranking;

import com.loopers.application.ranking.RankingInfo;

public class RankingDto {

    public record Response(Long rank, Long productId, String name, String description,
                           String brand, int price, long likeCount) {

        public static Response from(RankingInfo info) {
            return new Response(info.rank(), info.productId(), info.name(), info.description(),
                    info.brand(), info.price(), info.likeCount());
        }
    }
}
