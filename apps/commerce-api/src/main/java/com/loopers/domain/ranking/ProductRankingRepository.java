package com.loopers.domain.ranking;

import java.util.List;

public interface ProductRankingRepository {

    List<ProductRankingInfo> getRankings(String dateKey, int offset, int size);

    Long getRank(String dateKey, Long productId);

    Double getScore(String dateKey, Long productId);

    Long getTotalCount(String dateKey);

    void removeProduct(Long productId);
}
