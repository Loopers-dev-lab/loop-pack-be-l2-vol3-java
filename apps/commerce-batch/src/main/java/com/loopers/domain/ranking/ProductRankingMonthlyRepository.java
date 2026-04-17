package com.loopers.domain.ranking;

import java.util.List;

/**
 * 월간 랭킹 저장소.
 *
 * <p>배치가 집계한 월간 랭킹 스코어를 저장한다.
 * 동일한 (productId, scoreDate) 조합이 이미 존재하면 score를 덮어쓴다 (upsert).</p>
 */
public interface ProductRankingMonthlyRepository {

    /**
     * 월간 랭킹 스코어를 일괄 저장한다.
     *
     * <p>MySQL의 {@code ON DUPLICATE KEY UPDATE}를 활용하여
     * 동일한 (productId, scoreDate) 조합이 존재하면 score를 갱신하고,
     * 존재하지 않으면 새로 생성한다. 이를 통해 멱등성이 보장된다.</p>
     *
     * @param rankings 저장할 월간 랭킹 목록
     */
    void saveAll(List<ProductRankingMonthly> rankings);
}
