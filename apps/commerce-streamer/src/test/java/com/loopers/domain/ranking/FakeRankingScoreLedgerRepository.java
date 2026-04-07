package com.loopers.domain.ranking;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public class FakeRankingScoreLedgerRepository implements RankingScoreLedgerRepository {

    // key = bucketType|bucketKey|productId
    private final Map<String, RankingScoreLedger> store = new HashMap<>();
    private long idSeq = 0L;

    private void assignIdIfMissing(RankingScoreLedger ledger) {
        try {
            java.lang.reflect.Field idField = com.loopers.domain.BaseEntity.class.getDeclaredField("id");
            idField.setAccessible(true);
            Long current = (Long) idField.get(ledger);
            if (current == null || current == 0L) {
                idField.set(ledger, ++idSeq);
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("failed to assign fake id", e);
        }
    }

    @Override
    public Optional<RankingScoreLedger> findByBucket(
        RankingScoreLedger.BucketType bucketType, String bucketKey, Long productId
    ) {
        return Optional.ofNullable(store.get(key(bucketType, bucketKey, productId)));
    }

    @Override
    public RankingScoreLedger save(RankingScoreLedger ledger) {
        assignIdIfMissing(ledger);
        store.put(key(ledger.getBucketType(), ledger.getBucketKey(), ledger.getProductId()), ledger);
        return ledger;
    }

    @Override
    public List<RankingScoreLedger> saveAll(List<RankingScoreLedger> ledgers) {
        for (RankingScoreLedger l : ledgers) save(l);
        return ledgers;
    }

    @Override
    public List<RankingScoreLedger> findDirty(
        RankingScoreLedger.BucketType bucketType, String bucketKey, int limit
    ) {
        return store.values().stream()
            .filter(l -> l.getBucketType() == bucketType
                && Objects.equals(l.getBucketKey(), bucketKey)
                && l.isDirty())
            .sorted(Comparator.comparing(RankingScoreLedger::getProductId))
            .limit(limit)
            .toList();
    }

    @Override
    public List<RankingScoreLedger> findAllByBucket(
        RankingScoreLedger.BucketType bucketType, String bucketKey
    ) {
        List<RankingScoreLedger> result = new ArrayList<>();
        for (RankingScoreLedger l : store.values()) {
            if (l.getBucketType() == bucketType && Objects.equals(l.getBucketKey(), bucketKey)) {
                result.add(l);
            }
        }
        return result;
    }

    @Override
    public void markSyncedByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) return;
        Set<Long> idSet = new HashSet<>(ids);
        for (RankingScoreLedger l : store.values()) {
            if (l.getId() != null && idSet.contains(l.getId())) {
                l.markSynced();
            }
        }
    }

    public void clear() {
        store.clear();
    }

    public int size() {
        return store.size();
    }

    private static String key(RankingScoreLedger.BucketType type, String bucketKey, Long productId) {
        return type.name() + "|" + bucketKey + "|" + productId;
    }
}
