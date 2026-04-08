package com.loopers.domain.ranking;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RankingRepository {

    /**
     * 날짜별 ZSET에서 상위 N개의 상품 ID와 점수를 반환한다 (점수 내림차순).
     * page는 1-based.
     */
    List<RankedProduct> getTopN(LocalDate date, int page, int size);

    /**
     * 날짜별 ZSET에서 해당 상품의 순위를 반환한다 (1-based). 없으면 empty.
     */
    Optional<Integer> getRank(Long productId, LocalDate date);

    record RankedProduct(Long productId, double score) {}
}
