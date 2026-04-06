package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.RankingScoreLedger;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RankingScoreLedgerJpaRepository extends JpaRepository<RankingScoreLedger, Long> {

    Optional<RankingScoreLedger> findByBucketTypeAndBucketKeyAndProductId(
        RankingScoreLedger.BucketType bucketType, String bucketKey, Long productId
    );

    List<RankingScoreLedger> findByBucketTypeAndBucketKeyAndDirtyTrue(
        RankingScoreLedger.BucketType bucketType, String bucketKey, Pageable pageable
    );

    List<RankingScoreLedger> findByBucketTypeAndBucketKey(
        RankingScoreLedger.BucketType bucketType, String bucketKey
    );
}
