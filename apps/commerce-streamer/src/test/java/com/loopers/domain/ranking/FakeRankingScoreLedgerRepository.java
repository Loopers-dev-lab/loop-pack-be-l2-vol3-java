package com.loopers.domain.ranking;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class FakeRankingScoreLedgerRepository implements RankingScoreLedgerRepository {

    // key = bucketType|bucketKey|productId
    private final Map<String, RankingScoreLedger> store = new HashMap<>();

    @Override
    public Optional<RankingScoreLedger> findByBucket(
        RankingScoreLedger.BucketType bucketType, String bucketKey, Long productId
    ) {
        return Optional.ofNullable(store.get(key(bucketType, bucketKey, productId)));
    }

    @Override
    public RankingScoreLedger save(RankingScoreLedger ledger) {
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
