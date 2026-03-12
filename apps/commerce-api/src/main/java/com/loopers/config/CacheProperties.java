package com.loopers.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * cache.* 설정을 타입 안전하게 바인딩.
 * default-ttl-seconds: 전역 기본 TTL
 * ttl-overrides: 캐시 이름별 TTL 재정의 (예: product:list → 180초)
 */
@ConfigurationProperties(prefix = "cache")
public record CacheProperties(
        long defaultTtlSeconds,
        Map<String, Long> ttlOverrides
) {
    public CacheProperties {
        if (ttlOverrides == null) {
            ttlOverrides = new HashMap<>();
        }
    }

    public long getTtlSeconds(String cacheName) {
        return ttlOverrides.getOrDefault(cacheName, defaultTtlSeconds);
    }
}
