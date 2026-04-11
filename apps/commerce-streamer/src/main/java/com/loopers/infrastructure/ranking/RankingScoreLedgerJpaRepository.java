package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.RankingScoreLedger;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
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

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update RankingScoreLedger l set l.dirty = false where l.id in :ids")
    int markSyncedByIds(@Param("ids") Collection<Long> ids);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update RankingScoreLedger l
           set l.basePoints = l.basePoints + :delta,
               l.lastScoredAt = :now,
               l.dirty = true,
               l.version = l.version + 1
         where l.bucketType = :bucketType
           and l.bucketKey = :bucketKey
           and l.productId = :productId
        """)
    int addDelta(
        @Param("bucketType") RankingScoreLedger.BucketType bucketType,
        @Param("bucketKey") String bucketKey,
        @Param("productId") Long productId,
        @Param("delta") double delta,
        @Param("now") Instant now
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update RankingScoreLedger l
           set l.dirty = false
         where l.id = :id
           and l.version = :version
        """)
    int markSyncedIfUnchanged(@Param("id") Long id, @Param("version") Long version);
}
