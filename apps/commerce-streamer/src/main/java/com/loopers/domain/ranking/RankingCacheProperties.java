package com.loopers.domain.ranking;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 랭킹 ZSET retention 설정.
 *
 * `ranking.cache.retention` 에서 ISO-8601 Duration 으로 주입된다.
 * 과제 명세 `TTL: 2Day` 를 만족하며, 최초 키 생성 시 1회만 EXPIRE 가 적용된다.
 */
@ConfigurationProperties(prefix = "ranking.cache")
public record RankingCacheProperties(
        Duration retention
) {
    public RankingCacheProperties {
        if (retention == null) {
            throw new IllegalArgumentException("ranking.cache.retention must not be null");
        }
        if (retention.isZero() || retention.isNegative()) {
            throw new IllegalArgumentException(
                    "ranking.cache.retention must be positive, got: " + retention);
        }
    }
}
