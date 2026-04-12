package com.loopers.application.ranking;

import com.loopers.application.ranking.cache.RankingProductCacheItem;
import com.loopers.application.ranking.cache.RankingProductCacheProperties;
import com.loopers.application.ranking.cache.RankingProductCacheRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RankingProductCacheApplicationService {

    private final RankingProductCacheRepository rankingProductCacheRepository;
    private final RankingProductCacheProperties rankingProductCacheProperties;

    @Transactional(readOnly = true)
    public Map<UUID, RankingProductCacheItem> findAll(Collection<UUID> productIds) {
        if (!rankingProductCacheProperties.enabled() || productIds.isEmpty()) {
            return Map.of();
        }
        return rankingProductCacheRepository.findAll(productIds);
    }

    @Transactional
    public void saveAll(Collection<RankingProductCacheItem> items) {
        if (!rankingProductCacheProperties.enabled() || items.isEmpty()) {
            return;
        }
        rankingProductCacheRepository.saveAll(items, rankingProductCacheProperties.ttl());
    }

    @Transactional
    public void evict(UUID productId) {
        if (!rankingProductCacheProperties.enabled()) {
            return;
        }
        rankingProductCacheRepository.evict(productId);
    }
}
