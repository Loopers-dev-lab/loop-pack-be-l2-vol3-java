package com.loopers.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * cache.* 설정을 타입 안전하게 바인딩.
 * default-ttl-seconds: 전역 기본 TTL
 * ttl-overrides: 캐시 이름별 TTL 재정의 (예: product:list → 180초)
 * jitter-range-seconds: TTL에 ±N초 무작위 오차를 더해 동시 만료 방지 (캐시 스탬피드 완화)
 * max-cacheable-page: 이 페이지 번호 미만(0-indexed)만 캐싱 - 딥 페이징 캐시 키 폭발 방지
 */
@ConfigurationProperties(prefix = "cache")
public record CacheProperties(
        long defaultTtlSeconds,
        Map<String, Long> ttlOverrides,
        long jitterRangeSeconds,
        int maxCacheablePage
) {
    public CacheProperties {
        if (ttlOverrides == null) {
            ttlOverrides = new HashMap<>();
        }
    }

    public long getTtlSeconds(String cacheName) {
        long base = ttlOverrides.getOrDefault(cacheName, defaultTtlSeconds);
        if (jitterRangeSeconds <= 0) {
            return base;
        }
        // Bounded Jitter: base ± jitterRange 범위에서 무작위 TTL
        long jitter = ThreadLocalRandom.current().nextLong(-jitterRangeSeconds, jitterRangeSeconds + 1);
        return Math.max(1, base + jitter); // 최소 1초 보장
    }

    // page=0, 1, 2 → 캐싱 대상 / page=3 이상 → DB 직접 조회
    public boolean isCacheable(int pageNumber) {
        return pageNumber < maxCacheablePage;
    }
}
