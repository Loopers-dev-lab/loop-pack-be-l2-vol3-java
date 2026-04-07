package com.loopers.domain.ranking;

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
}
