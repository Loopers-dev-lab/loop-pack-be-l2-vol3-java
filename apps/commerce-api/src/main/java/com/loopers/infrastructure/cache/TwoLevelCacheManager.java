package com.loopers.infrastructure.cache;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.data.redis.cache.RedisCacheManager;

import java.util.Collection;
import java.util.Set;

@RequiredArgsConstructor
public class TwoLevelCacheManager implements CacheManager {

    private final CaffeineCacheManager l1CacheManager;
    private final RedisCacheManager l2CacheManager;
    private final Set<String> twoLevelCacheNames;

    @Override
    public Cache getCache(String name) {
        Cache l2 = l2CacheManager.getCache(name);
        if (l2 == null) {
            return null;
        }

        if (twoLevelCacheNames.contains(name)) {
            Cache l1 = l1CacheManager.getCache(name);
            return new TwoLevelCache(l1, l2);
        }

        return l2;
    }

    @Override
    public Collection<String> getCacheNames() {
        return l2CacheManager.getCacheNames();
    }
}
