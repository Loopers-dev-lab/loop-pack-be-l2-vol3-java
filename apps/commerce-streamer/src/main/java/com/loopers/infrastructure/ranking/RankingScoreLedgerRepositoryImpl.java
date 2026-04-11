package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.RankingScoreLedger;
import com.loopers.domain.ranking.RankingScoreLedgerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class RankingScoreLedgerRepositoryImpl implements RankingScoreLedgerRepository {

    private final RankingScoreLedgerJpaRepository jpaRepository;

    @Override
    public Optional<RankingScoreLedger> findByBucket(
        RankingScoreLedger.BucketType bucketType, String bucketKey, Long productId
    ) {
        return jpaRepository.findByBucketTypeAndBucketKeyAndProductId(bucketType, bucketKey, productId);
    }

    @Override
    public RankingScoreLedger save(RankingScoreLedger ledger) {
        return jpaRepository.save(ledger);
    }

    @Override
    public List<RankingScoreLedger> saveAll(List<RankingScoreLedger> ledgers) {
        return jpaRepository.saveAll(ledgers);
    }

    @Override
    public List<RankingScoreLedger> findDirty(
        RankingScoreLedger.BucketType bucketType, String bucketKey, int limit
    ) {
        return jpaRepository.findByBucketTypeAndBucketKeyAndDirtyTrue(
            bucketType, bucketKey, PageRequest.of(0, limit)
        );
    }

    @Override
    public List<RankingScoreLedger> findAllByBucket(
        RankingScoreLedger.BucketType bucketType, String bucketKey
    ) {
        return jpaRepository.findByBucketTypeAndBucketKey(bucketType, bucketKey);
    }

    @Override
    public void markSyncedByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) return;
        jpaRepository.markSyncedByIds(ids);
    }

    @Override
    public int addDelta(
        RankingScoreLedger.BucketType bucketType, String bucketKey, Long productId,
        double delta, Instant now
    ) {
        return jpaRepository.addDelta(bucketType, bucketKey, productId, delta, now);
    }

    @Override
    public int markSyncedIfUnchanged(Long id, Long version) {
        return jpaRepository.markSyncedIfUnchanged(id, version);
    }
}
