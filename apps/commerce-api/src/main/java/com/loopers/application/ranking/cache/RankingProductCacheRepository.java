package com.loopers.application.ranking.cache;

import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

public interface RankingProductCacheRepository {

    Map<UUID, RankingProductCacheItem> findAll(Collection<UUID> productIds);

    void saveAll(Collection<RankingProductCacheItem> items, Duration ttl);

    void evict(UUID productId);
}
