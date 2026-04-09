package com.loopers.domain.ranking;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 랭킹 스냅샷 저장소.
 *
 * <p>Redis ZSET의 상위권 score를 DB에 주기적으로 스냅샷하여,
 * Redis 장애 시 복구할 수 있는 SOT(Source of Truth)를 유지한다.</p>
 */
public interface RankingSnapshotRepository {

    /**
     * 상품별 score를 시간 단위로 일괄 upsert한다.
     *
     * @param scoreHour 스냅샷 대상 시간 (시간 단위로 truncate된 값)
     * @param scores    상품별 점수 목록
     */
    void saveAll(LocalDateTime scoreHour, List<RankingScore> scores);
}
