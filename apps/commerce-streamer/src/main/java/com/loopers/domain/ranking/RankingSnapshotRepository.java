package com.loopers.domain.ranking;

import java.time.LocalDate;
import java.util.Map;

/**
 * 랭킹 스냅샷 저장소.
 *
 * <p>Redis ZSET의 상위권 score를 DB에 주기적으로 스냅샷하여,
 * Redis 장애 시 복구할 수 있는 SOT(Source of Truth)를 유지한다.</p>
 */
public interface RankingSnapshotRepository {

    /**
     * 상품별 score를 일괄 upsert한다.
     *
     * <p>동일한 (productId, scoreDate) 조합이 존재하면 score를 갱신하고,
     * 존재하지 않으면 새로 생성한다.</p>
     *
     * @param scoreDate     스냅샷 대상 날짜
     * @param productScores 상품 ID → score 매핑
     */
    void saveAll(LocalDate scoreDate, Map<Long, Double> productScores);
}
