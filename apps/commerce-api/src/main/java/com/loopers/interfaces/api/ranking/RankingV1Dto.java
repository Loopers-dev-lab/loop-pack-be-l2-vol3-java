package com.loopers.interfaces.api.ranking;

import com.loopers.application.ranking.RankingInfo;
import com.loopers.application.ranking.RankingPageInfo;

import java.math.BigDecimal;
import java.util.List;

public class RankingV1Dto {

    public record RankItem(
            long rank,
            Long productId,
            double score,
            String name,
            BigDecimal price,
            String imageUrl,
            Long brandId,
            String brandName
    ) {
        public static RankItem from(RankingInfo info) {
            return new RankItem(
                    info.rank(), info.productId(), info.score(),
                    info.name(), info.price(), info.imageUrl(),
                    info.brandId(), info.brandName()
            );
        }
    }

    public record PageResponse(
            String date,
            List<RankItem> items,
            long totalElements,
            int page,
            int size
    ) {
        public static PageResponse from(RankingPageInfo pageInfo) {
            List<RankItem> items = pageInfo.items().stream().map(RankItem::from).toList();
            return new PageResponse(pageInfo.date(), items, pageInfo.totalElements(), pageInfo.page(), pageInfo.size());
        }
    }
}
