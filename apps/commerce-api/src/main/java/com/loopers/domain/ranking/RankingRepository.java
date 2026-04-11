package com.loopers.domain.ranking;

import java.util.List;

public interface RankingRepository {

    /** ZREVRANGE WITHSCORES — score 내림차순으로 Top-N 조회 */
    List<RankingEntry> getTopN(String key, int offset, int size);

    /** ZREVRANK — 특정 상품의 순위 조회 (0-based, 없으면 null) */
    Long getRank(String key, Long productId);

    /** DB ORDER BY 기반 Top-N 조회 — ZSET 성능 비교용 */
    List<RankingEntry> getTopNFromDB(int offset, int size);
}
