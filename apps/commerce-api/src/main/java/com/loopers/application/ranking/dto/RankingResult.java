package com.loopers.application.ranking.dto;

import com.loopers.domain.product.ProductModel;
import com.loopers.domain.ranking.dto.RankingInfo;
import java.time.LocalDate;
import java.util.List;

public class RankingResult {

    public record RankingEntry(
            long rank,
            Long productId,
            String productName,
            String brandName,
            int price,
            String thumbnailUrl,
            double score) {

        public static RankingEntry of(RankingInfo.RankedScore ranked,
                ProductModel product, String brandName) {
            return new RankingEntry(
                    ranked.rank(),
                    product.getId(),
                    product.getName(),
                    brandName,
                    product.getPrice(),
                    product.getThumbnailUrl(),
                    ranked.score());
        }
    }

    public record RankingPage(
            LocalDate date,
            int page,
            int size,
            long totalElements,
            List<RankingEntry> items) {
    }
}
