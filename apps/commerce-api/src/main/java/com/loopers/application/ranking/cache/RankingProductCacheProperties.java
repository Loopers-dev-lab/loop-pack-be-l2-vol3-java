package com.loopers.application.ranking.cache;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties("cache.ranking-product")
public record RankingProductCacheProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("30s") Duration ttl
) {
}
