package com.loopers.domain.ranking;

import java.time.LocalDate;
import java.util.List;

/**
 * 랭킹 스냅샷 저장소.
 *
 * <p>Redis ZSET의 상위권 score를 DB에 주기적으로 스냅샷하여,
 * Redis 장애 시 복구할 수 있는 SOT(Source of Truth)를 유지한다.</p>
 */
public interface RankingSnapshotRepository {

    /**
     * 상품별 score를 일괄 upsert한다.
     */
    void saveAll(LocalDate scoreDate, List<RankingScore> scores);
}
