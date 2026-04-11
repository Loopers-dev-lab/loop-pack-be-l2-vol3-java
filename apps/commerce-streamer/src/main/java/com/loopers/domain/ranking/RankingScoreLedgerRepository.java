package com.loopers.domain.ranking;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RankingScoreLedgerRepository {

    Optional<RankingScoreLedger> findByBucket(
        RankingScoreLedger.BucketType bucketType, String bucketKey, Long productId
    );

    RankingScoreLedger save(RankingScoreLedger ledger);

    List<RankingScoreLedger> saveAll(List<RankingScoreLedger> ledgers);

    List<RankingScoreLedger> findDirty(
        RankingScoreLedger.BucketType bucketType, String bucketKey, int limit
    );

    List<RankingScoreLedger> findAllByBucket(
        RankingScoreLedger.BucketType bucketType, String bucketKey
    );

    void markSyncedByIds(Collection<Long> ids);

    /**
     * 원자적 delta 반영. 존재하는 row에 basePoints += delta, lastScoredAt = now, dirty = true, version++ 를 한 쿼리로.
     * 반환: 영향받은 row 수 (0이면 row 없음 → 호출자가 INSERT 경로로 폴백)
     */
    int addDelta(
        RankingScoreLedger.BucketType bucketType, String bucketKey, Long productId,
        double delta, Instant now
    );

    /**
     * 조건부 markSynced. 주어진 version과 일치할 때만 dirty=false로 전환.
     * 반환: 영향받은 row 수 (0이면 중간에 갱신된 것 → dirty 유지)
     */
    int markSyncedIfUnchanged(Long id, Long version);
}
